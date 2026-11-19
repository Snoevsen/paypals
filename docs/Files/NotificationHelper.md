---
tags:
  - paypals
  - util
  - notifications
package: com.sdu.paypals.util
source_path: app/src/main/java/com/sdu/paypals/util/NotificationHelper.kt
aliases:
  - NotificationHelper.kt
---

# NotificationHelper

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/util/NotificationHelper.kt`  
> Singleton managing the Android notification channel and posting high-priority system notifications for incoming payment nudges and completed background retries.

## Responsibilities
- `createNotificationChannel(context)` creates `paypals_nudge_channel` on Android O+.
- `showNudgeNotification(context, nudge)`: Checks `POST_NOTIFICATIONS` permission on Android 13+, formats `nudge.amountOwed` with [[PayPalsRepository#Formatting|formatAmount]], and creates a `PendingIntent` launching [[MainActivity]] straight into `nudge.groupId`.
- `showBackgroundSyncNotification(context, actionLabel)`: Alerts the user when a previously queued offline/failed request finishes syncing in [[RequestSyncManager]].

## Links & Dependencies
- **Models & Data**: [[PaymentModels]] · [[PayPalsRepository]]
- **Invoked by**: [[MainActivity]] · [[RequestSyncManager]]
