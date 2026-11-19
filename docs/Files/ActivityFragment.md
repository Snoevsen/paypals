---
tags:
  - paypals
  - fragment
  - activity
  - pattern-strategy
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/ActivityFragment.kt
aliases:
  - ActivityFragment.kt
---

# ActivityFragment

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/ActivityFragment.kt`  
> Displays the user's chronological cross-group activity feed (paid bills/requests, money received, reminders/nudges) with live search, category filters, sorting, and card count controls (`10` vs `25`).

## Design Pattern: Strategy Pattern
- **`FilterMode` Enum**: Encapsulates both `summaryTitle` and the `predicate: (UserActivityItem) -> Boolean` directly on each enum constant (`ALL_ACTIVITIES`, `EXPENSES_PAID_BY_ME`, `PAID_TO_ME`, `REMINDERS`).
- **`SortMode` Enum**: Encapsulates `label`, `comparator: Comparator<UserActivityItem>`, and `next()` cycling (`NEWEST_FIRST`, `OLDEST_FIRST`, `AMOUNT_DESC`).

## Responsibilities
- Subscribes to [[PayPalsRepository#Activity Feed & Balance|observeUserActivityFeed]].
- Live text search (`etSearchActivity`) matches payer name, description, group name, formatted timestamps ([[ActivityAdapter#formatTimestampForDisplay|formatTimestampForDisplay]]), and dollar amounts (`formatAmount` & plain decimal).
- **Expenses Button Toggle**: Clicking Expenses selects `EXPENSES_PAID_BY_ME` ("Expenses (You Paid)"); clicking it while active toggles to `PAID_TO_ME` ("Paid to You").
- `companion object.filterAndSortActivities(...)`: Pure testable pipeline applying category filter, search query, sort comparator, and `limit.coerceIn(10, 25)` — verified in [[ExampleUnitTest]].
- Clicking an activity card navigates directly into the associated [[GroupChatFragment]] via `openGroupChat` in [[UiHelpers]].

## Links & Dependencies
- **Layout**: [[03 - Layouts & Resources#fragment_activity.xml|fragment_activity.xml]]
- **Adapter & Models**: [[ActivityAdapter]] · [[PaymentModels]]
- **Data, Helpers & Tests**: [[PayPalsRepository]] · [[ActivityAndBalanceRepository]] · [[UiHelpers]] · [[ExampleUnitTest]]
- **Screens**: [[HomeFragment]] · [[GroupChatFragment]]
