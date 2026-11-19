package com.sdu.paypals.repository

import com.google.firebase.firestore.ListenerRegistration
import com.sdu.paypals.model.GroupChat
import com.sdu.paypals.model.GroupMember
import com.sdu.paypals.model.NudgeNotification
import com.sdu.paypals.model.PaymentRequest
import com.sdu.paypals.model.UserActivityItem
import com.sdu.paypals.model.UserBalanceSummary
import java.util.Locale

/**
 * Unified facade repository that unites all domain-specific repositories in `com.sdu.paypals.repository`:
 * - [UserProfileRepository]: User identity, account validation, profile sync, and invite claiming.
 * - [GroupRepository]: Group creation, QR payloads, member lookups, and admin permissions.
 * - [PaymentRepository]: Payment request creation, split calculations, paying shares, and nudges.
 * - [ActivityAndBalanceRepository]: Chronological user activity feed and owed/owes balance calculations.
 */
object PayPalsRepository {

    // =========================================================================
    // 1. User Identity & Profile (UserProfileRepository)
    // =========================================================================

    fun currentUserId(): String? =
        UserProfileRepository.currentUserId()

    fun currentUserName(): String =
        UserProfileRepository.currentUserName()

    fun currentUserEmail(): String =
        UserProfileRepository.currentUserEmail()

    fun validateAndBuildAccountProfile(
        uid: String,
        fullName: String,
        email: String,
        password: String,
        now: Long = System.currentTimeMillis()
    ): Result<Map<String, Any>> =
        UserProfileRepository.validateAndBuildAccountProfile(uid, fullName, email, password, now)

    fun syncCurrentUserProfile(onComplete: (() -> Unit)? = null) =
        UserProfileRepository.syncCurrentUserProfile(onComplete)

    fun propagateProfileUpdate(
        newDisplayName: String,
        newEmail: String,
        onComplete: (() -> Unit)? = null
    ) = UserProfileRepository.propagateProfileUpdate(newDisplayName, newEmail, onComplete)

    fun inviteIdForEmail(email: String): String =
        UserProfileRepository.inviteIdForEmail(email)

    // =========================================================================
    // 2. Group Management & QR Joining (GroupRepository)
    // =========================================================================

    const val QR_PREFIX = GroupRepository.QR_PREFIX

    fun buildGroupQrPayload(groupId: String): String =
        GroupRepository.buildGroupQrPayload(groupId)

    fun extractGroupIdFromQr(rawValue: String?): String? =
        GroupRepository.extractGroupIdFromQr(rawValue)

