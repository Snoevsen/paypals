package com.sdu.paypals

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import com.sdu.paypals.adapter.GroupPaymentAdapter
import com.sdu.paypals.databinding.DialogCreatePaymentBinding
import com.sdu.paypals.databinding.DialogReviewReceiptBinding
import com.sdu.paypals.databinding.FragmentGroupChatBinding
import com.sdu.paypals.model.GroupChat
import com.sdu.paypals.model.PaymentRequest
import com.sdu.paypals.model.UserBalanceSummary
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.util.clearFocusAndHideKeyboard
import com.sdu.paypals.util.enableTapOutsideToUnfocus
import com.sdu.paypals.util.setTextIfDifferent
import com.sdu.paypals.util.showErrorSnackbar
import com.sdu.paypals.util.showPayShareConfirmationDialog
import com.sdu.paypals.util.showSnackbar
import com.sdu.paypals.viewmodel.GroupChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupChatFragment : Fragment() {

    private var _binding: FragmentGroupChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GroupChatViewModel by viewModels()

    private var groupId: String = ""
    private var currentGroup: GroupChat? = null
    private var currentGroupBalance: UserBalanceSummary = UserBalanceSummary()
    private var groupListener: ListenerRegistration? = null
    private var paymentsListener: ListenerRegistration? = null
    private var activeCreateDialog: AlertDialog? = null
    private var activePayShareDialog: AlertDialog? = null

    private lateinit var paymentAdapter: GroupPaymentAdapter

    companion object {
        private const val MENU_ITEM_ADMIN_SETTINGS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getString("groupId").orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (groupId.isBlank()) {
            findNavController().popBackStack()
            return
        }

        setupToolbarMenu()
        setupRecyclerView()
        setupListeners()
        startRealtimeListeners()
    }

    private fun setupToolbarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                val uid = PayPalsRepository.currentUserId()
                val isAdmin = uid != null && currentGroup?.isAdmin(uid) == true
                if (isAdmin) {
                    val item = menu.add(Menu.NONE, MENU_ITEM_ADMIN_SETTINGS, Menu.NONE, "Group Settings")
                    item.setIcon(R.drawable.ic_settings)
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return if (menuItem.itemId == MENU_ITEM_ADMIN_SETTINGS) {
                    openGroupSettings()
                    true
                } else {
                    false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        paymentAdapter = GroupPaymentAdapter(
            currentUserId = { PayPalsRepository.currentUserId() },
            onReviewReceiptClick = { payment -> showReviewReceiptDialog(payment) },
            onPayShareClick = { payment -> openPayShareDialogWithState(payment) },
            onNudgeClick = { payment -> nudgeAllUnpaid(payment) }
        )

        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = false
        }
        binding.rvPayments.layoutManager = layoutManager
        binding.rvPayments.adapter = paymentAdapter
    }

    private fun setupListeners() {
        binding.fabIPaid.setOnClickListener {
            currentGroup?.let { group ->
                showCreatePaymentDialog(group)
            }
        }

        binding.cardGroupBalance.setOnClickListener {
            val args = Bundle().apply { putString("groupId", groupId) }
            findNavController().navigate(R.id.action_groupChatFragment_to_groupBalanceFragment, args)
        }
    }

    private fun openGroupSettings() {
        val group = currentGroup ?: return
        val uid = PayPalsRepository.currentUserId() ?: return
        if (!group.isAdmin(uid)) return

        val args = Bundle().apply { putString("groupId", group.id) }
        findNavController().navigate(R.id.action_groupChatFragment_to_groupSettingsFragment, args)
    }

    private fun startRealtimeListeners() {
        groupListener?.remove()
        groupListener = PayPalsRepository.observeGroup(
            groupId = groupId,
            onUpdate = { group ->
                if (_binding == null) return@observeGroup
                if (group == null) {
                    findNavController().popBackStack()
                    return@observeGroup
                }
                currentGroup = group
                bindGroupHeader(group)
                requireActivity().invalidateOptionsMenu()
                if (viewModel.isCreateDialogOpen.value && activeCreateDialog == null) {
                    showCreatePaymentDialog(group)
                }
            },
            onError = { e ->
                _binding?.let { showErrorSnackbar(it.root, e, "Failed to load group") }
            }
        )

        paymentsListener?.remove()
        paymentsListener = PayPalsRepository.observeGroupPayments(
            groupId = groupId,
            onUpdate = { payments ->
                if (_binding == null) return@observeGroupPayments
                updateGroupBalanceCard(payments)
                val previousSize = paymentAdapter.itemCount
                paymentAdapter.submitList(payments) {
                    if (_binding != null && payments.size > previousSize && payments.isNotEmpty()) {
                        binding.rvPayments.smoothScrollToPosition(payments.size - 1)
                    }
                }
                binding.layoutEmptyPayments.visibility =
                    if (payments.isEmpty()) View.VISIBLE else View.GONE

                val pendingPayId = viewModel.pendingPaySharePaymentId.value
                if (pendingPayId != null && activePayShareDialog == null) {
                    payments.firstOrNull { it.id == pendingPayId }?.let { openPayShareDialogWithState(it) }
                }
            },
            onError = { e ->
                _binding?.let { showErrorSnackbar(it.root, e, "Failed to load payments") }
            }
        )
    }

    private fun updateGroupBalanceCard(payments: List<PaymentRequest>) {
        val uid = PayPalsRepository.currentUserId() ?: return
        val balance = PayPalsRepository.calculateUserBalance(uid, payments)
        currentGroupBalance = balance

        binding.tvGroupOwedToMe.text = PayPalsRepository.formatAmount(balance.totalOwedToMe)
        binding.tvGroupIOwe.text = PayPalsRepository.formatAmount(balance.totalIOwe)

        val owedCount = balance.owedByMembers.size
        val iOweCount = balance.iOweToMembers.size
        binding.tvGroupBalanceHint.text = when {
            owedCount > 0 && iOweCount > 0 ->
                "$owedCount member(s) owe you • You owe $iOweCount member(s) — Tap for details ›"
            owedCount > 0 ->
                "$owedCount member(s) owe you — Tap to view who owes you ›"
            iOweCount > 0 ->
                "You owe $iOweCount member(s) — Tap to view who you owe ›"
            else ->
                "All settled up in this group! Tap for member balances ›"
        }
    }

    private fun bindGroupHeader(group: GroupChat) {
        (activity as? AppCompatActivity)?.supportActionBar?.title = group.name
    }

    /**
     * Dialog for creating a new payment request ("Request") and optionally excluding members,
     * preserving open state and draft inputs via [GroupChatViewModel] (`SavedStateHandle`).
     */
    private fun showCreatePaymentDialog(group: GroupChat) {
        if (activeCreateDialog?.isShowing == true) return
        viewModel.openCreateDialog()

        val dialogBinding = DialogCreatePaymentBinding.inflate(layoutInflater)

        val unfocusClickListener = View.OnClickListener {
            dialogBinding.root.clearFocusAndHideKeyboard()
        }
        listOf(
            dialogBinding.root,
            dialogBinding.llDialogContent,
            dialogBinding.tvMembersHeader,
            dialogBinding.tvMembersSubtext,
            dialogBinding.tvSplitSummary
        ).forEach { it.setOnClickListener(unfocusClickListener) }

        val checkBoxes = mutableMapOf<String, CheckBox>()
        val membersList = group.memberIds.mapNotNull { id -> group.members[id] }
        val restoredExcluded = viewModel.excludedMemberIds.value.toSet()

        fun updateSplitPreview() {
            val enteredAmount = dialogBinding.etPaymentAmount.text?.toString()?.toDoubleOrNull() ?: 0.0
            val includedCount = checkBoxes.values.count { it.isChecked }
            if (includedCount == 0) {
                dialogBinding.tvSplitSummary.text = "Select at least 1 member to include in the request"
            } else {
                val perPerson = if (enteredAmount > 0) enteredAmount / includedCount else 0.0
                val excludedCount = membersList.size - includedCount
                val exclText = if (excludedCount > 0) " ($excludedCount excluded)" else ""
                dialogBinding.tvSplitSummary.text =
                    "Included: $includedCount of ${membersList.size} members$exclText • ${
                        PayPalsRepository.formatAmount(perPerson)
                    } each"
            }
        }

        val currentUid = PayPalsRepository.currentUserId()
        for (member in membersList) {
            val cb = CheckBox(requireContext()).apply {
                val youSuffix = if (member.uid == currentUid) " (You)" else ""
                text = "${member.displayName}$youSuffix"
                isChecked = !restoredExcluded.contains(member.uid)
                setOnCheckedChangeListener { _, checked ->
                    dialogBinding.root.clearFocusAndHideKeyboard()
                    viewModel.setMemberExcluded(member.uid, !checked)
                    updateSplitPreview()
                }
            }
            checkBoxes[member.uid] = cb
            dialogBinding.llMemberCheckboxes.addView(cb)
        }

        dialogBinding.etPaymentAmount.setTextIfDifferent(viewModel.draftAmount.value)
        dialogBinding.etPaymentDescription.setTextIfDifferent(viewModel.draftDescription.value)

        dialogBinding.etPaymentAmount.doAfterTextChanged { text ->
            viewModel.updateDraftAmount(text?.toString().orEmpty())
            updateSplitPreview()
        }
        dialogBinding.etPaymentDescription.doAfterTextChanged { text ->
            viewModel.updateDraftDescription(text?.toString().orEmpty())
        }

        updateSplitPreview()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Request")
            .setView(dialogBinding.root)
            .setPositiveButton("Request", null)
            .setNegativeButton("Cancel") { _, _ ->
                viewModel.clearCreateDialogState()
                activeCreateDialog = null
            }
            .create()

        dialog.setOnCancelListener {
            viewModel.clearCreateDialogState()
            activeCreateDialog = null
        }

        dialog.setOnShowListener {
            dialog.enableTapOutsideToUnfocus()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialogBinding.root.clearFocusAndHideKeyboard()
                val amountStr = dialogBinding.etPaymentAmount.text?.toString()?.trim().orEmpty()
                val amount = amountStr.toDoubleOrNull()
                val description = dialogBinding.etPaymentDescription.text?.toString()?.trim().orEmpty()

                if (amount == null || amount <= 0.0) {
                    dialogBinding.tilPaymentAmount.error = "Enter a valid amount greater than 0"
                    return@setOnClickListener
                } else {
                    dialogBinding.tilPaymentAmount.error = null
                }

                if (description.isEmpty()) {
                    dialogBinding.tilPaymentDescription.error = "Enter a short description"
                    return@setOnClickListener
                } else if (description.length > GroupChat.MAX_PAYMENT_DESCRIPTION_LENGTH) {
                    dialogBinding.tilPaymentDescription.error =
                        "Description max ${GroupChat.MAX_PAYMENT_DESCRIPTION_LENGTH} characters"
                    return@setOnClickListener
                } else {
                    dialogBinding.tilPaymentDescription.error = null
                }

                val excludedIds = checkBoxes.filterValues { !it.isChecked }.keys
                if (excludedIds.size >= membersList.size) {
                    showSnackbar(dialogBinding.root, "At least one member must be included in the request")
                    return@setOnClickListener
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                PayPalsRepository.createPaymentRequest(
                    group = group,
                    totalAmount = amount,
                    description = description,
                    receiptDetails = "",
                    receiptImageBase64 = "",
                    excludedMemberIds = excludedIds
                ) { result ->
                    result.onSuccess {
                        viewModel.clearCreateDialogState()
                        activeCreateDialog = null
                        dialog.dismiss()
                        _binding?.let { showSnackbar(it.root, "Payment request tracked!") }
                    }.onFailure { e ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        _binding?.let { showErrorSnackbar(it.root, e, "Failed to track payment") }
                    }
                }
            }
        }

        activeCreateDialog = dialog
        dialog.show()
    }

    private fun openPayShareDialogWithState(payment: PaymentRequest) {
        if (activePayShareDialog?.isShowing == true) return
        viewModel.openPayShareDialog(payment.id)
        activePayShareDialog = showPayShareConfirmationDialog(
            payment = payment,
            initialNote = viewModel.draftPayShareNote.value,
            onNoteChanged = viewModel::updatePayShareNote,
            onDialogFinished = {
                viewModel.clearPayShareDialogState()
                activePayShareDialog = null
            }
        ) { _binding?.root }
    }

    /**
     * Dialog allowing EVERY member in the group to review the receipt (with the description as the breakdown),
     * see who has paid, and nudge unpaid members.
     */
    private fun showReviewReceiptDialog(payment: PaymentRequest) {
        val dialogBinding = DialogReviewReceiptBinding.inflate(layoutInflater)
        val uid = PayPalsRepository.currentUserId()
        val isRequester = uid != null && payment.requesterId == uid
        val dateStr = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
            .format(Date(payment.createdAt))

        dialogBinding.tvReceiptTitle.text = payment.description
        dialogBinding.tvReceiptMeta.text = "Paid by ${payment.requesterName} • $dateStr"
        dialogBinding.tvReceiptAmountSummary.text =
            "Total: ${PayPalsRepository.formatAmount(payment.totalAmount)} (${
                PayPalsRepository.formatAmount(payment.sharePerPerson)
            } / person)"

        dialogBinding.tvReceiptBreakdown.visibility = View.VISIBLE
        dialogBinding.tvReceiptBreakdown.text = "Breakdown: ${payment.description}"
        dialogBinding.ivReceiptImage.visibility = View.GONE

        val density = resources.displayMetrics.density
        dialogBinding.llParticipantsContainer.removeAllViews()

        payment.participantStatuses.values.forEach { status ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }

            val statusIcon = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (20 * density).toInt(),
                    (20 * density).toInt()
                ).apply {
                    marginEnd = (10 * density).toInt()
                }
                setImageResource(
                    if (status.paid) R.drawable.ic_check_circle else R.drawable.ic_pending
                )
            }

            val textContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val titleTv = TextView(requireContext()).apply {
                val statusLabel = if (status.paid) {
                    "PAID ${PayPalsRepository.formatAmount(status.amountOwed)}"
                } else {
                    "OWES ${PayPalsRepository.formatAmount(status.amountOwed)}"
                }
                text = "${status.displayName} — $statusLabel"
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            textContainer.addView(titleTv)

            if (status.paid && status.paymentNote.isNotBlank()) {
                val noteTv = TextView(requireContext()).apply {
                    text = "Note: \"${status.paymentNote}\""
                    textSize = 12f
                }
                textContainer.addView(noteTv)
            } else if (!status.paid && status.lastNudgedAt > 0L) {
                val nudgedTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(status.lastNudgedAt))
                val nudgeTv = TextView(requireContext()).apply {
                    text = "Nudged at $nudgedTime"
                    textSize = 11f
                }
                textContainer.addView(nudgeTv)
            }

            row.addView(statusIcon)
            row.addView(textContainer)

            dialogBinding.llParticipantsContainer.addView(row)
        }

        if (payment.excludedMemberNames.isNotEmpty()) {
            dialogBinding.tvExcludedHeader.visibility = View.VISIBLE
            dialogBinding.tvExcludedMembersList.visibility = View.VISIBLE
            dialogBinding.tvExcludedMembersList.text =
                payment.excludedMemberNames.joinToString(", ")
        } else {
            dialogBinding.tvExcludedHeader.visibility = View.GONE
            dialogBinding.tvExcludedMembersList.visibility = View.GONE
        }

        val logFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        dialogBinding.tvFullPaymentLogs.text = payment.paymentLogs.joinToString("\n\n") { entry ->
            "[${logFormatter.format(Date(entry.timestamp))}] ${entry.message}"
        }.ifBlank { "No activity logs." }

        val myStatus = if (uid != null) payment.participantStatuses[uid] else null
        val hasUnpaidOthers = payment.participantStatuses.values.any { !it.paid && it.uid != uid }
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Receipt & Payment Status")
            .setView(dialogBinding.root)
            .setNegativeButton("Close", null)

        if (myStatus != null && !myStatus.paid) {
            builder.setPositiveButton("Pay My Share (${PayPalsRepository.formatAmount(myStatus.amountOwed)})") { _, _ ->
                openPayShareDialogWithState(payment)
            }
        } else if (isRequester || hasUnpaidOthers) {
            builder.setPositiveButton("Nudge") { _, _ ->
                nudgeAllUnpaid(payment)
            }
        }

        builder.show()
    }

    /**
     * Pings all unpaid users on the payment request without any customization dialog.
     * Targets ONLY members who have not paid yet.
     */
    private fun nudgeAllUnpaid(payment: PaymentRequest) {
        val uid = PayPalsRepository.currentUserId() ?: return
        val unpaidIds = payment.participantStatuses.values
            .filter { !it.paid && it.uid != uid }
            .map { it.uid }

        if (unpaidIds.isEmpty()) {
            showSnackbar(
                binding.root,
                "All other included members have already paid — nudge only pings unpaid members.",
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            )
            return
        }

        PayPalsRepository.nudgeUnpaidParticipants(payment, unpaidIds) { result ->
            val root = _binding?.root ?: return@nudgeUnpaidParticipants
            result.onSuccess { count ->
                showSnackbar(root, "Nudged $count unpaid member(s)! They will receive a notification.")
            }.onFailure { e ->
                showErrorSnackbar(root, e, "Failed to nudge members")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activeCreateDialog?.dismiss()
        activeCreateDialog = null
        activePayShareDialog?.dismiss()
        activePayShareDialog = null
        groupListener?.remove()
        paymentsListener?.remove()
        _binding = null
    }
}
