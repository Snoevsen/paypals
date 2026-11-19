---
tags:
  - paypals
  - adapter
  - activity
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/adapter/ActivityAdapter.kt
aliases:
  - ActivityAdapter.kt
---

# ActivityAdapter

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/adapter/ActivityAdapter.kt`  
> `ListAdapter` binding `UserActivityItem` records onto `item_activity.xml` cards for [[ActivityFragment]] and [[HomeFragment]].

## Responsibilities
- Uses `simpleDiffCallback { it.id }` from [[UiHelpers]].
- Formats amounts using [[PayPalsRepository#Formatting|formatAmount]] and color-codes amounts & category badges for `EXPENSE_PAID_BY_ME`, `PAID_TO_ME`, and `REMINDER`.

### formatTimestampForDisplay
Exposes `companion object.formatTimestampForDisplay(timestamp)` so card timestamps and search matching in [[ActivityFragment]] share the same human + ISO timestamp formats.

## Links & Dependencies
- **Layout & Model**: [[03 - Layouts & Resources#item_activity.xml|item_activity.xml]] · [[PaymentModels]]
- **Helpers, Data & Consumers**: [[UiHelpers]] · [[PayPalsRepository]] · [[ActivityFragment]] · [[HomeFragment]]
