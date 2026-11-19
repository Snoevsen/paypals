package com.sdu.paypals.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.sdu.paypals.databinding.ItemPaymentRequestBinding
import com.sdu.paypals.model.PaymentRequest
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.util.simpleDiffCallback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupPaymentAdapter(
    private val currentUserId: () -> String?,
    private val onReviewReceiptClick: (PaymentRequest) -> Unit,
    private val onPayShareClick: (PaymentRequest) -> Unit,
    private val onNudgeClick: (PaymentRequest) -> Unit
) : ListAdapter<PaymentRequest, GroupPaymentAdapter.PaymentViewHolder>(simpleDiffCallback { it.id }) {

    private val timeFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    inner class PaymentViewHolder(private val binding: ItemPaymentRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(payment: PaymentRequest) {
            val uid = currentUserId()
            val isSelfRequest = uid != null && payment.requesterId == uid
            val density = binding.root.resources.displayMetrics.density

            // Align to RIGHT if requested by self, LEFT if requested by others
            val params = binding.cardBubble.layoutParams as FrameLayout.LayoutParams
            if (isSelfRequest) {
                params.gravity = Gravity.END
                params.marginStart = (48 * density).toInt()
                params.marginEnd = (4 * density).toInt()
                val bgColor = MaterialColors.getColor(
                    binding.cardBubble,
                    com.google.android.material.R.attr.colorPrimaryContainer
                )
                binding.cardBubble.setCardBackgroundColor(bgColor)
                binding.tvRequesterHeader.text = "You paid"
            } else {
                params.gravity = Gravity.START
                params.marginStart = (4 * density).toInt()
                params.marginEnd = (48 * density).toInt()
                val bgColor = MaterialColors.getColor(
                    binding.cardBubble,
                    com.google.android.material.R.attr.colorSurfaceVariant
                )
                binding.cardBubble.setCardBackgroundColor(bgColor)
                binding.tvRequesterHeader.text = "${payment.requesterName} paid"
            }
            binding.cardBubble.layoutParams = params

            binding.tvTimestamp.text = timeFormatter.format(Date(payment.createdAt))
            binding.tvTotalAmount.text = PayPalsRepository.formatAmount(payment.totalAmount)
            binding.tvShareAmount.text =
                "(${PayPalsRepository.formatAmount(payment.sharePerPerson)} / person)"
            binding.tvPaymentDescription.text = payment.description

            // Excluded notice
            if (payment.excludedMemberNames.isNotEmpty()) {
                binding.tvExcludedNotice.visibility = View.VISIBLE
                binding.tvExcludedNotice.text =
                    "Excluded: ${payment.excludedMemberNames.joinToString(", ")}"
            } else {
                binding.tvExcludedNotice.visibility = View.GONE
            }

            // Paid progress
            val paidCount = payment.paidCount()
            val totalCount = payment.totalParticipantCount().coerceAtLeast(1)
            val progressPct = ((paidCount.toFloat() / totalCount.toFloat()) * 100).toInt()
            binding.progressPaid.progress = progressPct
            binding.tvPaidStatusSummary.text = "$paidCount/$totalCount paid"

            // Chronological log preview
            if (payment.paymentLogs.isNotEmpty()) {
                binding.tvPaymentLogPreview.visibility = View.VISIBLE
                val recentLogs = payment.paymentLogs.takeLast(3)
                binding.tvPaymentLogPreview.text =
                    recentLogs.joinToString("\n") { "• ${it.message}" }
            } else {
                binding.tvPaymentLogPreview.visibility = View.GONE
            }

            // Receipt review button (everybody can review)
            binding.btnReviewReceipt.setOnClickListener {
                onReviewReceiptClick(payment)
            }
            binding.cardBubble.setOnClickListener {
                onReviewReceiptClick(payment)
            }

            // Pay Share button (shown if current user is included and has NOT paid yet)
            val myStatus = if (uid != null) payment.participantStatuses[uid] else null
            if (myStatus != null && !myStatus.paid) {
                binding.btnPayMyShare.visibility = View.VISIBLE
                binding.btnPayMyShare.text =
                    "Pay ${PayPalsRepository.formatAmount(myStatus.amountOwed)}"
                binding.btnPayMyShare.setOnClickListener {
                    onPayShareClick(payment)
                }
            } else {
                binding.btnPayMyShare.visibility = View.GONE
            }

            // Nudge button: visible on requests to ping members who have not paid yet
            val hasUnpaidOthers = payment.participantStatuses.values.any { !it.paid && it.uid != uid }
            if (isSelfRequest || hasUnpaidOthers) {
                binding.btnNudgeUnpaid.visibility = View.VISIBLE
                binding.btnNudgeUnpaid.alpha = if (hasUnpaidOthers) 1.0f else 0.65f
                binding.btnNudgeUnpaid.setOnClickListener {
                    onNudgeClick(payment)
                }
            } else {
                binding.btnNudgeUnpaid.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val binding = ItemPaymentRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PaymentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
