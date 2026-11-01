package com.lisaan.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.common.Resource
import com.lisaan.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthScreenMode {
    WELCOME,
    SIGN_IN_EMAIL,
    SIGN_UP_EMAIL,
    FORGOT_PASSWORD
}

data class AuthUiState(
    val mode: AuthScreenMode = AuthScreenMode.WELCOME,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessageAr: String? = null,
    val errorMessageEn: String? = null,
    val passwordResetSentSuccess: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun switchMode(newMode: AuthScreenMode) {
        _uiState.update {
            it.copy(
                mode = newMode,
                errorMessageAr = null,
                errorMessageEn = null,
                passwordResetSentSuccess = false
            )
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, errorMessageAr = null, errorMessageEn = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessageAr = null, errorMessageEn = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessageAr = null, errorMessageEn = null) }
    }

    fun submitEmailSignIn(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.length < 6) {
            _uiState.update {
                it.copy(
                    errorMessageAr = "يرجى إدخال بريد إلكتروني صحيح وكلمة مرور من 6 أحرف على الأقل",
                    errorMessageEn = "Please enter a valid email and a password of at least 6 characters"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageAr = null) }
            when (val res = authRepository.signInWithEmail(state.email, state.password)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageAr = res.messageAr,
                            errorMessageEn = res.messageEn
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun submitEmailSignUp(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.displayName.isBlank() || state.email.isBlank() || state.password.length < 6) {
            _uiState.update {
                it.copy(
                    errorMessageAr = "يرجى ملء الاسم والبريد وكلمة مرور لا تقل عن 6 أحرف",
                    errorMessageEn = "Please provide your name, email, and a password (min 6 characters)"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageAr = null) }
            when (val res = authRepository.signUpWithEmail(state.displayName, state.email, state.password)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageAr = res.messageAr,
                            errorMessageEn = res.messageEn
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun handleGoogleIdToken(idToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageAr = null) }
            when (val res = authRepository.signInWithGoogleIdToken(idToken)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageAr = res.messageAr,
                            errorMessageEn = res.messageEn
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun continueAsGuest(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageAr = null) }
            when (val res = authRepository.continueAsGuest()) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageAr = res.messageAr,
                            errorMessageEn = res.messageEn
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email
        if (email.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessageAr = "يرجى كتابة بريدك الإلكتروني أولاً",
                    errorMessageEn = "Please enter your email address first"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageAr = null) }
            when (val res = authRepository.sendPasswordResetEmail(email)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, passwordResetSentSuccess = true)
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageAr = res.messageAr,
                            errorMessageEn = res.messageEn
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun deleteAccount(password: String?, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageAr = null) }
            when (val res = authRepository.deleteAccountPermanently(password)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onDeleted()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageAr = res.messageAr,
                            errorMessageEn = res.messageEn
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }
}
