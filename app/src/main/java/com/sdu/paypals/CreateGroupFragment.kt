package com.sdu.paypals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import com.sdu.paypals.databinding.FragmentCreateGroupBinding
import com.sdu.paypals.model.GroupIcon
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.util.bindSavedText
import com.sdu.paypals.util.collectOnStarted
import com.sdu.paypals.util.openGroupChat
import com.sdu.paypals.util.resolveGroupIconKey
import com.sdu.paypals.util.showErrorSnackbar
import com.sdu.paypals.viewmodel.CreateGroupViewModel

class CreateGroupFragment : Fragment() {

    private var _binding: FragmentCreateGroupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateGroupViewModel by viewModels()

    companion object {
        private val CHIP_TO_ICON = mapOf(
            R.id.chipIconGroups to GroupIcon.GROUPS,
            R.id.chipIconHome to GroupIcon.HOME,
            R.id.chipIconTrip to GroupIcon.TRIP,
            R.id.chipIconFood to GroupIcon.FOOD,
            R.id.chipIconShopping to GroupIcon.SHOPPING,
            R.id.chipIconCelebration to GroupIcon.CELEBRATION
        )
        private val ICON_TO_CHIP = CHIP_TO_ICON.entries.associate { (chipId, icon) -> icon to chipId }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSavedStateBindings()
        binding.btnCreateGroupSubmit.setOnClickListener {
            submitCreateGroup()
        }
    }

    private fun setupSavedStateBindings() {
        binding.etGroupName.bindSavedText(
            viewLifecycleOwner,
            viewModel.groupName,
            viewModel::updateGroupName
        )
        binding.etGroupDescription.bindSavedText(
            viewLifecycleOwner,
            viewModel.groupDescription,
            viewModel::updateGroupDescription
        )

        viewLifecycleOwner.collectOnStarted(viewModel.selectedIconKey) { iconKey ->
            val targetChipId = ICON_TO_CHIP[GroupIcon.fromKey(iconKey)] ?: R.id.chipIconGroups
            if (binding.chipGroupIcons.checkedChipId != targetChipId) {
                binding.chipGroupIcons.check(targetChipId)
            }
        }

        binding.chipGroupIcons.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            viewModel.updateSelectedIconKey(resolveGroupIconKey(checkedId, CHIP_TO_ICON))
        }
    }

    private fun submitCreateGroup() {
        val name = binding.etGroupName.text?.toString()?.trim().orEmpty()
        val description = binding.etGroupDescription.text?.toString()?.trim().orEmpty()

        if (name.isEmpty()) {
            binding.tilGroupName.error = "Please enter a group name"
            return
        } else {
            binding.tilGroupName.error = null
        }

        val iconKey = resolveGroupIconKey(binding.chipGroupIcons.checkedChipId, CHIP_TO_ICON)

        binding.progressBarCreateGroup.visibility = View.VISIBLE
        binding.btnCreateGroupSubmit.isEnabled = false

        PayPalsRepository.createGroup(
            name = name,
            description = description,
            iconKey = iconKey
        ) { result ->
            if (_binding == null) return@createGroup
            binding.progressBarCreateGroup.visibility = View.GONE
            binding.btnCreateGroupSubmit.isEnabled = true

            result.onSuccess { createdGroup ->
                viewModel.clearDraft()
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.createGroupFragment, true)
                    .build()
                openGroupChat(createdGroup.id, navOptions = navOptions)
            }.onFailure { e ->
                showErrorSnackbar(binding.root, e, "Failed to create group")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
