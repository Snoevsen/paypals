package com.sdu.paypals.repository

import com.google.firebase.firestore.SetOptions
import com.sdu.paypals.model.GroupChat
import com.sdu.paypals.model.GroupMember
import com.sdu.paypals.sync.RequestSyncManager
import java.util.Locale

/**
 * Handles user authentication identity helpers, account registration validation,
 * profile synchronization, and pending email invitation claiming.
 */
internal object UserProfileRepository {

    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun currentUserId(): String? = FirebaseProvider.auth.currentUser?.uid

    fun currentUserName(): String {
        val user = FirebaseProvider.auth.currentUser
        val display = user?.displayName
        if (!display.isNullOrBlank()) return display
        val emailPrefix = user?.email?.substringBefore("@")
        return if (!emailPrefix.isNullOrBlank()) emailPrefix else "User"
    }

    fun currentUserEmail(): String =
        FirebaseProvider.auth.currentUser?.email?.lowercase(Locale.ROOT).orEmpty()

    /**
     * Validates account registration inputs (Full Name, Email, Password) and builds the canonical
     * user profile map stored in Firestore when an account is created.
     */
    fun validateAndBuildAccountProfile(
        uid: String,
        fullName: String,
        email: String,
        password: String,
        now: Long = System.currentTimeMillis()
    ): Result<Map<String, Any>> {
        val cleanName = fullName.trim()
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val cleanPassword = password.trim()

        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Full name is required"))
        }
        if (cleanEmail.isEmpty() || !EMAIL_REGEX.matches(cleanEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }
        if (cleanPassword.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }

        val profileMap = mapOf(
            "uid" to uid,
            "email" to cleanEmail,
            "displayName" to cleanName,
            "updatedAt" to now
        )
        return Result.success(profileMap)
    }

    /**
     * Syncs the logged-in user's profile to Firestore `users/{uid}` and claims any
     * email-based pending group invitations matching their email address.
     */
    fun syncCurrentUserProfile(onComplete: (() -> Unit)? = null) {
        val user = FirebaseProvider.auth.currentUser ?: run {
            onComplete?.invoke()
            return
        }
        val uid = user.uid
        val email = user.email?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val displayName = currentUserName()

        val userMap = mapOf(
            "uid" to uid,
            "email" to email,
            "displayName" to displayName,
            "updatedAt" to System.currentTimeMillis()
        )

        if (!RequestSyncManager.isNetworkAvailable) {
            FirebaseProvider.db.collection("users").document(uid).set(userMap, SetOptions.merge())
            onComplete?.invoke()
            return
        }

        FirebaseProvider.db.collection("users").document(uid)
            .set(userMap, SetOptions.merge())
            .addOnCompleteListener {
                if (email.isNotEmpty()) {
                    claimPendingEmailInvites(uid, email, displayName) {
                        onComplete?.invoke()
                    }
                } else {
                    onComplete?.invoke()
                }
            }
    }

    /**
     * Updates the user's `users/{uid}` record and propagates their updated display name and email
     * to all groups they belong to.
     */
    fun propagateProfileUpdate(
        newDisplayName: String,
        newEmail: String,
        onComplete: (() -> Unit)? = null
    ) {
        val uid = currentUserId() ?: run {
            onComplete?.invoke()
            return
        }
        val cleanEmail = newEmail.trim().lowercase(Locale.ROOT)
        val cleanName = newDisplayName.trim()

        val userMap = mapOf(
            "uid" to uid,
            "email" to cleanEmail,
            "displayName" to cleanName,
            "updatedAt" to System.currentTimeMillis()
        )

        RequestSyncManager.executeWriteTask(
            actionLabel = "Update Profile",
            optimisticResult = Unit,
            taskProvider = {
                FirebaseProvider.db.collection("users").document(uid).set(userMap, SetOptions.merge())
            }
        ) {
            FirebaseProvider.db.collection("groups")
                .whereArrayContains("memberIds", uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        val batch = FirebaseProvider.db.batch()
                        for (doc in snapshot.documents) {
                            val group = GroupChat.fromDocument(doc)
                            if (group != null) {
                                val updatedMembers = group.members.toMutableMap()
                                updatedMembers[uid] = GroupMember(
                                    uid = uid,
                                    displayName = cleanName,
                                    email = cleanEmail
                                )
                                batch.update(
                                    doc.reference,
                                    "members",
                                    updatedMembers.mapValues { it.value.toMap() }
                                )
                            }
                        }
                        batch.commit()
                    }
                    onComplete?.invoke()
                }
                .addOnFailureListener {
                    onComplete?.invoke()
                }
        }
    }

    private fun claimPendingEmailInvites(
        uid: String,
        email: String,
        displayName: String,
        onDone: () -> Unit
    ) {
        val inviteId = inviteIdForEmail(email)
        FirebaseProvider.db.collection("groups")
            .whereArrayContains("memberIds", inviteId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onDone()
                    return@addOnSuccessListener
                }
                var remaining = snapshot.documents.size
                for (doc in snapshot.documents) {
                    val group = GroupChat.fromDocument(doc)
                    if (group != null) {
                        val updatedMemberIds = group.memberIds.map { if (it == inviteId) uid else it }.distinct()
                        val updatedAdminIds = group.adminIds.map { if (it == inviteId) uid else it }.distinct()
                        val updatedMembers = group.members.toMutableMap()
                        updatedMembers.remove(inviteId)
                        updatedMembers[uid] = GroupMember(uid = uid, displayName = displayName, email = email)

                        doc.reference.update(
                            mapOf(
                                "memberIds" to updatedMemberIds,
                                "adminIds" to updatedAdminIds,
                                "members" to updatedMembers.mapValues { it.value.toMap() }
                            )
                        ).addOnCompleteListener {
                            remaining--
                            if (remaining <= 0) onDone()
                        }
                    } else {
                        remaining--
                        if (remaining <= 0) onDone()
                    }
                }
            }
            .addOnFailureListener {
                onDone()
            }
    }

    fun inviteIdForEmail(email: String): String {
        val clean = email.trim().lowercase(Locale.ROOT)
        return "invite_${clean.hashCode().toUInt().toString(16)}"
    }
}
