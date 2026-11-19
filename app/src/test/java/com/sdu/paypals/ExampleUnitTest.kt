package com.sdu.paypals

import androidx.lifecycle.SavedStateHandle
import com.sdu.paypals.model.ActivityCategory
import com.sdu.paypals.model.GroupChat
import com.sdu.paypals.model.GroupIcon
import com.sdu.paypals.model.GroupMember
import com.sdu.paypals.model.ParticipantStatus
import com.sdu.paypals.model.PaymentRequest
import com.sdu.paypals.model.UserActivityItem
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.sync.PendingWriteQueue
import com.sdu.paypals.viewmodel.ActivityFilterViewModel
import com.sdu.paypals.viewmodel.CreateGroupViewModel
import com.sdu.paypals.viewmodel.GroupChatViewModel
import com.sdu.paypals.viewmodel.GroupSettingsViewModel
import com.sdu.paypals.viewmodel.LoginViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for reusable GroupChat admin/leaving rules, PaymentRequest logic,
 * QR payload parsing, and Activity feed filtering/sorting/card clamping.
 */
class ExampleUnitTest {

    @Test
    fun soleAdminWithOtherMembers_cannotLeaveUntilAnotherAdminAssigned() {
        val group = GroupChat(
            id = "g1",
            name = "Road Trip",
            memberIds = listOf("user_admin", "user_2"),
            adminIds = listOf("user_admin")
        )

        // Sole admin cannot leave while user_2 is in the group and not an admin
        assertFalse(group.canUserLeave("user_admin"))
        // Non-admin member can leave freely
        assertTrue(group.canUserLeave("user_2"))

        // After promoting user_2 to admin, user_admin can now leave
        val updatedGroup = group.copy(adminIds = listOf("user_admin", "user_2"))
        assertTrue(updatedGroup.canUserLeave("user_admin"))
    }

    @Test
    fun soleMemberAdmin_canLeaveGroup() {
        val group = GroupChat(
            id = "g2",
            name = "Solo Group",
            memberIds = listOf("user_admin"),
            adminIds = listOf("user_admin")
        )

        assertTrue(group.canUserLeave("user_admin"))
    }

    @Test
    fun paymentRequest_tracksPaidCountAndExclusions() {
        val statuses = mapOf(
            "u1" to ParticipantStatus(uid = "u1", displayName = "Alice", amountOwed = 50.0, paid = true),
            "u2" to ParticipantStatus(uid = "u2", displayName = "Bob", amountOwed = 50.0, paid = false)
        )
        val payment = PaymentRequest(
            id = "p1",
            groupId = "g1",
            requesterId = "u1",
            requesterName = "Alice",
            totalAmount = 100.0,
            description = "Dinner",
            includedMemberIds = listOf("u1", "u2"),
            excludedMemberIds = listOf("u3"),
            excludedMemberNames = listOf("Charlie"),
            sharePerPerson = 50.0,
            participantStatuses = statuses
        )

        assertEquals(1, payment.paidCount())
        assertEquals(2, payment.totalParticipantCount())
        assertFalse(payment.isFullyPaid())
    }

    @Test
    fun qrCodePayload_buildsAndExtractsGroupIdCorrectly() {
        val payload = PayPalsRepository.buildGroupQrPayload("group_abc_123")
        assertEquals("paypals://join?groupId=group_abc_123", payload)
        assertEquals("group_abc_123", PayPalsRepository.extractGroupIdFromQr(payload))
    }

