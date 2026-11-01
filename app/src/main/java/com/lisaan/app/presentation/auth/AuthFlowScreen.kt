package com.lisaan.app.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lisaan.app.core.designsystem.components.NabeehMascotView
import com.lisaan.app.core.designsystem.components.NabeehMoodState
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage

@Composable
fun AuthFlowScreen(
    onAuthSuccessNavigateToHome: () -> Unit,
    onRequestGoogleSignInLauncher: ((String) -> Unit) -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NabeehMascotView(
            mood = if (state.isLoading) NabeehMoodState.THINKING else NabeehMoodState.IDLE,
            size = 110.dp
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (state.mode) {
                AuthScreenMode.WELCOME -> if (isAr) "احفظ تقدمك في «لِسان»" else "Save Your Progress on Lisaan"
                AuthScreenMode.SIGN_IN_EMAIL -> if (isAr) "تسجيل الدخول بالبريد" else "Sign In with Email"
                AuthScreenMode.SIGN_UP_EMAIL -> if (isAr) "إنشاء حساب جديد" else "Create a New Account"
                AuthScreenMode.FORGOT_PASSWORD -> if (isAr) "استعادة كلمة المرور" else "Reset Password"
            },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // عرض رسائل الخطأ إن وجدت
        val errText = if (isAr) state.errorMessageAr else state.errorMessageEn
        if (!errText.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = errText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        when (state.mode) {
            AuthScreenMode.WELCOME -> {
                Button(
                    onClick = { viewModel.switchMode(AuthScreenMode.SIGN_UP_EMAIL) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (isAr) "التسجيل بالبريد الإلكتروني" else "Sign Up with Email")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        onRequestGoogleSignInLauncher { idToken ->
                            viewModel.handleGoogleIdToken(idToken, onAuthSuccessNavigateToHome)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (isAr) "المتابعة باستخدام حساب Google" else "Continue with Google")
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = { viewModel.switchMode(AuthScreenMode.SIGN_IN_EMAIL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isAr) "لديك حساب بالفعل؟ سجّل الدخول" else "Already have an account? Sign In")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                OutlinedButton(
                    onClick = { viewModel.continueAsGuest(onAuthSuccessNavigateToHome) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PersonOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAr) "الدخول كضيف (تعلّم فوراً بدون حساب)" else "Continue as Guest")
                }
            }

            AuthScreenMode.SIGN_IN_EMAIL, AuthScreenMode.SIGN_UP_EMAIL -> {
                if (state.mode == AuthScreenMode.SIGN_UP_EMAIL) {
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = viewModel::onNameChange,
                        label = { Text(if (isAr) "الاسم الكامل" else "Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text(if (isAr) "البريد الإلكتروني" else "Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(if (isAr) "كلمة المرور" else "Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                if (state.mode == AuthScreenMode.SIGN_IN_EMAIL) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { viewModel.switchMode(AuthScreenMode.FORGOT_PASSWORD) }) {
                            Text(if (isAr) "نسيت كلمة المرور؟" else "Forgot Password?")
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Button(
                    onClick = {
                        if (state.mode == AuthScreenMode.SIGN_IN_EMAIL) {
                            viewModel.submitEmailSignIn(onAuthSuccessNavigateToHome)
                        } else {
                            viewModel.submitEmailSignUp(onAuthSuccessNavigateToHome)
                        }
                    },
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (state.mode == AuthScreenMode.SIGN_IN_EMAIL) {
                                if (isAr) "تسجيل الدخول" else "Sign In"
                            } else {
                                if (isAr) "إنشاء الحساب والبدء" else "Create Account"
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { viewModel.switchMode(AuthScreenMode.WELCOME) }) {
                    Text(if (isAr) "العودة لخيارات الدخول الأخرى" else "Back to all Sign-In options")
                }
            }

            AuthScreenMode.FORGOT_PASSWORD -> {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text(if (isAr) "أدخل بريدك الإلكتروني المسجل" else "Enter your registered email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (state.passwordResetSentSuccess) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = if (isAr) "تم إرسال رابط استعادة كلمة المرور إلى بريدك الإلكتروني بنجاح."
                            else "Password reset link has been sent to your email.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                Button(
                    onClick = viewModel::sendPasswordReset,
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (isAr) "إرسال رابط الاستعادة" else "Send Reset Link")
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { viewModel.switchMode(AuthScreenMode.SIGN_IN_EMAIL) }) {
                    Text(if (isAr) "العودة لتسجيل الدخول" else "Back to Sign In")
                }
            }
        }
    }
}

/**
 * نافذة تأكيد حذف الحساب نهائياً (تُستدعى من الإعدادات أو الحساب)
 */
@Composable
fun DeleteAccountConfirmationDialog(
    isAr: Boolean,
    onConfirmDelete: (password: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = {
            Text(if (isAr) "حذف الحساب نهائياً" else "Delete Account Permanently")
        },
        text = {
            Column {
                Text(
                    text = if (isAr) "سيؤدي هذا الإجراء إلى حذف حسابك وجميع نقاطك وسجل تقدمك ومفرداتك المحفوظة نهائياً ولا يمكن التراجع عنه."
                    else "This will permanently delete your account, XP, streak, and saved vocabulary. This action cannot be undone."
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isAr) "كلمة المرور للتأكيد (إن وجدت)" else "Password to confirm (if applicable)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmDelete(password.takeIf { it.isNotBlank() }) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(if (isAr) "نعم، احذف حسابي" else "Yes, Delete Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isAr) "إلغاء" else "Cancel")
            }
        }
    )
}