    fun observeUserGroups(
        userId: String,
        onUpdate: (List<GroupChat>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        GroupRepository.observeUserGroups(userId, onUpdate, onError)

    fun observeGroup(
        groupId: String,
        onUpdate: (GroupChat?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        GroupRepository.observeGroup(groupId, onUpdate, onError)

    fun buildNewGroup(
        groupId: String,
        creatorUid: String,
        creatorName: String,
        creatorEmail: String,
        name: String,
        description: String,
        iconKey: String,
        now: Long = System.currentTimeMillis()
    ): Result<GroupChat> =
        GroupRepository.buildNewGroup(
            groupId = groupId,
            creatorUid = creatorUid,
            creatorName = creatorName,
            creatorEmail = creatorEmail,
            name = name,
            description = description,
            iconKey = iconKey,
            now = now
        )

    fun createGroup(
        name: String,
        description: String,
        iconKey: String,
        onResult: (Result<GroupChat>) -> Unit
    ) = GroupRepository.createGroup(name, description, iconKey, onResult)

    fun updateGroupDetails(
        group: GroupChat,
        newName: String,
        newDescription: String,
        newIconKey: String,
        onResult: (Result<Unit>) -> Unit
    ) = GroupRepository.updateGroupDetails(group, newName, newDescription, newIconKey, onResult)

    fun joinGroupById(
        groupId: String,
        onResult: (Result<GroupChat>) -> Unit
    ) = GroupRepository.joinGroupById(groupId, onResult)

    fun addMemberByEmail(
        group: GroupChat,
        emailInput: String,
        onResult: (Result<GroupMember>) -> Unit
    ) = GroupRepository.addMemberByEmail(group, emailInput, onResult)

    fun deleteGroup(
        group: GroupChat,
        onResult: (Result<Unit>) -> Unit
    ) = GroupRepository.deleteGroup(group, onResult)

    fun assignAdmin(
        group: GroupChat,
        targetUserId: String,
        onResult: (Result<Unit>) -> Unit
    ) = GroupRepository.assignAdmin(group, targetUserId, onResult)

    fun leaveGroup(
        group: GroupChat,
        onResult: (Result<Unit>) -> Unit
    ) = GroupRepository.leaveGroup(group, onResult)

    // =========================================================================
    // 3. Payment Requests & Nudges (PaymentRepository)
    // =========================================================================

    fun observeGroupPayments(
        groupId: String,
        onUpdate: (List<PaymentRequest>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        PaymentRepository.observeGroupPayments(groupId, onUpdate, onError)

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
    ): Result<PaymentRequest> =
        PaymentRepository.buildPaymentRequest(
            paymentId = paymentId,
            group = group,
            requesterId = requesterId,
            requesterFallbackName = requesterFallbackName,
            totalAmount = totalAmount,
            description = description,
            receiptDetails = receiptDetails,
            receiptImageBase64 = receiptImageBase64,
            excludedMemberIds = excludedMemberIds,
            now = now
        )

    fun createPaymentRequest(
        group: GroupChat,
        totalAmount: Double,
        description: String,
        receiptDetails: String,
        receiptImageBase64: String,
        excludedMemberIds: Set<String>,
        onResult: (Result<PaymentRequest>) -> Unit
    ) = PaymentRepository.createPaymentRequest(
        group = group,
        totalAmount = totalAmount,
        description = description,
        receiptDetails = receiptDetails,
        receiptImageBase64 = receiptImageBase64,
        excludedMemberIds = excludedMemberIds,
        onResult = onResult
    )

    fun markShareAsPaid(
        payment: PaymentRequest,
        paymentNote: String,
        onResult: (Result<Unit>) -> Unit
    ) = PaymentRepository.markShareAsPaid(payment, paymentNote, onResult)

    fun nudgeUnpaidParticipants(
        payment: PaymentRequest,
        targetUids: List<String>,
        onResult: (Result<Int>) -> Unit
    ) = PaymentRepository.nudgeUnpaidParticipants(payment, targetUids, onResult)

    fun observeIncomingNudges(
        userId: String,
        onNewNudge: (NudgeNotification) -> Unit
    ): ListenerRegistration =
        PaymentRepository.observeIncomingNudges(userId, onNewNudge)

    // =========================================================================
    // 4. Activity Feed & Balance Calculation (ActivityAndBalanceRepository)
    // =========================================================================

    fun observeUserActivityFeed(
        userId: String,
        onUpdate: (List<UserActivityItem>) -> Unit
    ): () -> Unit =
        ActivityAndBalanceRepository.observeUserActivityFeed(userId, onUpdate)

    fun calculateUserBalance(
        userId: String,
        payments: List<PaymentRequest>
    ): UserBalanceSummary =
        ActivityAndBalanceRepository.calculateUserBalance(userId, payments)

    fun observeTotalUserBalance(
        userId: String,
        onUpdate: (UserBalanceSummary) -> Unit
    ): () -> Unit =
        ActivityAndBalanceRepository.observeTotalUserBalance(userId, onUpdate)

    // =========================================================================
    // 5. Formatting Helper
    // =========================================================================

    fun formatAmount(amount: Double): String =
        String.format(Locale.US, "$%.2f", amount)
}
