---
tags:
  - paypals
  - repository
  - groups
  - qr
package: com.sdu.paypals.repository
source_path: app/src/main/java/com/sdu/paypals/repository/GroupRepository.kt
aliases:
  - GroupRepository.kt
---

# GroupRepository

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/repository/GroupRepository.kt`  
> Internal repository encapsulating group creation, real-time group observation, QR payload encoding/parsing, email member lookup, and admin permissions.

## Responsibilities
- **QR Payload Handling**: `QR_PREFIX = "paypals://join?groupId="`, `buildGroupQrPayload(...)`, `extractGroupIdFromQr(...)`.
- **Real-Time Streams**: `observeUserGroups(...)` (sorted descending by `lastActivityAt`) and `observeGroup(...)` built over `observeQuery` / `observeDocument` in [[FirebaseProvider]].
- **Pure Construction**: `buildNewGroup(...)` validates group name & creator UID and initializes creator as both member and admin.
- **Fault-Tolerant Mutations**: Uses `RequestSyncManager.executeWriteTask` for `createGroup`, `updateGroupDetails`, `joinGroupById`, `addMemberByEmail`, `deleteGroup` (only when admin and sole member), `assignAdmin`, and `leaveGroup` (enforcing `GroupChat.canUserLeave`), guarded by internal `requireSignedIn` and `requireAdmin` helpers.

## Links & Dependencies
- **Exposed via**: [[PayPalsRepository]]
- **Uses**: [[FirebaseProvider]] · [[UserProfileRepository]] · [[GroupChat]] · [[RequestSyncManager]]
- **Consumed by UI**: [[GroupsFragment]] · [[CreateGroupFragment]] · [[GroupChatFragment]] · [[GroupBalanceFragment]] · [[GroupSettingsFragment]] · [[HomeFragment]] · [[MainActivity]]
- **Tested by**: [[ExampleUnitTest]]
