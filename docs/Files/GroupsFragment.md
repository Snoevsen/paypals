---
tags:
  - paypals
  - fragment
  - groups
  - qr
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/GroupsFragment.kt
aliases:
  - GroupsFragment.kt
---

# GroupsFragment

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/GroupsFragment.kt`  
> Displays the user's real-time list of groups, provides a top-right AppBar QR scan icon to join groups, and a FAB to create a new group.

## Responsibilities
- **AppBar QR Scanner Button**: Registers an AndroidX `MenuProvider` showing `@drawable/ic_qr_code` (`SHOW_AS_ACTION_ALWAYS`) on the right of the toolbar.
- **Portrait QR Scanning**: `launchPortraitQrScanner()` launches ZXing `ScanContract` locked to [[PortraitCaptureActivity]], parses the group ID via [[PayPalsRepository#Group Management|extractGroupIdFromQr]], calls `joinGroupById`, and opens [[GroupChatFragment]].
- **Group List & Navigation**: Subscribes to [[PayPalsRepository#Group Management|observeUserGroups]], renders items through [[GroupListAdapter]], handles `+ New group` deep shortcut (`openCreateGroup` argument from [[HomeFragment]]), and opens [[CreateGroupFragment]] via `fabCreateGroup`.

## Links & Dependencies
- **Layout & Navigation**: [[03 - Layouts & Resources#fragment_groups.xml|fragment_groups.xml]] · [[02 - Manifest, Navigation & Menus#nav_graph.xml|nav_graph.xml]]
- **Activity & Screens**: [[PortraitCaptureActivity]] · [[CreateGroupFragment]] · [[GroupChatFragment]] · [[HomeFragment]]
- **Adapters, Data & Helpers**: [[GroupListAdapter]] · [[PayPalsRepository]] · [[GroupRepository]] · [[UiHelpers]]
