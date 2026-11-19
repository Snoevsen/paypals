---
tags:
  - paypals
  - adapter
  - payments
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/adapter/GroupPaymentAdapter.kt
aliases:
  - GroupPaymentAdapter.kt
---

# GroupPaymentAdapter

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/adapter/GroupPaymentAdapter.kt`  
> `ListAdapter` rendering `PaymentRequest` items as chat-like cards aligned right (current user paid) or left (another member paid).

## Responsibilities
- Uses `simpleDiffCallback { it.id }` from [[UiHelpers]].
- Dynamically aligns `cardBubble` (`Gravity.END` vs `Gravity.START`), applies container colors, and sets `"You paid"` vs `"<Name> paid"`.
- Formats `totalAmount`, `sharePerPerson`, and user share button labels via [[PayPalsRepository#Formatting|formatAmount]].
- Displays excluded members notice, `progressPaid` completion bar (`paidCount / totalParticipantCount`), recent 3 `paymentLogs`, Receipt button, Pay Share button (for unpaid participants), and one-tap Nudge button.

## Links & Dependencies
- **Layout & Model**: [[03 - Layouts & Resources#item_payment_request.xml|item_payment_request.xml]] · [[PaymentModels]]
- **Helpers, Data & Consumer**: [[UiHelpers]] · [[PayPalsRepository]] · [[GroupChatFragment]]
