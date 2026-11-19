package com.sdu.paypals.repository

import com.sdu.paypals.model.ActivityCategory
import com.sdu.paypals.model.MemberDebtItem
import com.sdu.paypals.model.NudgeNotification
import com.sdu.paypals.model.PaymentRequest
import com.sdu.paypals.model.UserActivityItem
import com.sdu.paypals.model.UserBalanceSummary

internal object ActivityAndBalanceRepository {

    fun observeUserActivityFeed(
        userId: String,
        onUpdate: (List<UserActivityItem>) -> Unit
    ): () -> Unit {
        var latestPaymentsByGroup: Map<String, List<PaymentRequest>> = emptyMap()
        var latestNudges: List<NudgeNotification> = emptyList()

        fun emit() {
            onUpdate(buildUserActivityItems(userId, latestPaymentsByGroup.values.flatten(), latestNudges))
        }

        val groupPaymentsReg = FirebaseProvider.observeUserGroupPayments(userId) { paymentsByGroup ->
            latestPaymentsByGroup = paymentsByGroup
            emit()
        }

        val nudgesReg = FirebaseProvider.observeQuery(
            query = FirebaseProvider.db.collection("notifications").whereEqualTo("recipientId", userId),
            mapper = { snapshot ->
                snapshot?.documents?.mapNotNull { NudgeNotification.fromDocument(it) } ?: emptyList()
            },
            onUpdate = { nudges ->
                latestNudges = nudges
                emit()
            }
        )

        return {
            groupPaymentsReg.remove()
            nudgesReg.remove()
        }
    }

    fun buildUserActivityItems(
        userId: String,
        payments: List<PaymentRequest>,
        nudges: List<NudgeNotification>
    ): List<UserActivityItem> = buildList {
        for (payment in payments) {
            if (payment.requesterId == userId) {
                add(
                    UserActivityItem(
                        id = "req_${payment.id}",
                        groupId = payment.groupId,
                        groupName = payment.groupName,
                        payerName = "${payment.requesterName} (You)",
                        description = "Paid & requested in ${payment.groupName}: ${payment.description}",
                        amount = payment.totalAmount,
                        timestamp = payment.createdAt,
                        category = ActivityCategory.EXPENSE_PAID_BY_ME
                    )
                )

                for (status in payment.participantStatuses.values) {
                    if (status.uid != userId && status.paid) {
                        val notePart = if (status.paymentNote.isNotBlank()) " (\"${status.paymentNote}\")" else ""
                        add(
                            UserActivityItem(
                                id = "paid_to_me_${payment.id}_${status.uid}",
                                groupId = payment.groupId,
                                groupName = payment.groupName,
                                payerName = status.displayName,
                                description = "${status.displayName} paid you for \"${payment.description}\" in ${payment.groupName}$notePart",
                                amount = status.amountOwed,
                                timestamp = if (status.paidAt > 0L) status.paidAt else payment.createdAt,
                                category = ActivityCategory.PAID_TO_ME
                            )
                        )
                    }
                }
            } else {
                val myStatus = payment.participantStatuses[userId] ?: continue
                if (myStatus.paid) {
                    val notePart = if (myStatus.paymentNote.isNotBlank()) " (\"${myStatus.paymentNote}\")" else ""
                    add(
                        UserActivityItem(
                            id = "my_share_paid_${payment.id}",
                            groupId = payment.groupId,
                            groupName = payment.groupName,
                            payerName = "${myStatus.displayName} (You)",
                            description = "Paid ${payment.requesterName} for \"${payment.description}\" in ${payment.groupName}$notePart",
                            amount = myStatus.amountOwed,
                            timestamp = if (myStatus.paidAt > 0L) myStatus.paidAt else payment.createdAt,
                            category = ActivityCategory.EXPENSE_PAID_BY_ME
                        )
                    )
                } else {
                    add(
                        UserActivityItem(
                            id = "pending_owe_${payment.id}",
                            groupId = payment.groupId,
                            groupName = payment.groupName,
                            payerName = "${payment.requesterName} (Requested)",
                            description = "Reminder: You owe ${payment.requesterName} for \"${payment.description}\" in ${payment.groupName}",
                            amount = myStatus.amountOwed,
                            timestamp = if (myStatus.lastNudgedAt > 0L) myStatus.lastNudgedAt else payment.createdAt,
                            category = ActivityCategory.REMINDER
                        )
                    )
                }
            }
        }

        for (nudge in nudges) {
            add(
                UserActivityItem(
                    id = "nudge_${nudge.id}",
                    groupId = nudge.groupId,
                    groupName = nudge.groupName,
                    payerName = "${nudge.senderName} (Nudge)",
                    description = "Nudge from ${nudge.senderName} for \"${nudge.paymentDescription}\" in ${nudge.groupName}",
                    amount = nudge.amountOwed,
                    timestamp = nudge.createdAt,
                    category = ActivityCategory.REMINDER
                )
            )
        }
    }

