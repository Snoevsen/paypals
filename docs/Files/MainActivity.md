---
tags:
  - paypals
  - activity
  - navigation
  - sync
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/MainActivity.kt
aliases:
  - MainActivity.kt
---

# MainActivity

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/MainActivity.kt`  
> Single-activity host that orchestrates splash-screen initialization, dynamic authentication start destination, bottom navigation synchronization across nested group screens, deep links (`paypals://join?groupId=...`), connectivity/request status banners, real-time incoming nudge listeners, and global tap-outside-to-unfocus behavior.

## Responsibilities
- **Startup & Theme**: Installs SplashScreen via `installSplashScreen()`, enables edge-to-edge display, inflates [[03 - Layouts & Resources#activity_main.xml|activity_main.xml]], initializes [[NotificationHelper]] and [[RequestSyncManager]].
- **Dynamic Start Destination**: Inspects Firebase `currentUser`; sets start destination in [[02 - Manifest, Navigation & Menus#nav_graph.xml|nav_graph.xml]] to [[HomeFragment]] (and calls [[PayPalsRepository#User Identity & Profile|syncCurrentUserProfile]]) or [[LoginFragment]].
- **Bottom Navigation Sync**: `navigateToBottomBarDestination(...)` and `syncBottomNavigationSelection(...)` keep the Groups tab active even when inside nested group destinations ([[GroupChatFragment]], [[GroupBalanceFragment]], [[GroupSettingsFragment]], [[CreateGroupFragment]]).
- **Deep Links & Notifications**: `handleIncomingIntent(...)` routes notification taps (`open_group_id`) directly into [[GroupChatFragment]] and resolves QR deep links via [[PayPalsRepository#Group Management|joinGroupById]].
- **Incoming Nudge Listener**: `ensureNudgeListenerStarted()` requests `POST_NOTIFICATIONS` on Android 13+ and subscribes to [[PayPalsRepository#Payment Requests & Nudges|observeIncomingNudges]], posting alerts via [[NotificationHelper]] and displaying an interactive `Snackbar`.
- **Status & Connectivity Banner**: Listens to [[RequestSyncManager]] events (`PENDING`, `SUCCESS`, `FAILED`, `FAILED_RETRYING_IN_BACKGROUND`, `BACKGROUND_RETRY_SUCCEEDED`) and offline transitions, updating `tvRequestStatusBanner` in [[03 - Layouts & Resources#content_main.xml|content_main.xml]].
- **Global TextField Unfocus**: Delegates `dispatchTouchEvent(...)` to `handleUnfocusOutsideEditText` in [[UiHelpers]].

## Links & Dependencies
- **Layouts / Menus / Manifest**: [[02 - Manifest, Navigation & Menus#AndroidManifest.xml|AndroidManifest.xml]] · [[02 - Manifest, Navigation & Menus#nav_graph.xml|nav_graph.xml]] · [[02 - Manifest, Navigation & Menus#bottom_nav_menu.xml|bottom_nav_menu.xml]] · [[03 - Layouts & Resources#activity_main.xml|activity_main.xml]] · [[03 - Layouts & Resources#content_main.xml|content_main.xml]]
- **App Infrastructure**: [[PayPalsRepository]] · [[RequestSyncManager]] · [[NotificationHelper]] · [[UiHelpers]]
- **Managed Destinations**: [[LoginFragment]] · [[HomeFragment]] · [[GroupsFragment]] · [[CreateGroupFragment]] · [[GroupChatFragment]] · [[GroupBalanceFragment]] · [[GroupSettingsFragment]] · [[ActivityFragment]] · [[ProfileFragment]]
