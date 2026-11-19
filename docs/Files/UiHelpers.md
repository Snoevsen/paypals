---
tags:
  - paypals
  - util
  - ui
  - pattern-factory
  - pattern-decorator
package: com.sdu.paypals.util
source_path: app/src/main/java/com/sdu/paypals/util/UiHelpers.kt
aliases:
  - UiHelpers.kt
---

# UiHelpers

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/util/UiHelpers.kt`  
> Shared UI extension, Decorator, and Factory helpers consolidating repeated RecyclerView diffing, dialog focus handling, share-payment confirmation dialogs, navigation, icon chip resolution, and Snackbars.

## Functions & Patterns
- **`simpleDiffCallback(idSelector)` (Factory)**: Generates `DiffUtil.ItemCallback<T>` for immutable data classes across [[GroupListAdapter]], [[GroupPaymentAdapter]], and [[ActivityAdapter]].
- **`clearFocusAndHideKeyboard()` & `handleUnfocusOutsideEditText(event, focusedView)`**: Clears focus and closes the IME when tapping outside an `EditText` (shared with [[MainActivity]]).
- **`AlertDialog.enableTapOutsideToUnfocus()` (Decorator)**: Wraps `Window.Callback` on any `AlertDialog` so users can dismiss keyboards before hitting dialog actions.
- **`showPayShareConfirmationDialog(payment, rootViewProvider)` (Dialog Factory)**: Shared "Confirm Payment" dialog inflating [[03 - Layouts & Resources#dialog_pay_share.xml|dialog_pay_share.xml]] and invoking [[PayPalsRepository#Payment Requests & Nudges|markShareAsPaid]], reused by [[GroupChatFragment]] and [[GroupBalanceFragment]].
- **`openGroupChat(...)`, `showSnackbar(...)`, `showErrorSnackbar(...)`, `resolveGroupIconKey(...)`**: Shared Fragment navigation, feedback, and chip-to-[[GroupChat#GroupIcon|GroupIcon]] mapping helpers.

## Links & Dependencies
- **Layouts & Models**: [[03 - Layouts & Resources#dialog_pay_share.xml|dialog_pay_share.xml]] · [[GroupChat]] · [[PaymentModels]] · [[PayPalsRepository]]
- **Consumers**: [[MainActivity]] · [[HomeFragment]] · [[GroupsFragment]] · [[CreateGroupFragment]] · [[GroupChatFragment]] · [[GroupBalanceFragment]] · [[GroupSettingsFragment]] · [[ActivityFragment]] · [[GroupListAdapter]] · [[GroupPaymentAdapter]] · [[ActivityAdapter]]