    @Test
    fun activityFilterAndSort_filtersExpensesPaidByMeVsPaidToMeAndSearchesAndLimits() {
        val items = listOf(
            UserActivityItem(
                id = "1",
                groupId = "g1",
                groupName = "Weekend",
                payerName = "Alice",
                description = "Pizza",
                amount = 120.0,
                timestamp = 1700000000000L,
                category = ActivityCategory.EXPENSE_PAID_BY_ME
            ),
            UserActivityItem(
                id = "2",
                groupId = "g1",
                groupName = "Weekend",
                payerName = "Bob",
                description = "Pizza",
                amount = 60.0,
                timestamp = 1700000500000L,
                category = ActivityCategory.PAID_TO_ME
            ),
            UserActivityItem(
                id = "3",
                groupId = "g1",
                groupName = "Weekend",
                payerName = "Charlie",
                description = "Tickets",
                amount = 45.0,
                timestamp = 1700000900000L,
                category = ActivityCategory.REMINDER
            )
        )

        // Default: ALL_ACTIVITIES, newest first
        val all = ActivityFragment.filterAndSortActivities(
            items = items,
            filterMode = ActivityFragment.FilterMode.ALL_ACTIVITIES,
            query = "",
            sortMode = ActivityFragment.SortMode.NEWEST_FIRST,
            limit = 10
        )
        assertEquals(listOf("3", "2", "1"), all.map { it.id })

        // Expenses active (clicked) -> shows what user has paid (EXPENSES_PAID_BY_ME)
        val paidByMe = ActivityFragment.filterAndSortActivities(
            items = items,
            filterMode = ActivityFragment.FilterMode.EXPENSES_PAID_BY_ME,
            query = "",
            sortMode = ActivityFragment.SortMode.NEWEST_FIRST,
            limit = 10
        )
        assertEquals(listOf("1"), paidByMe.map { it.id })

        // Expenses unclicked/toggled off -> shows what user has been paid (PAID_TO_ME)
        val paidToMe = ActivityFragment.filterAndSortActivities(
            items = items,
            filterMode = ActivityFragment.FilterMode.PAID_TO_ME,
            query = "",
            sortMode = ActivityFragment.SortMode.NEWEST_FIRST,
            limit = 10
        )
        assertEquals(listOf("2"), paidToMe.map { it.id })

        // Reminders filter
        val reminders = ActivityFragment.filterAndSortActivities(
            items = items,
            filterMode = ActivityFragment.FilterMode.REMINDERS,
            query = "",
            sortMode = ActivityFragment.SortMode.NEWEST_FIRST,
            limit = 10
        )
        assertEquals(listOf("3"), reminders.map { it.id })

        // Search by payer name ("Bob") or amount ("60.00")
        val searchedByName = ActivityFragment.filterAndSortActivities(
            items = items,
            filterMode = ActivityFragment.FilterMode.ALL_ACTIVITIES,
            query = "Bob",
            sortMode = ActivityFragment.SortMode.NEWEST_FIRST,
            limit = 25
        )
        assertEquals(listOf("2"), searchedByName.map { it.id })
    }

    @Test
    fun formatAmount_formatsDollarAmountsConsistently() {
        assertEquals("$60.00", PayPalsRepository.formatAmount(60.0))
        assertEquals("$0.00", PayPalsRepository.formatAmount(0.0))
        assertEquals("$12.50", PayPalsRepository.formatAmount(12.5))
    }

    @Test
    fun calculateUserBalance_computesOwedToMeAndIOweCorrectly() {
        val reqByAlice = PaymentRequest(
            id = "p1",
            groupId = "g1",
            requesterId = "alice",
            requesterName = "Alice",
            totalAmount = 90.0,
            description = "Dinner",
            sharePerPerson = 30.0,
            participantStatuses = mapOf(
                "alice" to ParticipantStatus(uid = "alice", displayName = "Alice", amountOwed = 30.0, paid = true),
                "bob" to ParticipantStatus(uid = "bob", displayName = "Bob", amountOwed = 30.0, paid = false),
                "charlie" to ParticipantStatus(uid = "charlie", displayName = "Charlie", amountOwed = 30.0, paid = true)
            )
        )
        val reqByBob = PaymentRequest(
            id = "p2",
            groupId = "g1",
            requesterId = "bob",
            requesterName = "Bob",
            totalAmount = 40.0,
            description = "Snacks",
            sharePerPerson = 20.0,
            participantStatuses = mapOf(
                "alice" to ParticipantStatus(uid = "alice", displayName = "Alice", amountOwed = 20.0, paid = false),
                "bob" to ParticipantStatus(uid = "bob", displayName = "Bob", amountOwed = 20.0, paid = true)
            )
        )

        val aliceBalance = PayPalsRepository.calculateUserBalance("alice", listOf(reqByAlice, reqByBob))
        assertEquals(30.0, aliceBalance.totalOwedToMe, 0.001)
        assertEquals(20.0, aliceBalance.totalIOwe, 0.001)
        assertEquals(1, aliceBalance.owedByMembers.size)
        assertEquals("Bob", aliceBalance.owedByMembers.first().displayName)
        assertEquals(1, aliceBalance.iOweToMembers.size)
        assertEquals("Bob", aliceBalance.iOweToMembers.first().displayName)
        assertEquals(
            "You are owed $30.00 and owe $20.00",
            aliceBalance.formatSummaryText(inGroup = true, PayPalsRepository::formatAmount)
        )

        assertEquals(ActivityFragment.SortMode.OLDEST_FIRST, ActivityFragment.SortMode.NEWEST_FIRST.next())
        assertEquals(ActivityFragment.SortMode.AMOUNT_DESC, ActivityFragment.SortMode.OLDEST_FIRST.next())
        assertEquals(ActivityFragment.SortMode.NEWEST_FIRST, ActivityFragment.SortMode.AMOUNT_DESC.next())
    }

