package com.sdu.paypals

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.sdu.paypals.databinding.FragmentProfileBinding
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.util.bindSavedText
import com.sdu.paypals.viewmodel.ProfileViewModel

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        loadUserProfile()
        setupSavedStateBindings()
        setupListeners()
    }

    private fun setupSavedStateBindings() {
        binding.etEditFullName.bindSavedText(
            viewLifecycleOwner,
            viewModel.draftFullName,
            viewModel::updateDraftFullName
        )
        binding.etEditEmail.bindSavedText(
            viewLifecycleOwner,
            viewModel.draftEmail,
            viewModel::updateDraftEmail
        )
    }

    private fun loadUserProfile() {
        val user = auth.currentUser
        val displayName = user?.displayName.orEmpty()
        val email = user?.email.orEmpty()

        binding.tvFullName.text =
            displayName.ifBlank { getString(R.string.placeholder_profile_title) }
        binding.tvEmail.text = email

        viewModel.populateFromUserIfNeeded(displayName, email)
    }

    private fun setupListeners() {
        binding.btnSaveProfile.setOnClickListener {
            saveProfileChanges()
        }

        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            findNavController().navigate(R.id.action_global_to_loginFragment)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.progressBarProfile.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSaveProfile.isEnabled = !isLoading
        binding.btnSignOut.isEnabled = !isLoading
    }

    private fun saveProfileChanges() {
        val user = auth.currentUser ?: return
        val currentName = user.displayName.orEmpty()
        val currentEmail = user.email.orEmpty()

        val newName = binding.etEditFullName.text?.toString()?.trim().orEmpty()
        val newEmail = binding.etEditEmail.text?.toString()?.trim().orEmpty()
        val newPassword = binding.etNewPassword.text?.toString()?.trim().orEmpty()
        val currentPassword = binding.etCurrentPassword.text?.toString()?.trim().orEmpty()

        binding.tilEditFullName.error = null
        binding.tilEditEmail.error = null
        binding.tilNewPassword.error = null
        binding.tilCurrentPassword.error = null

        var isValid = true

        if (newName.isEmpty()) {
            binding.tilEditFullName.error = getString(R.string.error_empty_name)
            isValid = false
        }

        if (newEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            binding.tilEditEmail.error = getString(R.string.error_invalid_email)
            isValid = false
        }

        if (newPassword.isNotEmpty() && newPassword.length < 6) {
            binding.tilNewPassword.error = getString(R.string.error_short_password)
            isValid = false
        }

        val emailChanged = !newEmail.equals(currentEmail, ignoreCase = true)
        val passwordChanged = newPassword.isNotEmpty()
        val nameChanged = newName != currentName

        if (!nameChanged && !emailChanged && !passwordChanged) {
            Snackbar.make(binding.root, "No changes to save.", Snackbar.LENGTH_SHORT).show()
            return
        }

        if ((emailChanged || passwordChanged) && currentPassword.isEmpty()) {
            binding.tilCurrentPassword.error =
                "Enter your current password to change email or password"
            isValid = false
        }

        if (!isValid) return

        setLoading(true)

        if (emailChanged || passwordChanged) {
            val credential = EmailAuthProvider.getCredential(currentEmail, currentPassword)
            user.reauthenticate(credential)
                .addOnSuccessListener {
                    if (!isAdded) return@addOnSuccessListener
                    applyAuthUpdates(user, newName, newEmail, newPassword, nameChanged, emailChanged, passwordChanged)
                }
                .addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    setLoading(false)
                    binding.tilCurrentPassword.error = "Re-authentication failed"
                    Snackbar.make(
                        binding.root,
                        e.localizedMessage ?: "Incorrect current password",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
        } else {
            applyAuthUpdates(user, newName, newEmail, newPassword, nameChanged, emailChanged, passwordChanged)
        }
    }

    private fun applyAuthUpdates(
        user: FirebaseUser,
        newName: String,
        newEmail: String,
        newPassword: String,
        nameChanged: Boolean,
        emailChanged: Boolean,
        passwordChanged: Boolean
    ) {
        fun stepUpdateName(onNext: () -> Unit) {
            if (!nameChanged) {
                onNext()
                return
            }
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()
            user.updateProfile(profileUpdates)
                .addOnSuccessListener { onNext() }
                .addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    setLoading(false)
                    Snackbar.make(
                        binding.root,
                        e.localizedMessage ?: "Failed to update full name",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
        }

        fun stepUpdateEmail(onNext: (String?) -> Unit) {
            if (!emailChanged) {
                onNext(null)
                return
            }
            @Suppress("DEPRECATION")
            user.updateEmail(newEmail)
                .addOnSuccessListener {
                    onNext(null)
                }
                .addOnFailureListener {
                    // Fallback to verifyBeforeUpdateEmail if project enforces email verification
                    user.verifyBeforeUpdateEmail(newEmail)
                        .addOnSuccessListener {
                            onNext("Verification link sent to $newEmail to finalize email change.")
                        }
                        .addOnFailureListener { e2 ->
                            if (!isAdded) return@addOnFailureListener
                            setLoading(false)
                            Snackbar.make(
                                binding.root,
                                e2.localizedMessage ?: "Failed to update email",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                }
        }

        fun stepUpdatePassword(emailNotice: String?, onDone: (String?) -> Unit) {
            if (!passwordChanged) {
                onDone(emailNotice)
                return
            }
            user.updatePassword(newPassword)
                .addOnSuccessListener {
                    onDone(emailNotice)
                }
                .addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    setLoading(false)
                    Snackbar.make(
                        binding.root,
                        e.localizedMessage ?: "Failed to update password",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
        }

        stepUpdateName {
            stepUpdateEmail { emailNotice ->
                stepUpdatePassword(emailNotice) { finalNotice ->
                    PayPalsRepository.propagateProfileUpdate(newName, newEmail) {
                        if (!isAdded || _binding == null) return@propagateProfileUpdate
                        setLoading(false)
                        binding.etNewPassword.setText("")
                        binding.etCurrentPassword.setText("")
                        binding.tvFullName.text = newName
                        binding.tvEmail.text = user.email ?: newEmail

                        val msg = finalNotice ?: "Profile updated successfully!"
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
