package com.sdu.paypals.model

import com.google.firebase.firestore.DocumentSnapshot

data class ParticipantStatus(
    val uid: String = "",
    val displayName: String = "",
    val amountOwed: Double = 0.0,
    val paid: Boolean = false,
    val paidAt: Long = 0L,
    val paymentNote: String = "",
    val lastNudgedAt: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "displayName" to displayName,
        "amountOwed" to amountOwed,
        "paid" to paid,
        "paidAt" to paidAt,
        "paymentNote" to paymentNote,
        "lastNudgedAt" to lastNudgedAt
    )

    companion object {
        fun fromMap(map: Map<*, *>?): ParticipantStatus? {
            if (map == null) return null
            val uid = map["uid"] as? String ?: return null
            val displayName = map["displayName"] as? String ?: ""
            val amountOwed = (map["amountOwed"] as? Number)?.toDouble() ?: 0.0
            val paid = map["paid"] as? Boolean ?: false
            val paidAt = (map["paidAt"] as? Number)?.toLong() ?: 0L
            val paymentNote = map["paymentNote"] as? String ?: ""
            val lastNudgedAt = (map["lastNudgedAt"] as? Number)?.toLong() ?: 0L
            return ParticipantStatus(
                uid = uid,
                displayName = displayName,
                amountOwed = amountOwed,
                paid = paid,
                paidAt = paidAt,
                paymentNote = paymentNote,
                lastNudgedAt = lastNudgedAt
            )
        }
    }
}

data class PaymentLogEntry(
    val actorUid: String = "",
    val actorName: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "actorUid" to actorUid,
        "actorName" to actorName,
        "message" to message,
        "timestamp" to timestamp
    )

    companion object {
        fun fromMap(map: Map<*, *>?): PaymentLogEntry? {
            if (map == null) return null
            val actorUid = map["actorUid"] as? String ?: ""
            val actorName = map["actorName"] as? String ?: ""
            val message = map["message"] as? String ?: ""
            val timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L
            return PaymentLogEntry(
                actorUid = actorUid,
                actorName = actorName,
                message = message,
                timestamp = timestamp
            )
        }
    }
}

