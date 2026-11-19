---
tags:
  - paypals
  - fragment
  - payments
  - groups
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/GroupChatFragment.kt
aliases:
  - GroupChatFragment.kt
---

# GroupChatFragment

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/GroupChatFragment.kt`  
> Central screen inside a group displaying the interactive group balance summary card, chronological payment request chat bubbles, "Request" creation dialog, receipt inspection dialog, share payments, and one-tap unpaid-member nudges.

## Responsibilities
- **Real-Time Group & Payments Listeners**: Subscribes to [[PayPalsRepository#Group Management|observeGroup]] (setting toolbar title & showing admin settings gear menu via `MenuProvider`) and [[PayPalsRepository#Payment Requests & Nudges|observeGroupPayments]] (auto-scrolling when new requests arrive).
- **Interactive Group Balance Card**: Computes group balances via `calculateUserBalance`, binds owed/owes amounts with `formatAmount`, and navigates on click to [[GroupBalanceFragment]].
- **Create Payment ("Request") Dialog (`showCreatePaymentDialog`)**:
  - Inflates [[03 - Layouts & Resources#dialog_create_payment.xml|dialog_create_payment.xml]].
  - Supports tap-outside-to-unfocus via `clearFocusAndHideKeyboard` and `enableTapOutsideToUnfocus` in [[UiHelpers]].
  - Computes live equal per-person split previews and lets requester uncheck excluded members (`Members`), creating requests via [[PayPalsRepository#Payment Requests & Nudges|createPaymentRequest]].
- **Receipt Review Dialog (`showReviewReceiptDialog`)**: Inflates [[03 - Layouts & Resources#dialog_review_receipt.xml|dialog_review_receipt.xml]] showing per-participant PAID/OWES statuses, notes, last-nudged timestamps, excluded members, full payment logs, and Pay/Nudge positive actions.
- **Pay Share & Nudge**: Delegates share payments to `showPayShareConfirmationDialog` in [[UiHelpers]], and triggers [[PayPalsRepository#Payment Requests & Nudges|nudgeUnpaidParticipants]] strictly for unpaid members via `nudgeAllUnpaid(payment)`.

## Links & Dependencies
- **Layouts**: [[03 - Layouts & Resources#fragment_group_chat.xml|fragment_group_chat.xml]] · [[03 - Layouts & Resources#dialog_create_payment.xml|dialog_create_payment.xml]] · [[03 - Layouts & Resources#dialog_review_receipt.xml|dialog_review_receipt.xml]]
- **Navigation Destinations**: [[GroupBalanceFragment]] · [[GroupSettingsFragment]] · [[02 - Manifest, Navigation & Menus#nav_graph.xml|nav_graph.xml]]
- **Adapters, Models & Helpers**: [[GroupPaymentAdapter]] · [[GroupChat]] · [[PaymentModels]] · [[PayPalsRepository]] · [[UiHelpers]]
