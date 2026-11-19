package com.sdu.paypals

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ListenerRegistration
import com.sdu.paypals.databinding.FragmentGroupBalanceBinding
import com.sdu.paypals.model.GroupChat
import com.sdu.paypals.model.PaymentRequest
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.util.showPayShareConfirmationDialog
import com.sdu.paypals.util.showSnackbar

class GroupBalanceFragment : Fragment() {

    private var _binding: FragmentGroupBalanceBinding? = null
    private val binding get() = _binding!!

    private var groupId: String = ""
    private var currentGroup: GroupChat? = null
    private var currentPayments: List<PaymentRequest> = emptyList()

    private var groupListener: ListenerRegistration? = null
    private var paymentsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getString("groupId").orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupBalanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (groupId.isBlank()) {
            findNavController().popBackStack()
            return
        }

        startRealtimeListeners()
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
                (activity as? AppCompatActivity)?.supportActionBar?.title = "${group.name} — Balance"
                binding.tvBalanceGroupTitle.text = "${group.name} — Your Balance"
                renderBalanceView()
            },
            onError = { _ -> }
        )

        paymentsListener?.remove()
        paymentsListener = PayPalsRepository.observeGroupPayments(
            groupId = groupId,
            onUpdate = { payments ->
                if (_binding == null) return@observeGroupPayments
                currentPayments = payments
                renderBalanceView()
            },
            onError = { _ -> }
        )
    }

    private fun renderBalanceView() {
        if (_binding == null) return
        val uid = PayPalsRepository.currentUserId() ?: return
        val balance = PayPalsRepository.calculateUserBalance(uid, currentPayments)
        val density = resources.displayMetrics.density

        // 1. Top Summary Card: What I'm Owed & What I Owe
        binding.tvViewOwedToMe.text = PayPalsRepository.formatAmount(balance.totalOwedToMe)
        binding.tvViewIOwe.text = PayPalsRepository.formatAmount(balance.totalIOwe)
        binding.tvNetBalanceText.text = balance.formatSummaryText(inGroup = true, PayPalsRepository::formatAmount)

        // 2. Owed From (Members Who Owe You)
        binding.llOwedFromMembers.removeAllViews()
        if (balance.owedByMembers.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = "No group members owe you right now."
                textSize = 13f
            }
            binding.llOwedFromMembers.addView(emptyTv)
            binding.btnNudgeUnpaidInBalanceView.visibility = View.GONE
        } else {
            balance.owedByMembers.forEach { memberDebt ->
                val row = createMemberRow(
                    density = density,
                    nameText = memberDebt.displayName,
                    subtitleText = "Owes you",
                    amountText = PayPalsRepository.formatAmount(memberDebt.amount),
                    amountColor = Color.parseColor("#2E7D32"),
                    iconRes = R.drawable.ic_profile
                )
                binding.llOwedFromMembers.addView(row)
            }

            if (balance.unpaidRequestsOwedToMe.isNotEmpty()) {
                binding.btnNudgeUnpaidInBalanceView.visibility = View.VISIBLE
                binding.btnNudgeUnpaidInBalanceView.setOnClickListener {
                    balance.unpaidRequestsOwedToMe.forEach { req ->
                        val unpaidIds = req.participantStatuses.values
                            .filter { !it.paid && it.uid != uid }
                            .map { it.uid }
                        if (unpaidIds.isNotEmpty()) {
                            PayPalsRepository.nudgeUnpaidParticipants(req, unpaidIds) { _ -> }
                        }
                    }
                    showSnackbar(binding.root, "Nudged unpaid members in this group!")
                }
            } else {
                binding.btnNudgeUnpaidInBalanceView.visibility = View.GONE
            }
        }

        // 3. Owe To (Members You Owe)
        binding.llIOweToMembers.removeAllViews()
        if (balance.iOweToMembers.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = "You don't owe any group members right now."
                textSize = 13f
            }
            binding.llIOweToMembers.addView(emptyTv)
        } else {
            balance.iOweToMembers.forEach { memberDebt ->
                val row = createMemberRow(
                    density = density,
                    nameText = memberDebt.displayName,
                    subtitleText = "You owe",
                    amountText = PayPalsRepository.formatAmount(memberDebt.amount),
                    amountColor = Color.parseColor("#C62828"),
                    iconRes = R.drawable.ic_profile
                )
                binding.llIOweToMembers.addView(row)
            }

            // Allow paying any unpaid request directly from this view
            balance.unpaidRequestsIOwe.forEach { req ->
                val myShare = req.participantStatuses[uid]?.amountOwed ?: req.sharePerPerson
                val payBtn = MaterialButton(requireContext()).apply {
                    text = "Pay ${PayPalsRepository.formatAmount(myShare)} to ${req.requesterName} (${req.description})"
                    textSize = 12f
                    setOnClickListener {
                        showPayShareConfirmationDialog(req) { _binding?.root }
                    }
                }
                binding.llIOweToMembers.addView(payBtn)
            }
        }

        // 4. All Group Members Balance List (shows every member in the group and who owes whom)
        binding.llAllGroupMembersBalances.removeAllViews()
        val group = currentGroup
        val members = group?.memberIds?.mapNotNull { group.members[it] }.orEmpty()
        val owedByMap = balance.owedByMembers.associateBy { it.uid }
        val iOweMap = balance.iOweToMembers.associateBy { it.uid }

        if (members.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = "No members found."
                textSize = 13f
            }
            binding.llAllGroupMembersBalances.addView(emptyTv)
        } else {
            val green = Color.parseColor("#2E7D32")
            val red = Color.parseColor("#C62828")
            for (member in members) {
                val isSelf = member.uid == uid
                val theyOweMe = owedByMap[member.uid]?.amount ?: 0.0
                val iOweThem = iOweMap[member.uid]?.amount ?: 0.0

                val (subtitle, rightAmountText, rightColor) = when {
                    isSelf -> {
                        val net = balance.netBalance
                        Triple(
                            "You (Owed: ${PayPalsRepository.formatAmount(balance.totalOwedToMe)} • Owe: ${PayPalsRepository.formatAmount(balance.totalIOwe)})",
                            if (net >= 0.0) "+${PayPalsRepository.formatAmount(net)}" else "-${PayPalsRepository.formatAmount(-net)}",
                            if (net >= 0.0) green else red
                        )
                    }
                    theyOweMe > 0.0 && iOweThem > 0.0 -> {
                        val net = theyOweMe - iOweThem
                        Triple(
                            "Owes you ${PayPalsRepository.formatAmount(theyOweMe)} • You owe ${PayPalsRepository.formatAmount(iOweThem)}",
                            if (net >= 0.0) "Owes you ${PayPalsRepository.formatAmount(net)}" else "You owe ${PayPalsRepository.formatAmount(-net)}",
                            if (net >= 0.0) green else red
                        )
                    }
                    theyOweMe > 0.0 -> Triple(
                        "Owed from ${member.displayName}",
                        "Owes you ${PayPalsRepository.formatAmount(theyOweMe)}",
                        green
                    )
                    iOweThem > 0.0 -> Triple(
                        "You owe ${member.displayName}",
                        "You owe ${PayPalsRepository.formatAmount(iOweThem)}",
                        red
                    )
                    else -> Triple(
                        "Settled up with you",
                        PayPalsRepository.formatAmount(0.0),
                        Color.GRAY
                    )
                }

                val displayLabel = if (isSelf) "${member.displayName} (You)" else member.displayName
                val row = createMemberRow(
                    density = density,
                    nameText = displayLabel,
                    subtitleText = subtitle,
                    amountText = rightAmountText,
                    amountColor = rightColor,
                    iconRes = R.drawable.ic_profile
                )
                binding.llAllGroupMembersBalances.addView(row)
            }
        }
    }

    private fun createMemberRow(
        density: Float,
        nameText: String,
        subtitleText: String,
        amountText: String,
        amountColor: Int,
        iconRes: Int
    ): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }

        val icon = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                (28 * density).toInt(),
                (28 * density).toInt()
            ).apply {
                marginEnd = (12 * density).toInt()
            }
            setImageResource(iconRes)
        }

        val textCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameTv = TextView(requireContext()).apply {
            text = nameText
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }

        val subTv = TextView(requireContext()).apply {
            text = subtitleText
            textSize = 12f
        }

        textCol.addView(nameTv)
        textCol.addView(subTv)

        val amountTv = TextView(requireContext()).apply {
            text = amountText
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(amountColor)
        }

        row.addView(icon)
        row.addView(textCol)
        row.addView(amountTv)
        return row
    }

    override fun onDestroyView() {
        super.onDestroyView()
        groupListener?.remove()
        paymentsListener?.remove()
        _binding = null
    }
}