    @Test
    fun accountCreation_validatesInputsAndCreatesProfileRecord() {
        // 1. Reject empty full name
        val emptyNameRes = PayPalsRepository.validateAndBuildAccountProfile(
            uid = "u_1",
            fullName = "   ",
            email = "alice@example.com",
            password = "password123"
        )
        assertTrue(emptyNameRes.isFailure)

        // 2. Reject invalid email
        val badEmailRes = PayPalsRepository.validateAndBuildAccountProfile(
            uid = "u_1",
            fullName = "Alice Johnson",
            email = "not-an-email",
            password = "password123"
        )
        assertTrue(badEmailRes.isFailure)

        // 3. Reject short password (< 6 chars)
        val shortPassRes = PayPalsRepository.validateAndBuildAccountProfile(
            uid = "u_1",
            fullName = "Alice Johnson",
            email = "alice@example.com",
            password = "123"
        )
        assertTrue(shortPassRes.isFailure)

        // 4. Succeed with valid account creation inputs
        val validRes = PayPalsRepository.validateAndBuildAccountProfile(
            uid = "u_1",
            fullName = "Alice Johnson",
            email = "Alice@Example.com",
            password = "password123",
            now = 1700000000000L
        )
        assertTrue(validRes.isSuccess)
        val profile = validRes.getOrThrow()
        assertEquals("u_1", profile["uid"])
        assertEquals("Alice Johnson", profile["displayName"])
        assertEquals("alice@example.com", profile["email"])
    }

    @Test
    fun groupCreation_buildsGroupWithCreatorAsMemberAndAdmin() {
        // Reject blank group name
        val invalidGroup = PayPalsRepository.buildNewGroup(
            groupId = "grp_1",
            creatorUid = "u_1",
            creatorName = "Alice Johnson",
            creatorEmail = "alice@example.com",
            name = "   ",
            description = "Summer trip",
            iconKey = "trip"
        )
        assertTrue(invalidGroup.isFailure)

        // Succeed with valid group creation
        val groupRes = PayPalsRepository.buildNewGroup(
            groupId = "grp_1",
            creatorUid = "u_1",
            creatorName = "Alice Johnson",
            creatorEmail = "alice@example.com",
            name = "Summer Trip",
            description = "Shared expenses for Spain",
            iconKey = "trip",
            now = 1700000000000L
        )
        assertTrue(groupRes.isSuccess)
        val group = groupRes.getOrThrow()
        assertEquals("grp_1", group.id)
        assertEquals("Summer Trip", group.name)
        assertEquals("Shared expenses for Spain", group.description)
        assertEquals("trip", group.iconKey)
        assertTrue(group.isMember("u_1"))
        assertTrue(group.isAdmin("u_1"))
        assertEquals("Alice Johnson", group.members["u_1"]?.displayName)
    }

    @Test
    fun paymentRequestCreation_splitsAmountExcludesMembersAndTracksStatuses() {
        val baseGroup = PayPalsRepository.buildNewGroup(
            groupId = "grp_1",
            creatorUid = "u_1",
            creatorName = "Alice",
            creatorEmail = "alice@example.com",
            name = "Apartment",
            description = "Roommates",
            iconKey = "home"
        ).getOrThrow()

        val groupWithThreeMembers = baseGroup.copy(
            memberIds = listOf("u_1", "u_2", "u_3"),
            members = mapOf(
                "u_1" to GroupMember(uid = "u_1", displayName = "Alice", email = "alice@example.com"),
                "u_2" to GroupMember(uid = "u_2", displayName = "Bob", email = "bob@example.com"),
                "u_3" to GroupMember(uid = "u_3", displayName = "Charlie", email = "charlie@example.com")
            )
        )

        // Create a request of $120.00 excluding Charlie ("u_3")
        val reqRes = PayPalsRepository.buildPaymentRequest(
            paymentId = "pay_1",
            group = groupWithThreeMembers,
            requesterId = "u_1",
            totalAmount = 120.0,
            description = "Groceries",
            excludedMemberIds = setOf("u_3"),
            now = 1700000000000L
        )

        assertTrue(reqRes.isSuccess)
        val req = reqRes.getOrThrow()
        assertEquals("pay_1", req.id)
        assertEquals(120.0, req.totalAmount, 0.001)
        assertEquals(60.0, req.sharePerPerson, 0.001)
        assertEquals(listOf("u_1", "u_2"), req.includedMemberIds)
        assertEquals(listOf("Charlie"), req.excludedMemberNames)
        assertTrue(req.participantStatuses["u_1"]?.paid == true)
        assertFalse(req.participantStatuses["u_2"]?.paid == true)
        assertEquals(1, req.paidCount())
        assertEquals(2, req.totalParticipantCount())
    }

