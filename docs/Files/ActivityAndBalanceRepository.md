---
tags:
  - paypals
  - repository
  - activity
  - balance
package: com.sdu.paypals.repository
source_path: app/src/main/java/com/sdu/paypals/repository/ActivityAndBalanceRepository.kt
aliases:
  - ActivityAndBalanceRepository.kt
---

# ActivityAndBalanceRepository

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/repository/ActivityAndBalanceRepository.kt`  
> Internal repository aggregating cross-group payment activity feeds and computing per-group and global owed/owes balance summaries.

## Responsibilities
- **Activity Feed (`observeUserActivityFeed` + `buildUserActivityItems`)**:
  - Uses `observeUserGroupPayments` and `observeQuery` on `notifications` from [[FirebaseProvider]].
  - Maps payment requests and nudges into typed `UserActivityItem` items (`EXPENSE_PAID_BY_ME`, `PAID_TO_ME`, `REMINDER`).
- **Balance Engine (`calculateUserBalance`)**:
  - Computes `totalOwedToMe`, `totalIOwe`, per-member maps (`owedByMembers`, `iOweToMembers`), and unpaid request lists (`unpaidRequestsIOwe`, `unpaidRequestsOwedToMe`) packaged in `UserBalanceSummary`.
- **Global Balance Stream (`observeTotalUserBalance`)**:
  - Wraps `FirebaseProvider.observeUserGroupPayments` and recomputes `calculateUserBalance` across all joined groups.

## Links & Dependencies
- **Exposed via**: [[PayPalsRepository]]
- **Uses**: [[FirebaseProvider]] · [[PaymentModels]]
- **Consumed by**: [[HomeFragment]] · [[ActivityFragment]] · [[GroupChatFragment]] · [[GroupBalanceFragment]] · [[ExampleUnitTest]]
