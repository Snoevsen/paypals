---
tags:
  - paypals
  - fragment
  - settings
  - qr
  - admin
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/GroupSettingsFragment.kt
aliases:
  - GroupSettingsFragment.kt
---

# GroupSettingsFragment

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/GroupSettingsFragment.kt`  
> Group administration and membership screen providing a scannable group QR code, metadata/icon editor, email-based member additions, admin promotion, and leave/delete governance.

## Responsibilities
- **QR Code Rendering**: Encodes [[PayPalsRepository#Group Management|buildGroupQrPayload]] (`paypals://join?groupId=<id>`) into a `512x512` `Bitmap` via ZXing `QRCodeWriter`.
- **Group Details Editing (Admin Only)**: Uses bidirectional mapping tables (`CHIP_TO_ICON` / `ICON_TO_CHIP`) and `resolveGroupIconKey` from [[UiHelpers]] to sync `GroupIcon` chips and save changes via `updateGroupDetails`.
- **Add Member by Email**: Calls `addMemberByEmail` in [[PayPalsRepository]] (resolving registered users in Firestore by email and preserving their profile display name).
- **Member & Admin List**: Dynamically lists members with `(You)` and `[Admin]` markers and lets admins promote non-admins via `assignAdmin`.
- **Leave / Delete Governance**: Enforces `GroupChat.canUserLeave` in UI state; enables `btnDeleteGroup` only when the current user is the sole remaining admin member.

## Links & Dependencies
- **Layout & Navigation**: [[03 - Layouts & Resources#fragment_group_settings.xml|fragment_group_settings.xml]] · [[02 - Manifest, Navigation & Menus#nav_graph.xml|nav_graph.xml]]
- **Models, Repositories & Helpers**: [[GroupChat]] · [[PayPalsRepository]] · [[GroupRepository]] · [[UiHelpers]]
- **Related Screens**: [[GroupChatFragment]] · [[GroupsFragment]]