    /**
     * Simulates OS SavedStateRegistry serialization across process death by snapshotting
     * all persisted keys into a fresh [SavedStateHandle].
     */
    private fun recreateSavedStateAfterProcessDeath(original: SavedStateHandle): SavedStateHandle {
        val persistedBundleMap = original.keys().associateWith { key -> original.get<Any?>(key) }
        return SavedStateHandle(persistedBundleMap)
    }

    @Test
    fun savedStateHandle_preservesLoginAndCreateGroupDraftsAcrossProcessDeath() {
        // 1. LoginViewModel during multi-field sign-up entry before process kill
        val loginHandle = SavedStateHandle()
        val loginVm = LoginViewModel(loginHandle)
        loginVm.setSignUpMode(true)
        loginVm.updateFullName("Alice Johnson")
        loginVm.updateEmail("alice@example.com")

        val restoredLoginVm = LoginViewModel(recreateSavedStateAfterProcessDeath(loginHandle))
        assertTrue(restoredLoginVm.isSignUpMode.value)
        assertEquals("Alice Johnson", restoredLoginVm.fullName.value)
        assertEquals("alice@example.com", restoredLoginVm.email.value)

        restoredLoginVm.clearState()
        assertFalse(restoredLoginVm.isSignUpMode.value)
        assertEquals("", restoredLoginVm.fullName.value)

        // 2. CreateGroupViewModel during group form entry before process kill
        val createHandle = SavedStateHandle()
        val createVm = CreateGroupViewModel(createHandle)
        createVm.updateGroupName("Norway Ski Trip")
        createVm.updateGroupDescription("Cabin, lift passes & food")
        createVm.updateSelectedIconKey(GroupIcon.TRIP.key)

        val restoredCreateVm = CreateGroupViewModel(recreateSavedStateAfterProcessDeath(createHandle))
        assertEquals("Norway Ski Trip", restoredCreateVm.groupName.value)
        assertEquals("Cabin, lift passes & food", restoredCreateVm.groupDescription.value)
        assertEquals(GroupIcon.TRIP.key, restoredCreateVm.selectedIconKey.value)

        restoredCreateVm.clearDraft()
        assertEquals("", restoredCreateVm.groupName.value)
        assertEquals(GroupIcon.GROUPS.key, restoredCreateVm.selectedIconKey.value)
    }

    @Test
    fun savedStateHandle_preservesOpenPaymentDialogAndExcludedMembersAcrossProcessDeath() {
        val handle = SavedStateHandle()
        val originalVm = GroupChatViewModel(handle)

        // Simulate user opening Request dialog, typing bill details, and excluding Bob & Charlie
        originalVm.openCreateDialog()
        originalVm.updateDraftAmount("180.50")
        originalVm.updateDraftDescription("Ferry tickets")
        originalVm.setMemberExcluded("u_2", true)
        originalVm.setMemberExcluded("u_3", true)
        originalVm.openPayShareDialog("pay_99")
        originalVm.updatePayShareNote("Sent via MobilePay")

        // Simulate OS terminating app in background -> recreate fresh ViewModel from SavedStateHandle
        val restoredVm = GroupChatViewModel(recreateSavedStateAfterProcessDeath(handle))
        assertTrue(restoredVm.isCreateDialogOpen.value)
        assertEquals("180.50", restoredVm.draftAmount.value)
        assertEquals("Ferry tickets", restoredVm.draftDescription.value)
        assertEquals(listOf("u_2", "u_3"), restoredVm.excludedMemberIds.value)
        assertEquals("pay_99", restoredVm.pendingPaySharePaymentId.value)
        assertEquals("Sent via MobilePay", restoredVm.draftPayShareNote.value)

        // Submitting/closing clears temporary dialog drafts
        restoredVm.clearCreateDialogState()
        restoredVm.clearPayShareDialogState()
        assertFalse(restoredVm.isCreateDialogOpen.value)
        assertEquals("", restoredVm.draftAmount.value)
        assertTrue(restoredVm.excludedMemberIds.value.isEmpty())
        assertNull(restoredVm.pendingPaySharePaymentId.value)
        assertEquals("", restoredVm.draftPayShareNote.value)
    }