data class PaymentRequest(
    val id: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val requesterId: String = "",
    val requesterName: String = "",
    val totalAmount: Double = 0.0,
    val description: String = "",
    val receiptDetails: String = "",
    val receiptImageBase64: String = "",
    val includedMemberIds: List<String> = emptyList(),
    val excludedMemberIds: List<String> = emptyList(),
    val excludedMemberNames: List<String> = emptyList(),
    val sharePerPerson: Double = 0.0,
    val participantStatuses: Map<String, ParticipantStatus> = emptyMap(),
    val paymentLogs: List<PaymentLogEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun paidCount(): Int = participantStatuses.values.count { it.paid }
    fun totalParticipantCount(): Int = participantStatuses.size
    fun isFullyPaid(): Boolean = participantStatuses.values.all { it.paid }

    fun toMap(): Map<String, Any> = mapOf(
        "groupId" to groupId,
        "groupName" to groupName,
        "requesterId" to requesterId,
        "requesterName" to requesterName,
        "totalAmount" to totalAmount,
        "description" to description,
        "receiptDetails" to receiptDetails,
        "receiptImageBase64" to receiptImageBase64,
        "includedMemberIds" to includedMemberIds,
        "excludedMemberIds" to excludedMemberIds,
        "excludedMemberNames" to excludedMemberNames,
        "sharePerPerson" to sharePerPerson,
        "participantStatuses" to participantStatuses.mapValues { it.value.toMap() },
        "paymentLogs" to paymentLogs.map { it.toMap() },
        "createdAt" to createdAt
    )

    companion object {
        fun fromDocument(doc: DocumentSnapshot): PaymentRequest? {
            val data = doc.data ?: return null
            val id = doc.id
            val groupId = data["groupId"] as? String ?: ""
            val groupName = data["groupName"] as? String ?: ""
            val requesterId = data["requesterId"] as? String ?: ""
            val requesterName = data["requesterName"] as? String ?: ""
            val totalAmount = (data["totalAmount"] as? Number)?.toDouble() ?: 0.0
            val description = data["description"] as? String ?: ""
            val receiptDetails = data["receiptDetails"] as? String ?: ""
            val receiptImageBase64 = data["receiptImageBase64"] as? String ?: ""
            val includedMemberIds = (data["includedMemberIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val excludedMemberIds = (data["excludedMemberIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val excludedMemberNames = (data["excludedMemberNames"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val sharePerPerson = (data["sharePerPerson"] as? Number)?.toDouble() ?: 0.0
            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L

            val rawStatuses = data["participantStatuses"] as? Map<*, *>
            val parsedStatuses = mutableMapOf<String, ParticipantStatus>()
            rawStatuses?.forEach { (k, v) ->
                val uidKey = k as? String
                val statusObj = ParticipantStatus.fromMap(v as? Map<*, *>)
                if (uidKey != null && statusObj != null) {
                    parsedStatuses[uidKey] = statusObj
                }
            }

            val rawLogs = data["paymentLogs"] as? List<*>
            val parsedLogs = rawLogs?.mapNotNull { PaymentLogEntry.fromMap(it as? Map<*, *>) } ?: emptyList()

            return PaymentRequest(
                id = id,
                groupId = groupId,
                groupName = groupName,
                requesterId = requesterId,
                requesterName = requesterName,
                totalAmount = totalAmount,
                description = description,
                receiptDetails = receiptDetails,
                receiptImageBase64 = receiptImageBase64,
                includedMemberIds = includedMemberIds,
                excludedMemberIds = excludedMemberIds,
                excludedMemberNames = excludedMemberNames,
                sharePerPerson = sharePerPerson,
                participantStatuses = parsedStatuses,
                paymentLogs = parsedLogs,
                createdAt = createdAt
            )
        }
    }
}

data class NudgeNotification(
    val id: String = "",
    val recipientId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val paymentId: String = "",
    val paymentDescription: String = "",
    val amountOwed: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val delivered: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "recipientId" to recipientId,
        "senderId" to senderId,
        "senderName" to senderName,
        "groupId" to groupId,
        "groupName" to groupName,
        "paymentId" to paymentId,
        "paymentDescription" to paymentDescription,
        "amountOwed" to amountOwed,
        "createdAt" to createdAt,
        "delivered" to delivered
    )

    companion object {
        fun fromDocument(doc: DocumentSnapshot): NudgeNotification? {
            val data = doc.data ?: return null
            return NudgeNotification(
                id = doc.id,
                recipientId = data["recipientId"] as? String ?: "",
                senderId = data["senderId"] as? String ?: "",
                senderName = data["senderName"] as? String ?: "",
                groupId = data["groupId"] as? String ?: "",
                groupName = data["groupName"] as? String ?: "",
                paymentId = data["paymentId"] as? String ?: "",
                paymentDescription = data["paymentDescription"] as? String ?: "",
                amountOwed = (data["amountOwed"] as? Number)?.toDouble() ?: 0.0,
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                delivered = data["delivered"] as? Boolean ?: false
            )
        }
    }
}

enum class ActivityCategory {
    EXPENSE_PAID_BY_ME,
    PAID_TO_ME,
    REMINDER
}

data class UserActivityItem(
    val id: String,
    val groupId: String,
    val groupName: String,
    val payerName: String,
    val description: String,
    val amount: Double,
    val timestamp: Long,
    val category: ActivityCategory
)

data class MemberDebtItem(
    val uid: String,
    val displayName: String,
    val amount: Double
)

data class UserBalanceSummary(
    val totalOwedToMe: Double = 0.0,
    val totalIOwe: Double = 0.0,
    val owedByMembers: List<MemberDebtItem> = emptyList(),
    val iOweToMembers: List<MemberDebtItem> = emptyList(),
    val unpaidRequestsIOwe: List<PaymentRequest> = emptyList(),
    val unpaidRequestsOwedToMe: List<PaymentRequest> = emptyList()
) {
    val netBalance: Double
        get() = totalOwedToMe - totalIOwe

    /**
     * Formats a human-readable balance status summary for either a single group or overall dashboard.
     */
    fun formatSummaryText(inGroup: Boolean, formatter: (Double) -> String): String = when {
        totalOwedToMe > 0.0 && totalIOwe > 0.0 ->
            "You are owed ${formatter(totalOwedToMe)} and owe ${formatter(totalIOwe)}"
        totalOwedToMe > 0.0 ->
            if (inGroup) "Others in this group owe you ${formatter(totalOwedToMe)}"
            else "Others owe you ${formatter(totalOwedToMe)} in total"
        totalIOwe > 0.0 ->
            if (inGroup) "You owe ${formatter(totalIOwe)} in this group"
            else "You owe ${formatter(totalIOwe)} in total"
        else ->
            if (inGroup) "All settled up in this group!" else "All settled up across your groups"
    }
}
