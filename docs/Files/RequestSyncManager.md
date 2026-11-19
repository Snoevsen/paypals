---
tags:
  - paypals
  - sync
  - offline
  - fault-tolerance
package: com.sdu.paypals.sync
source_path: app/src/main/java/com/sdu/paypals/sync/RequestSyncManager.kt
aliases:
  - RequestSyncManager.kt
---

# RequestSyncManager

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/sync/RequestSyncManager.kt`  
> Singleton **Observer / Retry Queue** implementing PayPals' non-functional requirements: Fault Tolerance, Offline Availability, Background Retries, and User Feedback (`RequestState`, `RequestStatusEvent`).

## Responsibilities
- **Connectivity Monitoring**: `init(context)` registers a `ConnectivityManager.NetworkCallback` updating `isNetworkAvailable`, notifying connectivity listeners ([[MainActivity]]), and invoking `processBackgroundRetries()` whenever connectivity returns.
- **Fault-Tolerant Execution (`executeWriteTask`)**:
  1. Emits `RequestState.PENDING`.
  2. When offline or when a request takes longer than `2500ms`, immediately returns `optimisticResult` so the user is never blocked, queues a `QueuedRequest`, and emits `FAILED_RETRYING_IN_BACKGROUND`.
  3. On transient `UNAVAILABLE` / `DEADLINE_EXCEEDED` Firestore errors, queues the task for retry and does not block UI.
  4. When queued requests succeed later in the background (either via retry task or `FirebaseFirestore.waitForPendingWrites()`), emits `BACKGROUND_RETRY_SUCCEEDED` and triggers a system notification via [[NotificationHelper]].

## Links & Dependencies
- **Notifications & UI Host**: [[NotificationHelper]] · [[MainActivity]]
- **Used by Repositories**: [[UserProfileRepository]] · [[GroupRepository]] · [[PaymentRepository]]
