---
tags:
  - paypals
  - layouts
  - resources
  - ui
  - themes
aliases:
  - Layouts & Resources
---

# Layouts, Themes, Drawables & XML Resources

> [!note] Navigation
> Back to [[00 - Overview]] | Related: [[02 - Manifest, Navigation & Menus]] Â· [[UiHelpers]]

---

## Screen & Host Layouts (`app/src/main/res/layout/`)

### activity_main.xml
- Root `CoordinatorLayout` for [[MainActivity]] wrapping `AppBarLayout` + `MaterialToolbar` (`@id/toolbar`) and including [[03 - Layouts & Resources#content_main.xml|content_main.xml]].

### content_main.xml
- Included inside [[03 - Layouts & Resources#activity_main.xml|activity_main.xml]]. Contains:
  - `tvRequestStatusBanner` â€” Top connectivity & request-sync status banner controlled by [[MainActivity]] from [[RequestSyncManager]] events.
  - `nav_host_fragment_content_main` â€” `NavHostFragment` rendering destinations from [[02 - Manifest, Navigation & Menus#nav_graph.xml|nav_graph.xml]].
  - `bottom_navigation` â€” Persistent `BottomNavigationView` inflated with [[02 - Manifest, Navigation & Menus#bottom_nav_menu.xml|bottom_nav_menu.xml]].

### fragment_login.xml
- Bound by [[LoginFragment]]. `ScrollView` with branding (`ic_paypals_logo`), Log In / Sign Up `MaterialButtonToggleGroup`, conditional Full Name input (`tilFullName`), Email & Password fields (`password_toggle`), Submit button, ProgressBar, and mode-switch button.

### fragment_home.xml
- Bound by [[HomeFragment]]. Dashboard `NestedScrollView` featuring:
  - Overall balance card (`tvHomeTotalOwedToMe`, `tvHomeTotalIOwe`, `tvHomeBalanceSummary`).
  - "Groups" section header with bold `+ New group` shortcut (`tvHomeNewGroup`) and top-3 `rvHomeGroups` `RecyclerView`.
  - "Latest Activities" header with bold `See all` shortcut (`tvHomeSeeAllActivities`) and card-wrapped 5-item `rvHomeActivities` `RecyclerView`.

### fragment_groups.xml
- Bound by [[GroupsFragment]]. Displays `rvGroups`, empty-state onboarding prompt referencing the top-right QR icon and `+` button, loading `progressBar`, and `fabCreateGroup` `FloatingActionButton`.

### fragment_create_group.xml
- Bound by [[CreateGroupFragment]]. Form with Group Name (`maxLength=50`), Description (`maxLength=140`), single-select `ChipGroup` for icon selection (`Group`, `Household`, `Trip`, `Dining`, `Shopping`, `Event` matching [[GroupChat#GroupIcon]]), progress indicator, and `btnCreateGroupSubmit`.

### fragment_group_chat.xml
- Bound by [[GroupChatFragment]]. Displays:
  - Interactive top `cardGroupBalance` showing `tvGroupOwedToMe`, `tvGroupIOwe`, and `tvGroupBalanceHint` (navigates to [[GroupBalanceFragment]]).
  - Chronological `rvPayments` stream and `layoutEmptyPayments` empty state.
  - Extended FAB `fabIPaid` ("I Paid") opening the Request dialog.

### fragment_group_balance.xml
- Bound by [[GroupBalanceFragment]]. Detailed group balance screen containing:
  - Top overview card (`tvBalanceGroupTitle`, `tvViewOwedToMe`, `tvViewIOwe`, `tvNetBalanceText`).
  - "Owed From (Members Who Owe You)" card with `llOwedFromMembers` and `btnNudgeUnpaidInBalanceView`.
  - "Owe To (Members You Owe)" card with `llIOweToMembers`.
  - "Group Members Balance List" card (`llAllGroupMembersBalances`).

### fragment_group_settings.xml
- Bound by [[GroupSettingsFragment]]. Four Material cards:
  - Join via QR Code (`ivGroupQrCode`).
  - Edit Group Info (name, description, `chipGroupEditIcons`, `btnSaveGroupDetails` â€” admin enabled).
  - Add Member by Email (`etAddMemberEmail`, `btnAddMember`).
  - Members & Admin Rights (`llMembersList`) + Leave/Delete actions (`btnLeaveGroup`, `btnDeleteGroup`).

### fragment_activity.xml
- Bound by [[ActivityFragment]]. Features search `TextInputLayout` (`etSearchActivity`), filter row (`btnFilterAll`, `btnFilterExpenses`, `btnFilterReminders`), summary + sort/limit controls (`tvActiveFilterSummary`, `btnSortOrder`, `btnCardLimit`), `rvActivities`, and empty state.

### fragment_profile.xml
- Bound by [[ProfileFragment]]. Profile header (`iv`/`tvFullName`/`tvEmail`), `Edit Profile` card (`etEditFullName`, `etEditEmail`, `etNewPassword`, `etCurrentPassword`, `btnSaveProfile`), and `btnSignOut` button.

---

## Dialog & List Item Layouts (`app/src/main/res/layout/`)

### dialog_create_payment.xml
- Inflated by [[GroupChatFragment]] ("Request" dialog). Contains fixed-dollar `tilPaymentAmount` (`app:prefixText="$"`), `tilPaymentDescription` (max 100 chars), "Members" checkbox container (`llMemberCheckboxes`), and live equal-split preview `tvSplitSummary`.

### dialog_pay_share.xml
- Inflated by `showPayShareConfirmationDialog` in [[UiHelpers]] (used in [[GroupChatFragment]] & [[GroupBalanceFragment]]). Shows `tvPaySharePrompt` and optional `etPayShareNote` (`maxLength=100`).

### dialog_review_receipt.xml
- Inflated by `showReviewReceiptDialog` in [[GroupChatFragment]]. Displays receipt summary card, `llParticipantsContainer` per-member payment status rows, optional `tvExcludedHeader`/`tvExcludedMembersList`, and chronological `tvFullPaymentLogs`.

### item_group.xml
- Bound by [[GroupListAdapter]]. Group card showing icon badge (`cardIconContainer`/`ivGroupIcon`), group name, `Admin` pill (`tvAdminBadge`), description, member count summary, and last activity snippet.

### item_payment_request.xml
- Bound by [[GroupPaymentAdapter]]. Chat-style `cardBubble` card with requester header, timestamp, `tvTotalAmount`, `tvShareAmount`, `tvPaymentDescription`, `tvExcludedNotice`, horizontal progress bar (`progressPaid` + `tvPaidStatusSummary`), `tvPaymentLogPreview`, and action buttons (`btnReviewReceipt`, `btnNudgeUnpaid`, `btnPayMyShare`).

### item_activity.xml
- Bound by [[ActivityAdapter]]. Card displaying `tvActivityPayer`, `tvActivityAmount`, `tvActivityDescription`, category badge (`tvActivityCategoryBadge`), and formatted timestamp (`tvActivityTimestamp`).

---

## Values, Themes, Dimensions & XML Config (`app/src/main/res/`)

### values/strings.xml
- Holds app name (`PayPals`), action labels, bottom navigation titles, login/signup form hints and error messages, and screen placeholder descriptions.

### values/colors.xml
- Defines `#FF000000` (`black`), `#FFFFFFFF` (`white`), and `#FF4C7BB3` (`splash_background` used by launch screen & adaptive launcher background).

### values/themes.xml, values-night/themes.xml & values-v23/themes.xml
- `values/themes.xml` & `values-night/themes.xml`: Define Material 3 DayNight base (`Theme.PayPals`) and Android 12+ SplashScreen theme `Theme.PayPals.Starting` (`windowSplashScreenBackground=@color/splash_background`, `windowSplashScreenAnimatedIcon=@drawable/ic_splash_logo`, `postSplashScreenTheme=@style/Theme.PayPals`).
- `values-v23/themes.xml`: Configures transparent navigation/status bars and `windowLightStatusBar` for edge-to-edge rendering in [[MainActivity]].

### values/dimens.xml, values-land/dimens.xml, values-w600dp/dimens.xml & values-w1240dp/dimens.xml
- Responsive dimension overrides defining `fab_margin` (`16dp` default, `48dp` landscape / `w600dp`, `200dp` `w1240dp`).

### xml/backup_rules.xml & xml/data_extraction_rules.xml
- Standard Android Auto Backup and cloud/device-transfer exclusion template XMLs referenced from [[02 - Manifest, Navigation & Menus#AndroidManifest.xml|AndroidManifest.xml]].

---

## Drawables & Launcher / Splash Icons

- **Vector UI Icons (`res/drawable/ic_*.xml`)**: `ic_activity`, `ic_add`, `ic_check_circle`, `ic_delete`, `ic_group_celebration`, `ic_group_food`, `ic_group_shopping`, `ic_group_trip`, `ic_groups`, `ic_home`, `ic_logout`, `ic_nudge`, `ic_pending`, `ic_profile`, `ic_qr_code`, `ic_receipt`, `ic_settings`, plus vector `ic_launcher_background.xml` (`#4C7BB3`).
- **Density-Bucket PNGs (`drawable-mdpi` .. `drawable-xxxhdpi`)**: `ic_launcher_foreground.png`, `ic_paypals_logo.png` (used in [[03 - Layouts & Resources#fragment_login.xml|fragment_login.xml]]), and `ic_splash_logo.png` (used in `Theme.PayPals.Starting`).
- **Adaptive & Legacy Mipmaps (`mipmap-anydpi-v26/` & `mipmap-*dpi/`)**: `ic_launcher.xml` and `ic_launcher_round.xml` combine `ic_launcher_background` and `ic_launcher_foreground` (plus monochrome layer) alongside PNG fallbacks for pre-API 26 launchers.
