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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.sdu.paypals.databinding.FragmentLoginBinding
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.util.bindSavedText
import com.sdu.paypals.util.collectOnStarted
import com.sdu.paypals.viewmodel.LoginViewModel

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setupSavedStateBindings()
        setupModeToggle()
        setupListeners()
    }

    private fun setupSavedStateBindings() {
        binding.etFullName.bindSavedText(viewLifecycleOwner, viewModel.fullName, viewModel::updateFullName)
        binding.etEmail.bindSavedText(viewLifecycleOwner, viewModel.email, viewModel::updateEmail)

        viewLifecycleOwner.collectOnStarted(viewModel.isSignUpMode) { signUp ->
            val targetId = if (signUp) R.id.btnTabSignup else R.id.btnTabLogin
            if (binding.toggleGroupMode.checkedButtonId != targetId) {
                binding.toggleGroupMode.check(targetId)
            }
            applySignUpModeUi(signUp)
        }
    }

    private fun setupModeToggle() {
        binding.toggleGroupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val signUp = checkedId == R.id.btnTabSignup
                viewModel.setSignUpMode(signUp)
                applySignUpModeUi(signUp)
            }
        }

        binding.btnSwitchMode.setOnClickListener {
            val newMode = !viewModel.isSignUpMode.value
            viewModel.setSignUpMode(newMode)
            binding.toggleGroupMode.check(if (newMode) R.id.btnTabSignup else R.id.btnTabLogin)
        }
    }

    private fun applySignUpModeUi(signUp: Boolean) {
        binding.tilFullName.visibility = if (signUp) View.VISIBLE else View.GONE
        binding.btnSubmit.text = getString(if (signUp) R.string.btn_signup else R.string.btn_login)
        binding.btnSwitchMode.text = getString(if (signUp) R.string.switch_to_login else R.string.switch_to_signup)

        // Clear any previous error states
        binding.tilFullName.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
    }

    private fun setupListeners() {
        binding.btnSubmit.setOnClickListener {
            if (viewModel.isSignUpMode.value) {
                performSignUp()
            } else {
                performLogin()
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()
        val fullName = binding.etFullName.text?.toString()?.trim().orEmpty()

        if (viewModel.isSignUpMode.value && fullName.isEmpty()) {
            binding.tilFullName.error = getString(R.string.error_empty_name)
            isValid = false
        } else {
            binding.tilFullName.error = null
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_invalid_email)
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_short_password)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        return isValid
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled = !isLoading
        binding.btnTabLogin.isEnabled = !isLoading
        binding.btnTabSignup.isEnabled = !isLoading
        binding.btnSwitchMode.isEnabled = !isLoading
    }

    private fun performLogin() {
        if (!validateInputs()) return

        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        setLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!isAdded) return@addOnCompleteListener
                setLoading(false)
                if (task.isSuccessful) {
                    navigateToHome()
                } else {
                    val message = task.exception?.localizedMessage ?: "Login failed"
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
            }
    }

    private fun performSignUp() {
        if (!validateInputs()) return

        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        setLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!isAdded) return@addOnCompleteListener
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(fullName)
                            .build()

                        user.updateProfile(profileUpdates)
                            .addOnCompleteListener {
                                if (!isAdded) return@addOnCompleteListener
                                setLoading(false)
                                navigateToHome()
                            }
                    } else {
                        setLoading(false)
                        navigateToHome()
                    }
                } else {
                    setLoading(false)
                    val message = task.exception?.localizedMessage ?: "Sign up failed"
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
            }
    }

    private fun navigateToHome() {
        viewModel.clearState()
        PayPalsRepository.syncCurrentUserProfile {
            if (isAdded) {
                findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
