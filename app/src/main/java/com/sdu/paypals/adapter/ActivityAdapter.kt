package com.sdu.paypals.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sdu.paypals.databinding.ItemActivityBinding
import com.sdu.paypals.model.ActivityCategory
import com.sdu.paypals.model.UserActivityItem
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.util.simpleDiffCallback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityAdapter(
    private val onItemClick: (UserActivityItem) -> Unit
) : ListAdapter<UserActivityItem, ActivityAdapter.ActivityViewHolder>(simpleDiffCallback { it.id }) {

    companion object {
        private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        private val readableDateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

        fun formatTimestampForDisplay(timestamp: Long): String {
            return "${readableDateFormat.format(Date(timestamp))} (${timestampFormat.format(Date(timestamp))})"
        }
    }

    inner class ActivityViewHolder(private val binding: ItemActivityBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UserActivityItem) {
            binding.tvActivityPayer.text = "User: ${item.payerName}"
            binding.tvActivityAmount.text = PayPalsRepository.formatAmount(item.amount)
            binding.tvActivityDescription.text = item.description
            binding.tvActivityTimestamp.text = readableDateFormat.format(Date(item.timestamp))

            when (item.category) {
                ActivityCategory.EXPENSE_PAID_BY_ME -> {
                    binding.tvActivityCategoryBadge.text = "Expense (You Paid)"
                    binding.tvActivityAmount.setTextColor(Color.parseColor("#1565C0"))
                }
                ActivityCategory.PAID_TO_ME -> {
                    binding.tvActivityCategoryBadge.text = "Paid to You"
                    binding.tvActivityAmount.setTextColor(Color.parseColor("#2E7D32"))
                }
                ActivityCategory.REMINDER -> {
                    binding.tvActivityCategoryBadge.text = "Reminder"
                    binding.tvActivityAmount.setTextColor(Color.parseColor("#E65100"))
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val binding = ItemActivityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ActivityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
