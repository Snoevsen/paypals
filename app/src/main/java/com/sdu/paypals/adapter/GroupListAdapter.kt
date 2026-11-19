package com.sdu.paypals.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sdu.paypals.databinding.ItemGroupBinding
import com.sdu.paypals.model.GroupChat
import com.sdu.paypals.util.simpleDiffCallback

class GroupListAdapter(
    private val currentUserId: () -> String?,
    private val onGroupClick: (GroupChat) -> Unit
) : ListAdapter<GroupChat, GroupListAdapter.GroupViewHolder>(simpleDiffCallback { it.id }) {

    inner class GroupViewHolder(private val binding: ItemGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(group: GroupChat) {
            val uid = currentUserId()
            binding.ivGroupIcon.setImageResource(group.getIconDrawableRes())
            binding.tvGroupName.text = group.name
            binding.tvGroupDescription.text = group.description.ifBlank { "No description" }

            val isAdmin = uid != null && group.isAdmin(uid)
            binding.tvAdminBadge.visibility = if (isAdmin) View.VISIBLE else View.GONE

            val count = group.memberIds.size
            binding.tvMembersSummary.text = if (count == 1) "1 member" else "$count members"
            binding.tvLastActivity.text = group.lastActivityText

            binding.root.setOnClickListener {
                onGroupClick(group)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemGroupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
