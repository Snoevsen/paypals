---
tags:
  - paypals
  - repository
  - payments
  - nudges
package: com.sdu.paypals.repository
source_path: app/src/main/java/com/sdu/paypals/repository/PaymentRepository.kt
aliases:
  - PaymentRepository.kt
---

# PaymentRepository

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/repository/PaymentRepository.kt`  
> Internal repository managing payment request creation, equal bill splitting, marking shares paid, triggering unpaid-member nudges, and listening for incoming nudge notifications.

## Responsibilities
- **Real-Time Group Payments**: `observeGroupPayments(...)` uses `observeQuery` from [[FirebaseProvider]] sorted chronologically by `createdAt`.
- **Payment Building**: `buildPaymentRequest(...)` validates positive amount, trimmed description, and non-empty included member list; calculates rounded equal `sharePerPerson`; sets requester share paid immediately; builds initial `PaymentLogEntry` formatted via `PayPalsRepository.formatAmount`.
- **Transactional Writes via Fault-Tolerant Queue**:
  - `createPaymentRequest(...)`: Batch-writes payment document and updates parent group's `lastActivityText` / `lastActivityAt` via [[RequestSyncManager]].
  - `markShareAsPaid(...)`: Updates current user's `ParticipantStatus` + optional note and appends audit log entry.
  - `nudgeUnpaidParticipants(...)`: Filters strictly to unpaid non-self recipients, updates `lastNudgedAt`, logs the nudge, and creates `NudgeNotification` documents in `notifications`.
- **Incoming Nudge Observer**: `observeIncomingNudges(...)` combines a direct query on `notifications` with `observeUserGroupPayments` in [[FirebaseProvider]], deduplicating events before delivering notifications to [[MainActivity]].

## Links & Dependencies
- **Exposed via**: [[PayPalsRepository]]
- **Uses**: [[FirebaseProvider]] · [[UserProfileRepository]] · [[GroupChat]] · [[PaymentModels]] · [[RequestSyncManager]]
- **Consumed by UI & Tests**: [[GroupChatFragment]] · [[GroupBalanceFragment]] · [[MainActivity]] · [[ExampleUnitTest]]
