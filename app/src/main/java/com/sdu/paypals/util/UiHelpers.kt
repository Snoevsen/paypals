package com.sdu.paypals.util

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sdu.paypals.R
import com.sdu.paypals.databinding.DialogPayShareBinding
import com.sdu.paypals.model.GroupIcon
import com.sdu.paypals.model.PaymentRequest
import com.sdu.paypals.repository.PayPalsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Factory helper creating a [DiffUtil.ItemCallback] for immutable data classes with a stable ID.
 */
inline fun <T : Any> simpleDiffCallback(
    crossinline idSelector: (T) -> Any?
): DiffUtil.ItemCallback<T> = object : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean =
        idSelector(oldItem) == idSelector(newItem)

    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean =
        oldItem == newItem
}

/**
 * Clears focus from this view (or its focused descendant) and hides the soft keyboard.
 */
fun View.clearFocusAndHideKeyboard() {
    val target = findFocus() ?: this
    target.clearFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(target.windowToken, 0)
}

/**
 * Unfocuses [focusedView] and hides the soft keyboard if a DOWN motion [event] lands outside it.
 */
fun handleUnfocusOutsideEditText(event: MotionEvent, focusedView: View?) {
    if (event.action != MotionEvent.ACTION_DOWN || focusedView !is EditText) return
    val loc = IntArray(2)
    focusedView.getLocationOnScreen(loc)
    val bounds = Rect(loc[0], loc[1], loc[0] + focusedView.width, loc[1] + focusedView.height)
    if (!bounds.contains(event.rawX.toInt(), event.rawY.toInt())) {
        focusedView.clearFocusAndHideKeyboard()
    }
}

/**
 * Window.Callback Decorator enabling tap-outside-to-unfocus for any [AlertDialog].
 */
fun AlertDialog.enableTapOutsideToUnfocus() {
    val dialogWindow = window ?: return
    val originalCallback = dialogWindow.callback ?: return
    dialogWindow.callback = object : Window.Callback by originalCallback {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            handleUnfocusOutsideEditText(event, currentFocus)
            return originalCallback.dispatchTouchEvent(event)
        }
    }
}

fun Fragment.openGroupChat(
    groupId: String,
    @IdRes actionId: Int = R.id.groupChatFragment,
    navOptions: NavOptions? = null
) {
    val args = Bundle().apply { putString("groupId", groupId) }
    findNavController().navigate(actionId, args, navOptions)
}

fun Fragment.showSnackbar(
    rootView: View,
    message: String,
    duration: Int = Snackbar.LENGTH_SHORT
) {
    if (isAdded) {
        Snackbar.make(rootView, message, duration).show()
    }
}

fun Fragment.showErrorSnackbar(
    rootView: View,
    error: Throwable,
    fallbackMessage: String
) {
    showSnackbar(rootView, error.localizedMessage ?: fallbackMessage, Snackbar.LENGTH_LONG)
}

/**
 * Collects a [Flow] cleanly while the lifecycle is at least [Lifecycle.State.STARTED].
 */
fun <T> LifecycleOwner.collectOnStarted(flow: Flow<T>, collector: (T) -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { collector(it) }
        }
    }
}

/**
 * Updates this [EditText] only when the incoming state differs, preserving cursor position.
 */
fun EditText.setTextIfDifferent(value: String) {
    if (text?.toString() != value) {
        setText(value)
        setSelection(value.length)
    }
}

/**
 * Two-way binds an [EditText] to a `SavedStateHandle`-backed [StateFlow] without feedback loops.
 */
fun EditText.bindSavedText(
    lifecycleOwner: LifecycleOwner,
    stateFlow: StateFlow<String>,
    onUserInput: (String) -> Unit
) {
    var restoring = false
    restoring = true
    setTextIfDifferent(stateFlow.value)
    restoring = false

    lifecycleOwner.collectOnStarted(stateFlow) { value ->
        if (text?.toString() != value) {
            restoring = true
            setTextIfDifferent(value)
            restoring = false
        }
    }

    doAfterTextChanged { editable ->
        if (!restoring) {
            onUserInput(editable?.toString().orEmpty())
        }
    }
}

/**
 * Resolves a selected Material Chip ID using a declarative [chipToIcon] mapping table.
 */
fun resolveGroupIconKey(checkedChipId: Int, chipToIcon: Map<Int, GroupIcon>): String =
    (chipToIcon[checkedChipId] ?: GroupIcon.GROUPS).key

/**
 * Shared confirmation dialog factory for paying a member's share of a [PaymentRequest],
 * optionally restoring/persisting the typed payment note across lifecycle interruptions.
 */
fun Fragment.showPayShareConfirmationDialog(
    payment: PaymentRequest,
    initialNote: String = "",
    onNoteChanged: (String) -> Unit = {},
    onDialogFinished: () -> Unit = {},
    rootViewProvider: () -> View?
): AlertDialog? {
    val uid = PayPalsRepository.currentUserId() ?: return null
    val myStatus = payment.participantStatuses[uid] ?: return null

    val dialogBinding = DialogPayShareBinding.inflate(layoutInflater)
    dialogBinding.tvPaySharePrompt.text =
        "Pay ${PayPalsRepository.formatAmount(myStatus.amountOwed)} to ${payment.requesterName} for \"${payment.description}\""

    dialogBinding.etPayShareNote.setTextIfDifferent(initialNote)
    dialogBinding.etPayShareNote.doAfterTextChanged { editable ->
        onNoteChanged(editable?.toString().orEmpty())
    }

    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle("Confirm Payment")
        .setView(dialogBinding.root)
        .setPositiveButton("Mark as Paid") { _, _ ->
            val note = dialogBinding.etPayShareNote.text?.toString()?.trim().orEmpty()
            onDialogFinished()
            PayPalsRepository.markShareAsPaid(payment, note) { result ->
                val root = rootViewProvider() ?: return@markShareAsPaid
                result.onSuccess {
                    showSnackbar(root, "Marked your share as paid!")
                }.onFailure { e ->
                    showErrorSnackbar(root, e, "Could not mark payment")
                }
            }
        }
        .setNegativeButton("Cancel") { _, _ ->
            onDialogFinished()
        }
        .create()

    dialog.setOnCancelListener {
        onDialogFinished()
    }
    dialog.setOnShowListener { dialog.enableTapOutsideToUnfocus() }
    dialog.show()
    return dialog
}
