---
tags:
  - paypals
  - fragment
  - balance
  - payments
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/GroupBalanceFragment.kt
aliases:
  - GroupBalanceFragment.kt
---

# GroupBalanceFragment

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/GroupBalanceFragment.kt`  
> Dedicated balance breakdown screen opened from the interactive card in [[GroupChatFragment]], showing totals owed/owing, member-by-member debtors/creditors, quick Pay/Nudge actions, and every group member's balance status.

## Responsibilities
- **Real-Time Group & Payment Streams**: Observes group metadata and payments via [[PayPalsRepository]] and runs `calculateUserBalance`.
- **Top Balance Overview Card**: Renders `totalOwedToMe`, `totalIOwe`, and formatted summary text using `UserBalanceSummary.formatSummaryText(inGroup = true, ...)` from [[PaymentModels]].
- **Owed From Section**: Lists `owedByMembers` and exposes `btnNudgeUnpaidInBalanceView` to ping unpaid participants across outstanding requests.
- **Owe To Section**: Lists `iOweToMembers` and creates quick-pay buttons for each `unpaidRequestsIOwe`, launching `showPayShareConfirmationDialog` in [[UiHelpers]].
- **Full Group Members Balance List**: Dynamically builds rows via `createMemberRow(...)` showing net or bilateral owed/owing state for every member in the group.

## Links & Dependencies
- **Layout**: [[03 - Layouts & Resources#fragment_group_balance.xml|fragment_group_balance.xml]]
- **Models & Data**: [[GroupChat]] · [[PaymentModels]] · [[PayPalsRepository]] · [[ActivityAndBalanceRepository]]
- **Helpers & Parent Screen**: [[UiHelpers]] · [[GroupChatFragment]] · [[02 - Manifest, Navigation & Menus#nav_graph.xml|nav_graph.xml]]
