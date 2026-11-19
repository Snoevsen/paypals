package com.sdu.paypals.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Preserves in-progress "Request" payment dialog inputs, excluded member checkboxes,
 * and "Pay Share" note drafts across configuration changes, calls, and OS process death.
 */
class GroupChatViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    companion object {
        internal const val KEY_CREATE_DIALOG_OPEN = "group_chat_create_dialog_open"
        internal const val KEY_DRAFT_AMOUNT = "group_chat_draft_amount"
        internal const val KEY_DRAFT_DESCRIPTION = "group_chat_draft_description"
        internal const val KEY_EXCLUDED_IDS = "group_chat_excluded_ids"
        internal const val KEY_PAY_SHARE_PAYMENT_ID = "group_chat_pay_share_payment_id"
        internal const val KEY_PAY_SHARE_NOTE = "group_chat_pay_share_note"
    }

    val isCreateDialogOpen: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_CREATE_DIALOG_OPEN, false)
    val draftAmount: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_DRAFT_AMOUNT, "")
    val draftDescription: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_DRAFT_DESCRIPTION, "")
    val excludedMemberIds: StateFlow<ArrayList<String>> =
        savedStateHandle.getStateFlow(KEY_EXCLUDED_IDS, arrayListOf())

    val pendingPaySharePaymentId: StateFlow<String?> =
        savedStateHandle.getStateFlow(KEY_PAY_SHARE_PAYMENT_ID, null)
    val draftPayShareNote: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_PAY_SHARE_NOTE, "")

    fun openCreateDialog() {
        savedStateHandle[KEY_CREATE_DIALOG_OPEN] = true
    }

    fun updateDraftAmount(amount: String) {
        savedStateHandle[KEY_DRAFT_AMOUNT] = amount
    }

    fun updateDraftDescription(description: String) {
        savedStateHandle[KEY_DRAFT_DESCRIPTION] = description
    }

    fun setMemberExcluded(memberUid: String, excluded: Boolean) {
        val updated = ArrayList(excludedMemberIds.value)
        if (excluded) {
            if (!updated.contains(memberUid)) updated.add(memberUid)
        } else {
            updated.remove(memberUid)
        }
        savedStateHandle[KEY_EXCLUDED_IDS] = updated
    }

    fun clearCreateDialogState() {
        savedStateHandle[KEY_CREATE_DIALOG_OPEN] = false
        savedStateHandle[KEY_DRAFT_AMOUNT] = ""
        savedStateHandle[KEY_DRAFT_DESCRIPTION] = ""
        savedStateHandle[KEY_EXCLUDED_IDS] = arrayListOf<String>()
    }

    fun openPayShareDialog(paymentId: String) {
        if (pendingPaySharePaymentId.value != paymentId) {
            savedStateHandle[KEY_PAY_SHARE_NOTE] = ""
        }
        savedStateHandle[KEY_PAY_SHARE_PAYMENT_ID] = paymentId
    }

    fun updatePayShareNote(note: String) {
        savedStateHandle[KEY_PAY_SHARE_NOTE] = note
    }

    fun clearPayShareDialogState() {
        savedStateHandle[KEY_PAY_SHARE_PAYMENT_ID] = null
        savedStateHandle[KEY_PAY_SHARE_NOTE] = ""
    }
}
