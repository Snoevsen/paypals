---
tags:
  - paypals
  - fragment
  - dashboard
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/HomeFragment.kt
aliases:
  - HomeFragment.kt
---

# HomeFragment

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/HomeFragment.kt`  
> Main dashboard summarizing the user's overall owed/owes balance, top 3 active groups, and latest 5 activities.

## Responsibilities
- **AppBar Greeting**: Displays `Hi, <ProfileName>!` via `updateGreetingInAppBar()` using [[PayPalsRepository#User Identity & Profile|currentUserName]].
- **Real-Time Observers**:
  - `observeTotalUserBalance` -> binds overall summary card (`tvHomeTotalOwedToMe`, `tvHomeTotalIOwe`, `tvHomeBalanceSummary`) using `UserBalanceSummary.formatSummaryText` in [[PaymentModels]] and [[PayPalsRepository#Formatting|formatAmount]].
  - `observeUserGroups` -> displays top 3 groups sorted by `lastActivityAt` inside [[GroupListAdapter]].
  - `observeUserActivityFeed` -> displays top 5 newest activities inside [[ActivityAdapter]].
- **Quick Shortcuts**:
  - `+ New group` routes through [[MainActivity]] (`navigateToBottomBarDestination`) to [[GroupsFragment]] with `openCreateGroup = true` so the create-group action fires automatically.
  - `See all` navigates to [[ActivityFragment]].
  - Clicking a group card or activity card navigates directly into [[GroupChatFragment]] using `openGroupChat` from [[UiHelpers]].

## Links & Dependencies
- **Layout**: [[03 - Layouts & Resources#fragment_home.xml|fragment_home.xml]]
- **Adapters & Models**: [[GroupListAdapter]] · [[ActivityAdapter]] · [[PaymentModels]]
- **Data & Helpers**: [[PayPalsRepository]] · [[ActivityAndBalanceRepository]] · [[UiHelpers]]
- **Screens**: [[MainActivity]] · [[GroupsFragment]] · [[ActivityFragment]] · [[GroupChatFragment]]
