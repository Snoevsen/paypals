---
tags:
  - paypals
  - test
  - junit
  - unit-test
package: com.sdu.paypals
source_path: app/src/test/java/com/sdu/paypals/ExampleUnitTest.kt
aliases:
  - ExampleUnitTest.kt
---

# ExampleUnitTest

> [!info] File Summary
> **Path**: `app/src/test/java/com/sdu/paypals/ExampleUnitTest.kt`  
> JUnit 4 local JVM unit test suite executed by Gradle (`testDebugUnitTest`) and pre-release CI verification in `release.yml`.

## Test Coverage (14 Tests)
- `soleAdminWithOtherMembers_cannotLeaveUntilAnotherAdminAssigned` & `soleMemberAdmin_canLeaveGroup` -> tests `GroupChat.canUserLeave` in [[GroupChat]].
- `paymentRequest_tracksPaidCountAndExclusions` -> tests `PaymentRequest` counts in [[PaymentModels]].
- `qrCodePayload_buildsAndExtractsGroupIdCorrectly` -> tests [[PayPalsRepository]] / [[GroupRepository]] QR helpers.
- `activityFilterAndSort_filtersExpensesPaidByMeVsPaidToMeAndSearchesAndLimits` -> tests `ActivityFragment.filterAndSortActivities` Strategy filtering/sorting/limit clamping in [[ActivityFragment]].
- `formatAmount_formatsDollarAmountsConsistently` -> tests `PayPalsRepository.formatAmount` in [[PayPalsRepository]].
- `calculateUserBalance_computesOwedToMeAndIOweCorrectly` -> tests [[ActivityAndBalanceRepository]] balance calculations, `UserBalanceSummary.formatSummaryText`, and `ActivityFragment.SortMode.next()`.
- `accountCreation_validatesInputsAndCreatesProfileRecord` -> tests `validateAndBuildAccountProfile` in [[UserProfileRepository]].
- `groupCreation_buildsGroupWithCreatorAsMemberAndAdmin` -> tests `buildNewGroup` in [[GroupRepository]].
- `paymentRequestCreation_splitsAmountExcludesMembersAndTracksStatuses` -> tests `buildPaymentRequest` in [[PaymentRepository]].
- `savedStateHandle_preservesLoginAndCreateGroupDraftsAcrossProcessDeath` -> tests [[LoginViewModel]] & [[CreateGroupViewModel]] across simulated process death.
- `savedStateHandle_preservesOpenPaymentDialogAndExcludedMembersAcrossProcessDeath` -> tests [[GroupChatViewModel]] modal dialog & checkbox preservation.
- `savedStateHandle_preservesActivityFiltersAndGroupSettingsEditsAcrossProcessDeath` -> tests [[ActivityFilterViewModel]] & [[GroupSettingsViewModel]] preservation.
- `pendingWriteQueue_supportsOfflineDeduplicationAndTransientErrorClassification` -> tests [[PendingWriteQueue]] deduplication, single-completion semantics, and transient failure classification.

## Links & Dependencies
- **Tested Targets**: [[GroupChat]] · [[PaymentModels]] · [[PayPalsRepository]] · [[UserProfileRepository]] · [[GroupRepository]] · [[PaymentRepository]] · [[ActivityAndBalanceRepository]] · [[ActivityFragment]] · [[LoginViewModel]] · [[CreateGroupViewModel]] · [[GroupChatViewModel]] · [[ActivityFilterViewModel]] · [[GroupSettingsViewModel]] · [[PendingWriteQueue]]
- **Run in CI**: [[01 - Build, Config & CI-CD#release.yml|release.yml]]