    /**
     * Calculates how much `userId` is owed and owes across the given list of [PaymentRequest]s,
     * along with per-member breakdowns and unpaid requests.
     */
    fun calculateUserBalance(
        userId: String,
        payments: List<PaymentRequest>
    ): UserBalanceSummary {
        var totalOwedToMe = 0.0
        var totalIOwe = 0.0
        val owedByMap = linkedMapOf<String, MemberDebtItem>()
        val iOweToMap = linkedMapOf<String, MemberDebtItem>()
        val unpaidIOweList = mutableListOf<PaymentRequest>()
        val unpaidOwedToMeList = mutableListOf<PaymentRequest>()

        fun accumulateDebt(targetMap: MutableMap<String, MemberDebtItem>, uid: String, name: String, delta: Double) {
            val existing = targetMap[uid]
            targetMap[uid] = MemberDebtItem(uid = uid, displayName = name, amount = (existing?.amount ?: 0.0) + delta)
        }

        for (payment in payments) {
            if (payment.requesterId == userId) {
                var hasUnpaidOnThisRequest = false
                for (status in payment.participantStatuses.values) {
                    if (status.uid != userId && !status.paid && status.amountOwed > 0.0) {
                        hasUnpaidOnThisRequest = true
                        totalOwedToMe += status.amountOwed
                        accumulateDebt(owedByMap, status.uid, status.displayName, status.amountOwed)
                    }
                }
                if (hasUnpaidOnThisRequest) {
                    unpaidOwedToMeList.add(payment)
                }
            } else {
                val myStatus = payment.participantStatuses[userId]
                if (myStatus != null && !myStatus.paid && myStatus.amountOwed > 0.0) {
                    totalIOwe += myStatus.amountOwed
                    accumulateDebt(iOweToMap, payment.requesterId, payment.requesterName, myStatus.amountOwed)
                    unpaidIOweList.add(payment)
                }
            }
        }

        return UserBalanceSummary(
            totalOwedToMe = totalOwedToMe,
            totalIOwe = totalIOwe,
            owedByMembers = owedByMap.values.toList(),
            iOweToMembers = iOweToMap.values.toList(),
            unpaidRequestsIOwe = unpaidIOweList,
            unpaidRequestsOwedToMe = unpaidOwedToMeList
        )
    }

    /**
     * Observes the user's total owed and owes across ALL groups they belong to in real time.
     * Returns a cleanup function that removes all active listeners.
     */
    fun observeTotalUserBalance(
        userId: String,
        onUpdate: (UserBalanceSummary) -> Unit
    ): () -> Unit {
        val reg = FirebaseProvider.observeUserGroupPayments(userId) { paymentsByGroup ->
            onUpdate(calculateUserBalance(userId, paymentsByGroup.values.flatten()))
        }
        return { reg.remove() }
    }
}
