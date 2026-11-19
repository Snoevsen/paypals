---
tags:
  - paypals
  - repository
  - pattern-facade
package: com.sdu.paypals.repository
source_path: app/src/main/java/com/sdu/paypals/repository/PayPalsRepository.kt
aliases:
  - PayPalsRepository.kt
---

# PayPalsRepository

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/repository/PayPalsRepository.kt`  
> **Facade Pattern** uniting all domain-specific repositories under one public entry point used by activities, fragments, adapters, notification helpers, and unit tests.

## Facade Sections

### User Identity & Profile
Delegates to [[UserProfileRepository]]: `currentUserId`, `currentUserName`, `currentUserEmail`, `validateAndBuildAccountProfile`, `syncCurrentUserProfile`, `propagateProfileUpdate`, `inviteIdForEmail`.

### Group Management
Delegates to [[GroupRepository]]: `QR_PREFIX`, `buildGroupQrPayload`, `extractGroupIdFromQr`, `observeUserGroups`, `observeGroup`, `buildNewGroup`, `createGroup`, `updateGroupDetails`, `joinGroupById`, `addMemberByEmail`, `deleteGroup`, `assignAdmin`, `leaveGroup`.

### Payment Requests & Nudges
Delegates to [[PaymentRepository]]: `observeGroupPayments`, `buildPaymentRequest`, `createPaymentRequest`, `markShareAsPaid`, `nudgeUnpaidParticipants`, `observeIncomingNudges`.

### Activity Feed & Balance
Delegates to [[ActivityAndBalanceRepository]]: `observeUserActivityFeed`, `calculateUserBalance`, `observeTotalUserBalance`.

### Formatting
- `formatAmount(amount: Double)`: Formats fixed US dollar strings (`$XX.XX`) uniformly across the app.

## Links & Dependencies
- **Delegates to**: [[UserProfileRepository]] · [[GroupRepository]] · [[PaymentRepository]] · [[ActivityAndBalanceRepository]]
- **Consumed by**: [[MainActivity]] · [[LoginFragment]] · [[HomeFragment]] · [[GroupsFragment]] · [[CreateGroupFragment]] · [[GroupChatFragment]] · [[GroupBalanceFragment]] · [[GroupSettingsFragment]] · [[ActivityFragment]] · [[ProfileFragment]] · [[ActivityAdapter]] · [[GroupPaymentAdapter]] · [[NotificationHelper]] · [[UiHelpers]] · [[ExampleUnitTest]]
