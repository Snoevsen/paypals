package com.sdu.paypals.repository

import com.google.firebase.firestore.ListenerRegistration
import com.sdu.paypals.model.GroupChat
import com.sdu.paypals.model.NudgeNotification
import com.sdu.paypals.model.ParticipantStatus
import com.sdu.paypals.model.PaymentLogEntry
import com.sdu.paypals.model.PaymentRequest
import com.sdu.paypals.sync.RequestSyncManager

/**
 * Handles payment requests, split calculations, marking shares as paid,
 * nudging unpaid participants, and real-time incoming nudge listeners.
 */
internal object PaymentRepository {

    /**
     * Real-time listener for all payment requests in a group, ordered chronologically.
     * Supports offline availability via Firestore persistent cache.
     */
    fun observeGroupPayments(
        groupId: String,
        onUpdate: (List<PaymentRequest>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = FirebaseProvider.observeQuery(
        query = FirebaseProvider.db.collection("groups").document(groupId).collection("payments"),
        onError = onError,
        mapper = { snapshot ->
            snapshot?.documents
                ?.mapNotNull { PaymentRequest.fromDocument(it) }
                ?.sortedBy { it.createdAt }
                ?: emptyList()
        },
        onUpdate = onUpdate
    )

    /**
     * Validates and constructs a new [PaymentRequest] in a group, handling member exclusions,
     * equal share calculation, and initial payment statuses.
     */
    fun buildPaymentRequest(
        paymentId: String,
        group: GroupChat,
        requesterId: String,
        requesterFallbackName: String = "User",
        totalAmount: Double,
        description: String,
        receiptDetails: String = "",
        receiptImageBase64: String = "",
        excludedMemberIds: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis()
    ): Result<PaymentRequest> {
        if (totalAmount <= 0.0) {
            return Result.failure(IllegalArgumentException("Amount must be greater than 0"))
        }
        val trimmedDesc = description.trim().take(GroupChat.MAX_PAYMENT_DESCRIPTION_LENGTH)
        if (trimmedDesc.isEmpty()) {
            return Result.failure(IllegalArgumentException("Description is required"))
        }

        val includedIds = group.memberIds.filter { !excludedMemberIds.contains(it) }
        if (includedIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one member must be included in the payment."))
        }

        val requesterName = group.members[requesterId]?.displayName ?: requesterFallbackName
        val share = Math.round((totalAmount / includedIds.size) * 100.0) / 100.0

        val statuses = includedIds.associateWith { memberId ->
            val memberName = group.members[memberId]?.displayName ?: "Member"
            val isRequester = memberId == requesterId
            ParticipantStatus(
                uid = memberId,
                displayName = memberName,
                amountOwed = share,
                paid = isRequester,
                paidAt = if (isRequester) now else 0L,
                paymentNote = if (isRequester) "Paid original bill" else "",
                lastNudgedAt = 0L
            )
        }

        val excludedNames = excludedMemberIds.mapNotNull { group.members[it]?.displayName }
        val formattedTotal = PayPalsRepository.formatAmount(totalAmount)
        val formattedShare = PayPalsRepository.formatAmount(share)
        val initialLog = PaymentLogEntry(
            actorUid = requesterId,
            actorName = requesterName,
            message = "$requesterName paid $formattedTotal for \"$trimmedDesc\" ($formattedShare each)",
            timestamp = now
        )

        return Result.success(
            PaymentRequest(
                id = paymentId,
                groupId = group.id,
                groupName = group.name,
                requesterId = requesterId,
                requesterName = requesterName,
                totalAmount = totalAmount,
                description = trimmedDesc,
                receiptDetails = receiptDetails.trim(),
                receiptImageBase64 = receiptImageBase64,
                includedMemberIds = includedIds,
                excludedMemberIds = excludedMemberIds.toList(),
                excludedMemberNames = excludedNames,
                sharePerPerson = share,
                participantStatuses = statuses,
                paymentLogs = listOf(initialLog),
                createdAt = now
            )
        )
    }

    /**
     * Tracks a new payment request in the group with fault tolerance and background retry.
     */
    fun createPaymentRequest(
        group: GroupChat,
        totalAmount: Double,
        description: String,
        receiptDetails: String,
        receiptImageBase64: String,
        excludedMemberIds: Set<String>,
        onResult: (Result<PaymentRequest>) -> Unit
    ) {
        val uid = UserProfileRepository.currentUserId()
        if (uid == null) {
            onResult(Result.failure(IllegalStateException("Not signed in")))
            return
        }
        val groupRef = FirebaseProvider.db.collection("groups").document(group.id)
        val paymentRef = groupRef.collection("payments").document()
        val now = System.currentTimeMillis()

        val paymentRequest = buildPaymentRequest(
            paymentId = paymentRef.id,
            group = group,
            requesterId = uid,
            requesterFallbackName = UserProfileRepository.currentUserName(),
            totalAmount = totalAmount,
            description = description,
            receiptDetails = receiptDetails,
            receiptImageBase64 = receiptImageBase64,
            excludedMemberIds = excludedMemberIds,
            now = now
        ).getOrElse { err ->
            onResult(Result.failure(err))
            return
        }

        val formattedTotal = PayPalsRepository.formatAmount(totalAmount)
        RequestSyncManager.executeWriteTask(
            actionLabel = "Track Payment $formattedTotal (${paymentRequest.description})",
            optimisticResult = paymentRequest,
            taskProvider = {
                val batch = FirebaseProvider.db.batch()
                batch.set(paymentRef, paymentRequest.toMap())
                batch.update(
                    groupRef,
                    mapOf(
                        "lastActivityText" to "${paymentRequest.requesterName} tracked $formattedTotal: ${paymentRequest.description}",
                        "lastActivityAt" to now
                    )
                )
                batch.commit()
            },
            onImmediateResult = onResult
        )
    }

    /**
     * Marks a member's share of a payment request as paid, with fault tolerance & background retry.
     */
    fun markShareAsPaid(
        payment: PaymentRequest,
        paymentNote: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val uid = UserProfileRepository.currentUserId()
        if (uid == null) {
            onResult(Result.failure(IllegalStateException("Not signed in")))
            return
        }

        val existingStatus = payment.participantStatuses[uid]
        if (existingStatus == null) {
            onResult(Result.failure(IllegalArgumentException("You are not included in this payment.")))
            return
        }
        if (existingStatus.paid) {
            onResult(Result.success(Unit))
            return
        }

        val now = System.currentTimeMillis()
        val trimmedNote = paymentNote.trim().take(GroupChat.MAX_PAYMENT_DESCRIPTION_LENGTH)
        val payerName = existingStatus.displayName.ifBlank { UserProfileRepository.currentUserName() }
        val updatedStatuses = payment.participantStatuses + (
            uid to existingStatus.copy(paid = true, paidAt = now, paymentNote = trimmedNote)
        )

        val formattedOwed = PayPalsRepository.formatAmount(existingStatus.amountOwed)
        val noteSuffix = if (trimmedNote.isNotEmpty()) " (\"$trimmedNote\")" else ""
        val updatedLogs = payment.paymentLogs + PaymentLogEntry(
            actorUid = uid,
            actorName = payerName,
            message = "$payerName paid their share of $formattedOwed$noteSuffix",
            timestamp = now
        )

        val groupRef = FirebaseProvider.db.collection("groups").document(payment.groupId)
        val paymentRef = groupRef.collection("payments").document(payment.id)

        RequestSyncManager.executeWriteTask(
            actionLabel = "Pay $formattedOwed for '${payment.description}'",
            optimisticResult = Unit,
            taskProvider = {
                val batch = FirebaseProvider.db.batch()
                batch.update(
                    paymentRef,
                    mapOf(
                        "participantStatuses" to updatedStatuses.mapValues { it.value.toMap() },
                        "paymentLogs" to updatedLogs.map { it.toMap() }
                    )
                )
                batch.update(
                    groupRef,
                    mapOf(
                        "lastActivityText" to "$payerName paid $formattedOwed for ${payment.description}",
                        "lastActivityAt" to now
                    )
                )
                batch.commit()
            },
            onImmediateResult = onResult
        )
    }

    /**
     * Pings all unpaid participants on a payment request.
     * Targets ONLY members who have not paid yet.
     */
    fun nudgeUnpaidParticipants(
        payment: PaymentRequest,
        targetUids: List<String>,
        onResult: (Result<Int>) -> Unit
    ) {
        val uid = UserProfileRepository.currentUserId()
        if (uid == null) {
            onResult(Result.failure(IllegalStateException("Not signed in")))
            return
        }

        val unpaidTargets = targetUids.mapNotNull { targetId ->
            payment.participantStatuses[targetId]?.takeIf { !it.paid && it.uid != uid }
        }
        if (unpaidTargets.isEmpty()) {
            onResult(Result.failure(IllegalArgumentException("All included members have already paid!")))
            return
        }

        val now = System.currentTimeMillis()
        val senderName = payment.participantStatuses[uid]?.displayName?.takeIf { it.isNotBlank() }
            ?: UserProfileRepository.currentUserName()
        val updatedStatuses = payment.participantStatuses.toMutableMap()
        unpaidTargets.forEach { target ->
            updatedStatuses[target.uid] = target.copy(lastNudgedAt = now)
        }

        val targetNames = unpaidTargets.joinToString(", ") { it.displayName }
        val updatedLogs = payment.paymentLogs + PaymentLogEntry(
            actorUid = uid,
            actorName = senderName,
            message = "$senderName nudged $targetNames",
            timestamp = now
        )

        RequestSyncManager.executeWriteTask(
            actionLabel = "Nudge $targetNames",
            optimisticResult = unpaidTargets.size,
            taskProvider = {
                val paymentRef = FirebaseProvider.db.collection("groups").document(payment.groupId)
                    .collection("payments").document(payment.id)

                val updateTask = paymentRef.update(
                    mapOf(
                        "participantStatuses" to updatedStatuses.mapValues { it.value.toMap() },
                        "paymentLogs" to updatedLogs.map { it.toMap() }
                    )
                )

                unpaidTargets.forEach { target ->
                    val notifRef = FirebaseProvider.db.collection("notifications").document()
                    val notif = NudgeNotification(
                        id = notifRef.id,
                        recipientId = target.uid,
                        senderId = uid,
                        senderName = senderName,
                        groupId = payment.groupId,
                        groupName = payment.groupName,
                        paymentId = payment.id,
                        paymentDescription = payment.description,
                        amountOwed = target.amountOwed,
                        createdAt = now,
                        delivered = false
                    )
                    notifRef.set(notif.toMap())
                }

                updateTask
            },
            onImmediateResult = onResult
        )
    }

    /**
     * Observes incoming unread nudges for the current user (both via `notifications`
     * and via `lastNudgedAt` updates on group payment requests) so the device can post
     * system notifications in real time.
     */
    fun observeIncomingNudges(
        userId: String,
        onNewNudge: (NudgeNotification) -> Unit
    ): ListenerRegistration {
        val seenNudgeKeys = mutableSetOf<String>()
        val lastSeenByPaymentId = mutableMapOf<String, Long>()
        val initializedPayments = mutableSetOf<String>()

        val notifReg = FirebaseProvider.db.collection("notifications")
            .whereEqualTo("recipientId", userId)
            .whereEqualTo("delivered", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (doc in snapshot.documents) {
                    val nudge = NudgeNotification.fromDocument(doc)
                    if (nudge != null && !nudge.delivered) {
                        doc.reference.update("delivered", true)
                        val dedupKey = "${nudge.paymentId}_${nudge.createdAt}"
                        if (seenNudgeKeys.add(dedupKey)) {
                            lastSeenByPaymentId[nudge.paymentId] = nudge.createdAt
                            onNewNudge(nudge)
                        }
                    }
                }
            }

        val groupPaymentsReg = FirebaseProvider.observeUserGroupPayments(userId) { paymentsByGroup ->
            for (payment in paymentsByGroup.values.flatten()) {
                val myStatus = payment.participantStatuses[userId] ?: continue
                val prevNudgedAt = lastSeenByPaymentId[payment.id] ?: 0L
                if (!initializedPayments.contains(payment.id)) {
                    initializedPayments.add(payment.id)
                    lastSeenByPaymentId[payment.id] = myStatus.lastNudgedAt
                } else if (!myStatus.paid && myStatus.lastNudgedAt > prevNudgedAt) {
                    lastSeenByPaymentId[payment.id] = myStatus.lastNudgedAt
                    val dedupKey = "${payment.id}_${myStatus.lastNudgedAt}"
                    if (seenNudgeKeys.add(dedupKey)) {
                        onNewNudge(
                            NudgeNotification(
                                id = dedupKey,
                                recipientId = userId,
                                senderId = payment.requesterId,
                                senderName = payment.requesterName,
                                groupId = payment.groupId,
                                groupName = payment.groupName,
                                paymentId = payment.id,
                                paymentDescription = payment.description,
                                amountOwed = myStatus.amountOwed,
                                createdAt = myStatus.lastNudgedAt,
                                delivered = true
                            )
                        )
                    }
                }
            }
        }

        return ListenerRegistration {
            notifReg.remove()
            groupPaymentsReg.remove()
        }
    }
}
