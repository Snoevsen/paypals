package com.sdu.paypals

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ListenerRegistration
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.sdu.paypals.databinding.FragmentGroupSettingsBinding
import com.sdu.paypals.model.GroupChat
import com.sdu.paypals.model.GroupIcon
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.util.bindSavedText
import com.sdu.paypals.util.collectOnStarted
import com.sdu.paypals.util.resolveGroupIconKey
import com.sdu.paypals.util.showErrorSnackbar
import com.sdu.paypals.util.showSnackbar
import com.sdu.paypals.viewmodel.GroupSettingsViewModel

class GroupSettingsFragment : Fragment() {

    private var _binding: FragmentGroupSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GroupSettingsViewModel by viewModels()

    private var groupId: String = ""
    private var currentGroup: GroupChat? = null
    private var groupListener: ListenerRegistration? = null

    companion object {
        private val CHIP_TO_ICON = mapOf(
            R.id.chipEditIconGroups to GroupIcon.GROUPS,
            R.id.chipEditIconHome to GroupIcon.HOME,
            R.id.chipEditIconTrip to GroupIcon.TRIP,
            R.id.chipEditIconFood to GroupIcon.FOOD,
            R.id.chipEditIconShopping to GroupIcon.SHOPPING,
            R.id.chipEditIconCelebration to GroupIcon.CELEBRATION
        )
        private val ICON_TO_CHIP = CHIP_TO_ICON.entries.associate { (chipId, icon) -> icon to chipId }
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
        _binding = FragmentGroupSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (groupId.isBlank()) {
            findNavController().popBackStack()
            return
        }

        renderQrCode(PayPalsRepository.buildGroupQrPayload(groupId))
        setupSavedStateBindings()
        setupListeners()
        observeGroupRealtime()
    }

