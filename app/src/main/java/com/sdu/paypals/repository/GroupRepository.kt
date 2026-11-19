package com.sdu.paypals.repository

import com.google.firebase.firestore.ListenerRegistration
import com.sdu.paypals.model.GroupChat
import com.sdu.paypals.model.GroupMember
import com.sdu.paypals.sync.RequestState
import com.sdu.paypals.sync.RequestSyncManager
import java.util.Locale

/**
 * Handles group creation, real-time group listeners, QR code payloads,
 * member management, and admin permissions.
 */
internal object GroupRepository {

    const val QR_PREFIX = "paypals://join?groupId="

    fun buildGroupQrPayload(groupId: String): String = "$QR_PREFIX$groupId"

    fun extractGroupIdFromQr(rawValue: String?): String? {
        val trimmed = rawValue?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.startsWith(QR_PREFIX) ->
                trimmed.removePrefix(QR_PREFIX).trim().takeIf { it.isNotEmpty() }
            trimmed.contains("groupId=") ->
                trimmed.substringAfter("groupId=").substringBefore("&").trim().takeIf { it.isNotEmpty() }
            else -> trimmed
        }
    }

    /**
     * Real-time listener for all groups that include the given user.
     * Works both online and offline using Firestore's persistent local cache.
     */
    fun observeUserGroups(
        userId: String,
        onUpdate: (List<GroupChat>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = FirebaseProvider.observeQuery(
        query = FirebaseProvider.db.collection("groups").whereArrayContains("memberIds", userId),
        onError = onError,
        mapper = { snapshot ->
            snapshot?.documents
                ?.mapNotNull { GroupChat.fromDocument(it) }
                ?.sortedByDescending { it.lastActivityAt }
                ?: emptyList()
        },
        onUpdate = onUpdate
    )

    /**
     * Real-time listener for a single group's metadata and members (including offline cache).
     */
    fun observeGroup(
        groupId: String,
        onUpdate: (GroupChat?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = FirebaseProvider.observeDocument(
        docRef = FirebaseProvider.db.collection("groups").document(groupId),
        onError = onError,
        mapper = { snapshot ->
            if (snapshot != null && snapshot.exists()) GroupChat.fromDocument(snapshot) else null
        },
        onUpdate = onUpdate
    )

    /**
     * Validates and constructs a new [GroupChat] with the creator as initial member and admin.
     */
    fun buildNewGroup(
        groupId: String,
        creatorUid: String,
        creatorName: String,
        creatorEmail: String,
        name: String,
        description: String,
        iconKey: String,
        now: Long = System.currentTimeMillis()
    ): Result<GroupChat> {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Group name is required"))
        }
        if (creatorUid.isBlank()) {
            return Result.failure(IllegalStateException("Creator UID is required"))
        }

        val creatorMember = GroupMember(
            uid = creatorUid,
            displayName = creatorName.trim().ifEmpty { "User" },
            email = creatorEmail.trim().lowercase(Locale.ROOT)
        )

        val group = GroupChat(
            id = groupId,
            name = cleanName,
            description = description.trim(),
            iconKey = iconKey,
            memberIds = listOf(creatorUid),
            adminIds = listOf(creatorUid),
            members = mapOf(creatorUid to creatorMember),
            createdBy = creatorUid,
            createdAt = now,
            lastActivityText = "${creatorMember.displayName} created the group",
            lastActivityAt = now
        )
        return Result.success(group)
    }

    /**
     * Creates a new group with fault tolerance and background retry support.
     */
    fun createGroup(
        name: String,
        description: String,
        iconKey: String,
        onResult: (Result<GroupChat>) -> Unit
    ) {
        val uid = requireSignedIn(onResult) ?: return
        val docRef = FirebaseProvider.db.collection("groups").document()
        val group = buildNewGroup(
            groupId = docRef.id,
            creatorUid = uid,
            creatorName = UserProfileRepository.currentUserName(),
            creatorEmail = UserProfileRepository.currentUserEmail(),
            name = name,
            description = description,
            iconKey = iconKey
        ).getOrElse { err ->
            onResult(Result.failure(err))
            return
        }

        RequestSyncManager.executeWriteTask(
            actionLabel = "Create Group '${group.name}'",
            optimisticResult = group,
            taskProvider = { docRef.set(group.toMap()) },
            onImmediateResult = onResult
        )
    }

    /**
     * Updates group name, description, and icon (Admin only) with fault tolerance.
     */
    fun updateGroupDetails(
        group: GroupChat,
        newName: String,
        newDescription: String,
        newIconKey: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        requireAdmin(group, "Only group admins can edit group details.", onResult) ?: return

        val updates = mapOf(
            "name" to newName.trim(),
            "description" to newDescription.trim(),
            "iconKey" to newIconKey,
            "lastActivityText" to "${UserProfileRepository.currentUserName()} updated group details",
            "lastActivityAt" to System.currentTimeMillis()
        )

        RequestSyncManager.executeWriteTask(
            actionLabel = "Update Group '${newName.trim()}'",
            optimisticResult = Unit,
            taskProvider = { FirebaseProvider.db.collection("groups").document(group.id).update(updates) },
            onImmediateResult = onResult
        )
    }

    /**
     * Joins a group directly by its ID (e.g. after scanning a group QR code).
     * Uses the user's own full name from account creation / profile edit.
     */
    fun joinGroupById(
        groupId: String,
        onResult: (Result<GroupChat>) -> Unit
    ) {
        val uid = requireSignedIn(onResult, "Please sign in to join a group.") ?: return
        val userName = UserProfileRepository.currentUserName()
        val userEmail = UserProfileRepository.currentUserEmail()

        RequestSyncManager.emitStatus(RequestState.PENDING, "Join Group via QR", "Pending: Joining group...")
        val groupRef = FirebaseProvider.db.collection("groups").document(groupId)
        groupRef.get()
            .addOnSuccessListener { snapshot ->
                val group = if (snapshot.exists()) GroupChat.fromDocument(snapshot) else null
                if (group == null) {
                    val msg = if (!snapshot.exists()) "Group not found." else "Could not read group data."
                    RequestSyncManager.emitStatus(RequestState.FAILED, "Join Group via QR", "Failed: $msg")
                    onResult(Result.failure(IllegalArgumentException(msg)))
                    return@addOnSuccessListener
                }

                if (group.memberIds.contains(uid)) {
                    RequestSyncManager.emitStatus(RequestState.SUCCESS, "Join Group via QR", "Success: Opened '${group.name}'")
                    onResult(Result.success(group))
                    return@addOnSuccessListener
                }

                val newMember = GroupMember(uid = uid, displayName = userName, email = userEmail)
                val updatedMemberIds = (group.memberIds + uid).distinct()
                val updatedMembers = group.members + (uid to newMember)
                val updatedGroup = group.copy(memberIds = updatedMemberIds, members = updatedMembers)

                RequestSyncManager.executeWriteTask(
                    actionLabel = "Join Group '${group.name}'",
                    optimisticResult = updatedGroup,
                    taskProvider = {
                        groupRef.update(
                            mapOf(
                                "memberIds" to updatedMemberIds,
                                "members" to updatedMembers.mapValues { it.value.toMap() },
                                "lastActivityText" to "$userName joined via QR code",
                                "lastActivityAt" to System.currentTimeMillis()
                            )
                        )
                    },
                    onImmediateResult = onResult
                )
            }
            .addOnFailureListener { e ->
                RequestSyncManager.emitStatus(
                    RequestState.FAILED,
                    "Join Group via QR",
                    "Failed: ${e.localizedMessage ?: "Could not join group"}"
                )
                onResult(Result.failure(e))
            }
    }

    /**
     * Adds a member to the group by looking up their email in Firestore `users`
     * and using ONLY the user's own full name from account creation or profile edit.
     */
    fun addMemberByEmail(
        group: GroupChat,
        emailInput: String,
        onResult: (Result<GroupMember>) -> Unit
    ) {
        val cleanEmail = emailInput.trim().lowercase(Locale.ROOT)
        if (cleanEmail.isEmpty()) {
            onResult(Result.failure(IllegalArgumentException("Email is required")))
            return
        }

        if (group.members.values.any { it.email.equals(cleanEmail, ignoreCase = true) }) {
            onResult(Result.failure(IllegalArgumentException("This user is already in the group.")))
            return
        }

        RequestSyncManager.emitStatus(RequestState.PENDING, "Add Member", "Pending: Looking up $cleanEmail...")
        FirebaseProvider.db.collection("users")
            .whereEqualTo("email", cleanEmail)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val doc = querySnapshot.documents.firstOrNull()
                if (doc == null) {
                    val msg = "No registered account found for $cleanEmail. They must sign up first or scan the group QR code."
                    RequestSyncManager.emitStatus(RequestState.FAILED, "Add Member", "Failed: $msg")
                    onResult(Result.failure(IllegalArgumentException(msg)))
                    return@addOnSuccessListener
                }

                val foundUid = doc.getString("uid") ?: doc.id
                val foundName = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                    ?: cleanEmail.substringBefore("@")
                val newMember = GroupMember(uid = foundUid, displayName = foundName, email = cleanEmail)

                val updatedMemberIds = (group.memberIds + newMember.uid).distinct()
                val updatedMembers = group.members + (newMember.uid to newMember)

                RequestSyncManager.executeWriteTask(
                    actionLabel = "Add ${newMember.displayName} to '${group.name}'",
                    optimisticResult = newMember,
                    taskProvider = {
                        FirebaseProvider.db.collection("groups").document(group.id).update(
                            mapOf(
                                "memberIds" to updatedMemberIds,
                                "members" to updatedMembers.mapValues { it.value.toMap() },
                                "lastActivityText" to "${UserProfileRepository.currentUserName()} added ${newMember.displayName}",
                                "lastActivityAt" to System.currentTimeMillis()
                            )
                        )
                    },
                    onImmediateResult = onResult
                )
            }
            .addOnFailureListener { e ->
                RequestSyncManager.emitStatus(
                    RequestState.FAILED,
                    "Add Member",
                    "Failed: ${e.localizedMessage ?: "Lookup error"}"
                )
                onResult(Result.failure(e))
            }
    }

    /**
     * Deletes the group if the user is an admin and is the only person inside the group.
     */
    fun deleteGroup(
        group: GroupChat,
        onResult: (Result<Unit>) -> Unit
    ) {
        requireAdmin(group, "Only an admin can delete the group.", onResult) ?: return
        if (group.memberIds.size > 1) {
            onResult(
                Result.failure(
                    IllegalStateException("You can only delete the group when you are the only member inside it.")
                )
            )
            return
        }

        RequestSyncManager.executeWriteTask(
            actionLabel = "Delete Group '${group.name}'",
            optimisticResult = Unit,
            taskProvider = { FirebaseProvider.db.collection("groups").document(group.id).delete() },
            onImmediateResult = onResult
        )
    }

    /**
     * Promotes another member to Admin (Admin only).
     */
    fun assignAdmin(
        group: GroupChat,
        targetUserId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        requireAdmin(group, "Only admins can assign new admins.", onResult) ?: return
        if (!group.memberIds.contains(targetUserId)) {
            onResult(Result.failure(IllegalArgumentException("Target user is not a member of this group.")))
            return
        }

        val updatedAdmins = (group.adminIds + targetUserId).distinct()
        val targetName = group.members[targetUserId]?.displayName ?: "Member"

        RequestSyncManager.executeWriteTask(
            actionLabel = "Promote $targetName to Admin",
            optimisticResult = Unit,
            taskProvider = {
                FirebaseProvider.db.collection("groups").document(group.id).update(
                    mapOf(
                        "adminIds" to updatedAdmins,
                        "lastActivityText" to "$targetName was made an admin",
                        "lastActivityAt" to System.currentTimeMillis()
                    )
                )
            },
            onImmediateResult = onResult
        )
    }

    /**
     * Allows a user to leave the group if they satisfy the admin rule:
     * If they are the sole admin and other members remain, they must assign another admin first.
     */
    fun leaveGroup(
        group: GroupChat,
        onResult: (Result<Unit>) -> Unit
    ) {
        val uid = requireSignedIn(onResult) ?: return

        if (!group.canUserLeave(uid)) {
            onResult(
                Result.failure(
                    IllegalStateException(
                        "You are the only admin of this group. Please assign admin rights to another member before leaving."
                    )
                )
            )
            return
        }

        val groupRef = FirebaseProvider.db.collection("groups").document(group.id)
        if (group.memberIds.size <= 1) {
            RequestSyncManager.executeWriteTask(
                actionLabel = "Leave & Delete Group '${group.name}'",
                optimisticResult = Unit,
                taskProvider = { groupRef.delete() },
                onImmediateResult = onResult
            )
            return
        }

        val updatedMemberIds = group.memberIds.filter { it != uid }
        val updatedAdminIds = group.adminIds.filter { it != uid }
        val updatedMembers = group.members - uid

        RequestSyncManager.executeWriteTask(
            actionLabel = "Leave Group '${group.name}'",
            optimisticResult = Unit,
            taskProvider = {
                groupRef.update(
                    mapOf(
                        "memberIds" to updatedMemberIds,
                        "adminIds" to updatedAdminIds,
                        "members" to updatedMembers.mapValues { it.value.toMap() },
                        "lastActivityText" to "${UserProfileRepository.currentUserName()} left the group",
                        "lastActivityAt" to System.currentTimeMillis()
                    )
                )
            },
            onImmediateResult = onResult
        )
    }

    private fun <T> requireSignedIn(
        onResult: (Result<T>) -> Unit,
        message: String = "Not signed in"
    ): String? {
        val uid = UserProfileRepository.currentUserId()
        if (uid == null) {
            onResult(Result.failure(IllegalStateException(message)))
        }
        return uid
    }

    private fun <T> requireAdmin(
        group: GroupChat,
        errorMessage: String,
        onResult: (Result<T>) -> Unit
    ): String? {
        val uid = UserProfileRepository.currentUserId()
        if (uid == null || !group.isAdmin(uid)) {
            onResult(Result.failure(IllegalAccessException(errorMessage)))
            return null
        }
        return uid
    }
}
