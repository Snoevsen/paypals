---
tags:
  - paypals
  - model
  - firestore
  - payments
  - balance
package: com.sdu.paypals.model
source_path: app/src/main/java/com/sdu/paypals/model/PaymentModels.kt
aliases:
  - PaymentModels.kt
  - PaymentRequest
  - ParticipantStatus
  - NudgeNotification
  - UserActivityItem
  - UserBalanceSummary
---

# PaymentModels

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/model/PaymentModels.kt`  
> Contains immutable domain models for group payment requests, participant statuses, audit logs, push/local nudges, unified activity cards, and member/overall balance summaries.

## Defined Models
- **`ParticipantStatus`**: Per-member share (`uid`, `displayName`, `amountOwed`, `paid`, `paidAt`, `paymentNote`, `lastNudgedAt`) + `toMap()` / `fromMap()`.
- **`PaymentLogEntry`**: Chronological log entry (`actorUid`, `actorName`, `message`, `timestamp`) + `toMap()` / `fromMap()`.
- **`PaymentRequest`**: Firestore group subcollection model (`groups/{groupId}/payments/{id}`) with helper methods `paidCount()`, `totalParticipantCount()`, `isFullyPaid()`, and `toMap()` / `fromDocument()`.
- **`NudgeNotification`**: Delivery document in top-level `notifications` collection (`recipientId`, `senderId`, `senderName`, `groupId`, `groupName`, `paymentId`, `paymentDescription`, `amountOwed`, `createdAt`, `delivered`) + `toMap()` / `fromDocument()`.
- **`ActivityCategory` & `UserActivityItem`**: Unified activity-card model categorizing items into `EXPENSE_PAID_BY_ME`, `PAID_TO_ME`, and `REMINDER`.
- **`MemberDebtItem` & `UserBalanceSummary`**: Aggregated balance breakdown (`totalOwedToMe`, `totalIOwe`, `owedByMembers`, `iOweToMembers`, `unpaidRequestsIOwe`, `unpaidRequestsOwedToMe`, computed `netBalance`), plus `formatSummaryText(inGroup, formatter)` shared between [[HomeFragment]] and [[GroupBalanceFragment]].

## Links & Dependencies
- **Used by Repositories**: [[PaymentRepository]] · [[ActivityAndBalanceRepository]] · [[FirebaseProvider]] · [[PayPalsRepository]]
- **Used by UI, Adapters & Utils**: [[GroupPaymentAdapter]] · [[ActivityAdapter]] · [[HomeFragment]] · [[GroupChatFragment]] · [[GroupBalanceFragment]] · [[ActivityFragment]] · [[NotificationHelper]] · [[UiHelpers]]
- **Tested by**: [[ExampleUnitTest]]
