package za.org.rtc.community.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale

import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import za.org.rtc.community.ui.components.RtcBrandLockup
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.theme.RtcSpacing

/**
 * SignInScreen composable providing email/password authentication with real-time StateFlow
 * input validation, Remember Me checkbox persisted to DataStore, loading indicators with
 * button disabling, Forgot Password link to Supabase password recovery flow, and guest access.
 */
@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit = {},
    onDismissToGuest: (() -> Unit)? = null,
    onNavigateToForgotPassword: ((email: String) -> Unit)? = null,
    viewModel: AuthStateViewModel = hiltViewModel(),
    formViewModel: SignInFormViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by formViewModel.formState.collectAsStateWithLifecycle()

    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var showForgotPasswordModal by rememberSaveable { mutableStateOf(false) }
    // Reactively notify on successful authentication
    LaunchedEffect(uiState.authStatus) {
        if (uiState.authStatus is AuthStatus.Authenticated) {
            onSignInSuccess()
        }
    }

    // Display error message in snackbar if present
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            coroutineScope.launch { snackbarHostState.showSnackbar(error) }
            viewModel.clearError()
        }
    }

    if (showForgotPasswordModal) {
        ForgotPasswordScreen(
            initialEmail = formState.email,
            onNavigateBack = { showForgotPasswordModal = false },
            viewModel = viewModel
        )
        return
    }

    val isAnyLoading = uiState.isLoading

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RtcSpacing.cardPadding, vertical = RtcSpacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // App Branding & Title
            RtcBrandLockup(
                modifier = Modifier.padding(bottom = RtcSpacing.section)
            )

            RtcCard(
                protected = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sign_in_card")
            ) {
                Column(
                    modifier = Modifier.padding(RtcSpacing.standard),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.standard)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "Secure Authentication",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )

                    Text(
                        text = "Sign in to your account",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Access community updates, municipal reports, marketplace services, and administrative workspaces with verified credentials.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(RtcSpacing.compact))

                    // Email Input Field with StateFlow real-time validation error
                    OutlinedTextField(
                        value = formState.email,
                        onValueChange = { formViewModel.onEmailChanged(it) },
                        label = { Text("Email address") },
                        placeholder = { Text("resident@example.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Email,
                                contentDescription = "Email Icon"
                            )
                        },
                        isError = formState.emailError != null,
                        supportingText = {
                            if (formState.emailError != null) {
                                Text(
                                    text = formState.emailError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("email_error_message")
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !isAnyLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input")
                    )

                    // Password Input Field with StateFlow complexity validation error
                    OutlinedTextField(
                        value = formState.password,
                        onValueChange = { formViewModel.onPasswordChanged(it) },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Password Icon"
                            )
                        },
                        trailingIcon = {
                            val image = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            val description = if (isPasswordVisible) "Hide password" else "Show password"
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
                        isError = formState.passwordError != null,
                        supportingText = {
                            if (formState.passwordError != null) {
                                Text(
                                    text = formState.passwordError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("password_error_message")
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !isAnyLoading,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                formViewModel.markAllTouched()
                                if (formState.isFormValid && !isAnyLoading) {
                                    viewModel.signInWithEmail(
                                        email = formState.email,
                                        password = formState.password,
                                        rememberMe = formState.rememberMe
                                    )
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )

                    // Remember Me Checkbox & Forgot Password Link row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("remember_me_container")
                        ) {
                            Checkbox(
                                checked = formState.rememberMe,
                                onCheckedChange = { formViewModel.onRememberMeChanged(it) },
                                enabled = !isAnyLoading,
                                modifier = Modifier.testTag("remember_me_checkbox")
                            )
                            Text(
                                text = "Remember me",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        TextButton(
                            onClick = {
                                if (onNavigateToForgotPassword != null) {
                                    onNavigateToForgotPassword(formState.email)
                                } else {
                                    showForgotPasswordModal = true
                                }
                            },
                            enabled = !isAnyLoading,
                            modifier = Modifier.testTag("forgot_password_button")
                        ) {
                            Text(
                                text = "Forgot password?",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Email / Password Login Button with CircularProgressIndicator and disabled state
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            formViewModel.markAllTouched()
                            if (formState.isFormValid && !isAnyLoading) {
                                viewModel.signInWithEmail(
                                    email = formState.email,
                                    password = formState.password,
                                    rememberMe = formState.rememberMe
                                )
                            }
                        },
                        enabled = formState.isFormValid && !isAnyLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("sign_in_submit_button")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(22.dp)
                                    .testTag("login_progress_indicator"),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Signing in...", fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Sign In", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (onDismissToGuest != null) {
                        OutlinedButton(
                            onClick = onDismissToGuest,
                            enabled = !isAnyLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("continue_as_guest_button")
                        ) {
                            Text("Continue as Guest")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(RtcSpacing.standard))

            // Security assurance footer
            Row(
                horizontalArrangement = Arrangement.spacedBy(RtcSpacing.tiny),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Encrypted Connection",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "End-to-end encrypted session with Supabase GoTrue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (uiState.isAuthenticating) {
                BreathingLoaderOverlay()
            }
        }
    }
}
}

/**
 * A full-screen semi-transparent overlay with a breathing animation circle.
 */
@Composable
private fun BreathingLoaderOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = false) {}, // Scrim/interactable blocker
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Outer breathing glow circle
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(scale)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                            shape = CircleShape
                        )
                )
                // Middle solid ring
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Authenticating…",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Establishing a secure session",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