    @Test
    fun savedStateHandle_preservesActivityFiltersAndGroupSettingsEditsAcrossProcessDeath() {
        // Activity search, filter strategy, sort strategy, and max-cards preference
        val activityHandle = SavedStateHandle()
        val activityVm = ActivityFilterViewModel(activityHandle)
        activityVm.updateSearchQuery("Dinner")
        activityVm.setFilterMode(ActivityFragment.FilterMode.EXPENSES_PAID_BY_ME)
        activityVm.cycleSortMode() // OLDEST_FIRST
        activityVm.cycleSortMode() // AMOUNT_DESC
        activityVm.toggleCardLimit() // 25

        val restoredActivityVm = ActivityFilterViewModel(recreateSavedStateAfterProcessDeath(activityHandle))
        assertEquals("Dinner", restoredActivityVm.searchQuery.value)
        assertEquals(ActivityFragment.FilterMode.EXPENSES_PAID_BY_ME, restoredActivityVm.currentFilterMode())
        assertEquals(ActivityFragment.SortMode.AMOUNT_DESC, restoredActivityVm.currentSortMode())
        assertEquals(25, restoredActivityVm.maxCardsLimit.value)

        // GroupSettingsViewModel keeps unsaved user edits rather than reverting to Firestore snapshot
        val settingsHandle = SavedStateHandle()
        val settingsVm = GroupSettingsViewModel(settingsHandle)
        val serverGroup = GroupChat(id = "grp_7", name = "Old Name", description = "Old Desc", iconKey = "groups")
        assertTrue(settingsVm.populateFromGroupIfNeeded(serverGroup))
        settingsVm.updateDraftName("Edited Trip Name")
        settingsVm.updateDraftIconKey(GroupIcon.FOOD.key)
        settingsVm.updateDraftMemberEmail("new.pal@example.com")

        val restoredSettingsVm = GroupSettingsViewModel(recreateSavedStateAfterProcessDeath(settingsHandle))
        assertFalse(restoredSettingsVm.populateFromGroupIfNeeded(serverGroup))
        assertEquals("Edited Trip Name", restoredSettingsVm.draftName.value)
        assertEquals(GroupIcon.FOOD.key, restoredSettingsVm.draftIconKey.value)
        assertEquals("new.pal@example.com", restoredSettingsVm.draftMemberEmail.value)
    }

    @Test
    fun pendingWriteQueue_supportsOfflineDeduplicationAndTransientErrorClassification() {
        val queue = PendingWriteQueue<String>()

        assertTrue(queue.enqueueIfAbsent("req_1", "Create Group 'Trip'", "payload_1"))
        assertFalse(queue.enqueueIfAbsent("req_1", "Create Group 'Trip'", "payload_1"))
        assertTrue(queue.enqueueIfAbsent("req_2", "Track Payment $60.00", "payload_2"))
        assertEquals(2, queue.count())

        val snapshot = queue.snapshot()
        assertTrue(queue.markCompleted(snapshot[0]))
        // Completing same item twice must return false so user notification fires once
        assertFalse(queue.markCompleted(snapshot[0]))
        assertEquals(1, queue.count())

        assertTrue(queue.removeById("req_2"))
        assertEquals(0, queue.count())

        // Classify offline and transient network error codes vs permanent failures
        assertTrue(PendingWriteQueue.isTransientNetworkFailure(isNetworkAvailable = false, error = null))
        assertTrue(PendingWriteQueue.isTransientCodeName(isNetworkAvailable = true, codeName = "UNAVAILABLE"))
        assertTrue(PendingWriteQueue.isTransientCodeName(isNetworkAvailable = true, codeName = "DEADLINE_EXCEEDED"))
        assertFalse(PendingWriteQueue.isTransientCodeName(isNetworkAvailable = true, codeName = "PERMISSION_DENIED"))
        assertFalse(
            PendingWriteQueue.isTransientNetworkFailure(
                isNetworkAvailable = true,
                error = IllegalArgumentException("Validation failure")
            )
        )
    }
}