    private fun setupSavedStateBindings() {
        binding.etEditName.bindSavedText(
            viewLifecycleOwner,
            viewModel.draftName,
            viewModel::updateDraftName
        )
        binding.etEditDescription.bindSavedText(
            viewLifecycleOwner,
            viewModel.draftDescription,
            viewModel::updateDraftDescription
        )
        binding.etAddMemberEmail.bindSavedText(
            viewLifecycleOwner,
            viewModel.draftMemberEmail,
            viewModel::updateDraftMemberEmail
        )

        viewLifecycleOwner.collectOnStarted(viewModel.draftIconKey) { iconKey ->
            val targetChipId = ICON_TO_CHIP[GroupIcon.fromKey(iconKey)] ?: R.id.chipEditIconGroups
            if (binding.chipGroupEditIcons.checkedChipId != targetChipId) {
                binding.chipGroupEditIcons.check(targetChipId)
            }
        }

        binding.chipGroupEditIcons.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            viewModel.updateDraftIconKey(resolveGroupIconKey(checkedId, CHIP_TO_ICON))
        }
    }

    private fun renderQrCode(payload: String) {
        try {
            val sizePx = 512
            val bitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            binding.ivGroupQrCode.setImageBitmap(bmp)
        } catch (_: Exception) {
            // Ignore QR encoding failure
        }
    }

    private fun observeGroupRealtime() {
        groupListener?.remove()
        groupListener = PayPalsRepository.observeGroup(
            groupId = groupId,
            onUpdate = { group ->
                if (_binding == null) return@observeGroup
                if (group == null) {
                    // Group deleted -> return to GroupsFragment
                    navigateBackToGroupsList()
                    return@observeGroup
                }
                currentGroup = group
                bindGroupData(group)
            },
            onError = { e ->
                _binding?.let { showErrorSnackbar(it.root, e, "Failed to load group settings") }
            }
        )
    }

    private fun bindGroupData(group: GroupChat) {
        val uid = PayPalsRepository.currentUserId().orEmpty()
        val isAdmin = group.isAdmin(uid)

        viewModel.populateFromGroupIfNeeded(group)

        binding.etEditName.isEnabled = isAdmin
        binding.etEditDescription.isEnabled = isAdmin
        for (i in 0 until binding.chipGroupEditIcons.childCount) {
            binding.chipGroupEditIcons.getChildAt(i).isEnabled = isAdmin
        }
        binding.btnSaveGroupDetails.visibility = if (isAdmin) View.VISIBLE else View.GONE

        populateMembersList(group, uid, isAdmin)
        updateLeaveAndDeleteState(group, uid, isAdmin)
    }

    private fun populateMembersList(group: GroupChat, currentUid: String, currentUserIsAdmin: Boolean) {
        val density = resources.displayMetrics.density
        binding.llMembersList.removeAllViews()

        group.memberIds.forEach { memberId ->
            val member = group.members[memberId]
            val memberName = member?.displayName ?: "Member"
            val memberEmail = member?.email.orEmpty()
            val memberIsAdmin = group.isAdmin(memberId)

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }

            val infoCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameLine = buildString {
                append(memberName)
                if (memberId == currentUid) append(" (You)")
                if (memberIsAdmin) append(" • [Admin]")
            }

            val nameTv = TextView(requireContext()).apply {
                text = nameLine
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            infoCol.addView(nameTv)

            if (memberEmail.isNotBlank()) {
                val emailTv = TextView(requireContext()).apply {
                    text = memberEmail
                    textSize = 12f
                }
                infoCol.addView(emailTv)
            }

            row.addView(infoCol)

            if (currentUserIsAdmin && !memberIsAdmin) {
                val makeAdminBtn = MaterialButton(
                    requireContext(),
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
                ).apply {
                    text = "Make Admin"
                    textSize = 11f
                    setOnClickListener {
                        isEnabled = false
                        PayPalsRepository.assignAdmin(group, memberId) { res ->
                            if (_binding == null) return@assignAdmin
                            res.onSuccess {
                                showSnackbar(binding.root, "$memberName is now an Admin!")
                            }.onFailure { e ->
                                isEnabled = true
                                showErrorSnackbar(binding.root, e, "Failed to assign admin")
                            }
                        }
                    }
                }
                row.addView(makeAdminBtn)
            }

            binding.llMembersList.addView(row)
        }
    }

    private fun updateLeaveAndDeleteState(group: GroupChat, currentUid: String, isAdmin: Boolean) {
        val isOnlyMember = group.memberIds.size <= 1
        val canLeave = group.canUserLeave(currentUid)

        if (isOnlyMember && isAdmin) {
            binding.tvAdminLeaveHint.text =
                "You are the only person in this group. You are allowed to delete or leave the group."
            binding.btnDeleteGroup.visibility = View.VISIBLE
            binding.btnLeaveGroup.isEnabled = true
        } else {
            binding.btnDeleteGroup.visibility = View.GONE
            binding.tvAdminLeaveHint.text =
                "To leave or delete this group, you must either be the only one in the group, or have another admin than yourself."
            binding.btnLeaveGroup.isEnabled = canLeave
        }
    }

    private fun setupListeners() {
        binding.btnSaveGroupDetails.setOnClickListener {
            val group = currentGroup ?: return@setOnClickListener
            val newName = binding.etEditName.text?.toString()?.trim().orEmpty()
            val newDesc = binding.etEditDescription.text?.toString()?.trim().orEmpty()

            if (newName.isEmpty()) {
                binding.tilEditName.error = "Group name cannot be empty"
                return@setOnClickListener
            } else {
                binding.tilEditName.error = null
            }

            val selectedIconKey = resolveGroupIconKey(binding.chipGroupEditIcons.checkedChipId, CHIP_TO_ICON)

            binding.btnSaveGroupDetails.isEnabled = false
            PayPalsRepository.updateGroupDetails(group, newName, newDesc, selectedIconKey) { res ->
                if (_binding == null) return@updateGroupDetails
                binding.btnSaveGroupDetails.isEnabled = true
                res.onSuccess {
                    showSnackbar(binding.root, "Group details saved!")
                }.onFailure { e ->
                    showErrorSnackbar(binding.root, e, "Failed to update group")
                }
            }
        }

        binding.btnAddMember.setOnClickListener {
            val group = currentGroup ?: return@setOnClickListener
            val email = binding.etAddMemberEmail.text?.toString()?.trim().orEmpty()
            if (email.isEmpty()) {
                binding.tilAddMemberEmail.error = "Enter the member's email"
                return@setOnClickListener
            } else {
                binding.tilAddMemberEmail.error = null
            }

            binding.btnAddMember.isEnabled = false
            PayPalsRepository.addMemberByEmail(group, email) { res ->
                if (_binding == null) return@addMemberByEmail
                binding.btnAddMember.isEnabled = true
                res.onSuccess { addedMember ->
                    viewModel.clearMemberEmailDraft()
                    binding.etAddMemberEmail.setText("")
                    showSnackbar(binding.root, "Added ${addedMember.displayName} to the group!")
                }.onFailure { e ->
                    showErrorSnackbar(binding.root, e, "Could not add member")
                }
            }
        }

        binding.btnLeaveGroup.setOnClickListener {
            val group = currentGroup ?: return@setOnClickListener
            val uid = PayPalsRepository.currentUserId().orEmpty()
            if (!group.canUserLeave(uid)) {
                showSnackbar(
                    binding.root,
                    "You must either be the only one in the group, or have another admin than yourself before leaving.",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                )
                return@setOnClickListener
            }

            binding.btnLeaveGroup.isEnabled = false
            PayPalsRepository.leaveGroup(group) { res ->
                if (_binding == null) return@leaveGroup
                res.onSuccess {
                    navigateBackToGroupsList()
                }.onFailure { e ->
                    binding.btnLeaveGroup.isEnabled = true
                    showErrorSnackbar(binding.root, e, "Failed to leave group")
                }
            }
        }

        binding.btnDeleteGroup.setOnClickListener {
            val group = currentGroup ?: return@setOnClickListener
            binding.btnDeleteGroup.isEnabled = false
            PayPalsRepository.deleteGroup(group) { res ->
                if (_binding == null) return@deleteGroup
                res.onSuccess {
                    navigateBackToGroupsList()
                }.onFailure { e ->
                    binding.btnDeleteGroup.isEnabled = true
                    showErrorSnackbar(binding.root, e, "Failed to delete group")
                }
            }
        }
    }

    private fun navigateBackToGroupsList() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.groupsFragment, false)
            .build()
        findNavController().navigate(R.id.groupsFragment, null, navOptions)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        groupListener?.remove()
        _binding = null
    }
}
