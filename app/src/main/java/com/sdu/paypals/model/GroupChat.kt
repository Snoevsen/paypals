package com.sdu.paypals.model

import androidx.annotation.DrawableRes
import com.google.firebase.firestore.DocumentSnapshot
import com.sdu.paypals.R

enum class GroupIcon(val key: String, val label: String, @param:DrawableRes val drawableRes: Int) {
    GROUPS("groups", "Group", R.drawable.ic_groups),
    HOME("home", "Household", R.drawable.ic_home),
    TRIP("trip", "Trip", R.drawable.ic_group_trip),
    FOOD("food", "Dining", R.drawable.ic_group_food),
    SHOPPING("shopping", "Shopping", R.drawable.ic_group_shopping),
    CELEBRATION("celebration", "Event", R.drawable.ic_group_celebration);

    companion object {
        fun fromKey(key: String?): GroupIcon {
            return entries.firstOrNull { it.key == key } ?: GROUPS
        }
    }
}

data class GroupMember(
    val uid: String = "",
    val displayName: String = "",
    val email: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "displayName" to displayName,
        "email" to email
    )

    companion object {
        fun fromMap(map: Map<*, *>?): GroupMember? {
            if (map == null) return null
            val uid = map["uid"] as? String ?: return null
            val displayName = map["displayName"] as? String ?: ""
            val email = map["email"] as? String ?: ""
            return GroupMember(uid = uid, displayName = displayName, email = email)
        }
    }
}

/**
 * Reusable GroupChat model used across all payment tracking groups.
 * Enforces membership visibility (`memberIds`) and admin rules (`adminIds`).
 */
data class GroupChat(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val iconKey: String = GroupIcon.GROUPS.key,
    val memberIds: List<String> = emptyList(),
    val adminIds: List<String> = emptyList(),
    val members: Map<String, GroupMember> = emptyMap(),
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastActivityText: String = "",
    val lastActivityAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MAX_PAYMENT_DESCRIPTION_LENGTH = 100
        const val MAX_GROUP_NAME_LENGTH = 50
        const val MAX_GROUP_DESC_LENGTH = 140

        fun fromDocument(doc: DocumentSnapshot): GroupChat? {
            val data = doc.data ?: return null
            val id = doc.id
            val name = data["name"] as? String ?: "Unnamed Group"
            val description = data["description"] as? String ?: ""
            val iconKey = data["iconKey"] as? String ?: GroupIcon.GROUPS.key
            val memberIds = (data["memberIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val adminIds = (data["adminIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val createdBy = data["createdBy"] as? String ?: ""
            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
            val lastActivityText = data["lastActivityText"] as? String ?: ""
            val lastActivityAt = (data["lastActivityAt"] as? Number)?.toLong() ?: createdAt

            val rawMembers = data["members"] as? Map<*, *>
            val parsedMembers = mutableMapOf<String, GroupMember>()
            rawMembers?.forEach { (k, v) ->
                val uidKey = k as? String
                val memberObj = GroupMember.fromMap(v as? Map<*, *>)
                if (uidKey != null && memberObj != null) {
                    parsedMembers[uidKey] = memberObj
                }
            }

            return GroupChat(
                id = id,
                name = name,
                description = description,
                iconKey = iconKey,
                memberIds = memberIds,
                adminIds = adminIds,
                members = parsedMembers,
                createdBy = createdBy,
                createdAt = createdAt,
                lastActivityText = lastActivityText,
                lastActivityAt = lastActivityAt
            )
        }
    }

    fun isMember(userId: String): Boolean = memberIds.contains(userId)

    fun isAdmin(userId: String): Boolean = adminIds.contains(userId)

    /**
     * Checks whether the given user is allowed to leave the group.
     * If the user is an admin and is the ONLY admin while other members remain in the group,
     * they must first assign admin rights to another member before leaving.
     */
    fun canUserLeave(userId: String): Boolean {
        if (!memberIds.contains(userId)) return false
        if (memberIds.size <= 1) return true
        if (isAdmin(userId) && adminIds.size <= 1) {
            return false
        }
        return true
    }

    fun getIconDrawableRes(): Int = GroupIcon.fromKey(iconKey).drawableRes

    fun toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "description" to description,
        "iconKey" to iconKey,
        "memberIds" to memberIds,
        "adminIds" to adminIds,
        "members" to members.mapValues { it.value.toMap() },
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "lastActivityText" to lastActivityText,
        "lastActivityAt" to lastActivityAt
    )
}
