package com.capstone.datara.ui.auth

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.ui.auth.components.DataraErrorBanner
import com.capstone.datara.ui.auth.components.DataraFieldLabel
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.auth.components.DataraLogo
import com.capstone.datara.ui.auth.components.DataraPrimaryButton
import com.capstone.datara.ui.auth.components.DataraSecondaryButton
import com.capstone.datara.ui.auth.components.DataraTextField
import com.capstone.datara.ui.auth.components.PasswordStrengthMeter
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.AuthPanelTheme
import kotlin.math.roundToInt

/** Which form the auth panel shows. Null on [AuthEntryScreen] means the welcome state. */
enum class AuthPanel { Login, Register }

// Material 3 emphasized easing: things arriving decelerate, things leaving accelerate.
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val ENTER_MS = 400
private const val EXIT_MS = 250

private val PanelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

private val WelcomeLogoSize = 112.dp
private val OpenLogoSize = 56.dp

/** Height the collapsed hero (logo only) keeps above the open panel. */
private val CollapsedHeroHeight = 104.dp

/**
 * First screen for signed-out users. The welcome state shows the logo, what the app does, and
 * the ways in. Choosing Log in or Create account shrinks the logo to the top and raises a panel
 * holding the form; switching between the two forms morphs the panel in place. Back, the panel
 * handle, or dragging the handle down returns to the welcome state.
 *
 * This is an in-screen panel rather than a ModalBottomSheet: forms in a modal sheet fight the
 * keyboard, and Forgot password must navigate to a full screen and come back to the same panel.
 */
@Composable
fun AuthEntryScreen(
    onSignedIn: (needsUsername: Boolean) -> Unit,
    onForgotPasswordClick: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var panel by rememberSaveable { mutableStateOf<AuthPanel?>(null) }
    // The form keeps showing while the panel slides away after `panel` is cleared.
    var lastPanel by rememberSaveable { mutableStateOf(AuthPanel.Login) }

    LaunchedEffect(uiState) {
        (uiState as? AuthUiState.Success)?.let { onSignedIn(it.needsUsername) }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.clearError() }
    }

    // Focus left in a form would otherwise move to the first welcome button when the panel
    // closes and leave it drawn in its focused (gray) state.
    val focusManager = LocalFocusManager.current

    fun openPanel(target: AuthPanel) {
        focusManager.clearFocus()
        viewModel.clearError()
        lastPanel = target
        panel = target
    }

    fun closePanel() {
        focusManager.clearFocus()
        viewModel.clearError()
        panel = null
    }

    BackHandler(enabled = panel != null) { closePanel() }

    val isOpen = panel != null
    val transition = updateTransition(targetState = isOpen, label = "AuthPanel")
    val logoSize by transition.animateDp(
        transitionSpec = { tween(if (targetState) ENTER_MS else EXIT_MS, easing = EmphasizedDecelerate) },
        label = "LogoSize"
    ) { open -> if (open) OpenLogoSize else WelcomeLogoSize }

    // Measured sizes, used to center the hero in whatever space the bottom content leaves.
    val density = LocalDensity.current
    var copyHeightPx by remember { mutableIntStateOf(0) }
    var actionsHeightPx by remember { mutableIntStateOf(0) }
    var panelHeightPx by remember { mutableIntStateOf(0) }
    // Until the first real measurement lands, jump to positions instead of animating, so the
    // hero doesn't visibly slide into place on launch.
    var layoutSettled by remember { mutableStateOf(false) }
    LaunchedEffect(copyHeightPx > 0 && actionsHeightPx > 0) {
        if (copyHeightPx > 0 && actionsHeightPx > 0) {
            withFrameNanos { }
            layoutSettled = true
        }
    }

    NavigationBarIcons(lightIcons = isOpen)

    // Only the status bar is padded here. The navy panel runs behind the navigation bar (gesture
    // handle or 3-button bar), and the welcome buttons and panel content pad for it themselves.
    // imePadding consumes the IME inset, so that inner navigation-bar padding drops to zero while
    // the keyboard is up instead of being added twice.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        val heroTopTarget = with(density) {
            val available = constraints.maxHeight.toFloat()
            if (isOpen) {
                // Logo (plus DataraLogo's 24dp clear space) centered above the panel.
                val logoBox = (OpenLogoSize + 24.dp).toPx()
                ((available - panelHeightPx - logoBox) / 2).toDp().coerceAtLeast(4.dp)
            } else {
                val heroHeight = (WelcomeLogoSize + 24.dp).toPx() + copyHeightPx
                ((available - actionsHeightPx - heroHeight) / 2).toDp().coerceAtLeast(16.dp)
            }
        }
        val heroTop by animateDpAsState(
            targetValue = heroTopTarget,
            animationSpec = if (layoutSettled) {
                tween(if (isOpen) ENTER_MS else EXIT_MS, easing = EmphasizedDecelerate)
            } else {
                snap()
            },
            label = "HeroTop"
        )

        // In the welcome state the hero may not grow into the buttons: on a small screen with a
        // large font it scrolls within the space above them instead of sliding underneath.
        val heroMaxHeight = (maxHeight - with(density) { actionsHeightPx.toDp() } - heroTop)
            .coerceAtLeast(0.dp)
        val heroScroll = rememberScrollState()

        // Hero: logo always, name and value statement only in the welcome state.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = heroTop)
                .then(
                    if (!isOpen && actionsHeightPx > 0) {
                        Modifier
                            .heightIn(max = heroMaxHeight)
                            .verticalScroll(heroScroll)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DataraLogo(size = logoSize)
            AnimatedVisibility(
                visible = !isOpen,
                enter = fadeIn(tween(ENTER_MS, delayMillis = 100)) + expandVertically(tween(ENTER_MS)),
                exit = fadeOut(tween(EXIT_MS / 2)) + shrinkVertically(tween(EXIT_MS))
            ) {
                WelcomeCopy(Modifier.onSizeChanged { copyHeightPx = it.height })
            }
        }

        // Welcome actions, anchored to the bottom.
        AnimatedVisibility(
            visible = !isOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(ENTER_MS, delayMillis = 100)) +
                slideInVertically(tween(ENTER_MS, easing = EmphasizedDecelerate)) { it / 4 },
            exit = fadeOut(tween(EXIT_MS)) +
                slideOutVertically(tween(EXIT_MS, easing = EmphasizedAccelerate)) { it / 4 }
        ) {
            WelcomeActions(
                onLogIn = { openPanel(AuthPanel.Login) },
                onCreateAccount = { openPanel(AuthPanel.Register) },
                modifier = Modifier.onSizeChanged { actionsHeightPx = it.height }
            )
        }

        // The panel rises over the space the welcome actions leave.
        AnimatedVisibility(
            visible = isOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(ENTER_MS, easing = EmphasizedDecelerate)) { it } +
                fadeIn(tween(ENTER_MS / 2)),
            exit = slideOutVertically(tween(EXIT_MS, easing = EmphasizedAccelerate)) { it } +
                fadeOut(tween(EXIT_MS, delayMillis = EXIT_MS / 2))
        ) {
            AuthPanelSurface(
                modifier = Modifier.onSizeChanged { panelHeightPx = it.height },
                panel = panel ?: lastPanel,
                maxHeight = maxHeight - CollapsedHeroHeight,
                onDismiss = ::closePanel,
                onSwitch = ::openPanel,
                onForgotPasswordClick = {
                    viewModel.clearError()
                    onForgotPasswordClick()
                },
                viewModel = viewModel
            )
        }
    }
}

/**
 * Light navigation-bar icons (gesture handle or back/home/recents) while the navy panel sits
 * behind them, dark ones over the light welcome screen. Restores dark icons when the screen
 * leaves, matching the rest of the light-themed app.
 */
@Composable
private fun NavigationBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, lightIcons) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        // "Appearance light" means dark icons, for a light background.
        controller?.isAppearanceLightNavigationBars = !lightIcons
        onDispose { controller?.isAppearanceLightNavigationBars = true }
    }
}

@Composable
private fun WelcomeCopy(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "DATAra",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "See when you'll reach your data limit, before you hit it.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Set a quota and DATAra forecasts when your mobile data use will reach it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Ways in from the welcome state. Google sign-in lives inside the two forms. */
@Composable
private fun WelcomeActions(
    onLogIn: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        DataraPrimaryButton(text = "Log in", onClick = onLogIn)
        Spacer(Modifier.height(12.dp))
        DataraSecondaryButton(text = "Create account", onClick = onCreateAccount)
    }
}

/** The form panel: dark navy ([AuthPanelTheme]) over the light welcome screen. */
@Composable
private fun AuthPanelSurface(
    panel: AuthPanel,
    maxHeight: Dp,
    onDismiss: () -> Unit,
    onSwitch: (AuthPanel) -> Unit,
    onForgotPasswordClick: () -> Unit,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) = AuthPanelTheme {
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = PanelShape,
        // In the panel scheme, background is the navy panel and surface the lighter field fill.
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .offset { IntOffset(0, dragOffset.roundToInt()) }
    ) {
        Column {
            // Drag handle: swipe down to close, or tap it (it is the accessible close button).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                        },
                        onDragStopped = { velocity ->
                            if (dragOffset > dismissThresholdPx || velocity > 1_500f) {
                                onDismiss()
                            } else {
                                animate(dragOffset, 0f) { value, _ -> dragOffset = value }
                            }
                        }
                    )
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = "Close" }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
            }

            AnimatedContent(
                targetState = panel,
                transitionSpec = {
                    // Register sits "to the right" of Login.
                    val forward = targetState == AuthPanel.Register
                    val direction = if (forward) 1 else -1
                    (fadeIn(tween(ENTER_MS / 2, delayMillis = 90)) +
                        slideInHorizontally(tween(ENTER_MS, easing = EmphasizedDecelerate)) { direction * it / 6 })
                        .togetherWith(
                            fadeOut(tween(90)) +
                                slideOutHorizontally(tween(EXIT_MS, easing = EmphasizedAccelerate)) { -direction * it / 6 }
                        )
                        .using(SizeTransform(clip = false) { _, _ -> tween(ENTER_MS, easing = EmphasizedDecelerate) })
                },
                label = "AuthPanelForm",
                modifier = Modifier.weight(1f, fill = false)
            ) { target ->
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                ) {
                    when (target) {
                        AuthPanel.Login -> LoginForm(
                            viewModel = viewModel,
                            onForgotPasswordClick = onForgotPasswordClick,
                            onSwitchToRegister = { onSwitch(AuthPanel.Register) }
                        )
                        AuthPanel.Register -> RegisterForm(
                            viewModel = viewModel,
                            onSwitchToLogin = { onSwitch(AuthPanel.Login) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
}

/**
 * "Prompt Action" line that switches between the two forms; a full-height touch target. A flow
 * row, so at large font sizes the action moves to its own line whole instead of being squeezed
 * and split mid-word.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwitchFormLink(prompt: String, action: String, onClick: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = action,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun LoginForm(
    viewModel: AuthViewModel,
    onForgotPasswordClick: () -> Unit,
    onSwitchToRegister: () -> Unit
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.login(identifier, password)
    }

    PanelHeader("Welcome back", "Log in to check your quota forecast.")

    // Email or username — AuthViewModel.login tells them apart by the '@'.
    DataraFieldLabel(label = "Email or username")
    Spacer(Modifier.height(8.dp))
    DataraTextField(
        value = identifier,
        onValueChange = {
            identifier = it
            viewModel.clearError()
        },
        placeholder = "you@example.com or username",
        leadingIcon = DataraIcons.Person,
        contentType = ContentType.Username + ContentType.EmailAddress,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )

    Spacer(Modifier.height(16.dp))

    DataraFieldLabel(
        label = "Password",
        trailingContent = {
            Text(
                text = "Forgot password?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onForgotPasswordClick)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
    )
    Spacer(Modifier.height(8.dp))
    DataraTextField(
        value = password,
        onValueChange = {
            password = it
            viewModel.clearError()
        },
        placeholder = "Your password",
        leadingIcon = DataraIcons.Lock,
        isPassword = true,
        contentType = ContentType.Password,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { submit() })
    )

    DataraErrorBanner((uiState as? AuthUiState.Error)?.message)

    Spacer(Modifier.height(24.dp))
    DataraPrimaryButton(
        text = "Log in",
        onClick = submit,
        enabled = identifier.isNotBlank() && password.isNotBlank(),
        isLoading = uiState is AuthUiState.Loading
    )

    Spacer(Modifier.height(20.dp))
    GoogleSignInSection(
        text = "Continue with Google",
        enabled = uiState !is AuthUiState.Loading,
        viewModel = viewModel
    )

    Spacer(Modifier.height(8.dp))
    SwitchFormLink("Don't have an account? ", "Create one", onSwitchToRegister)
}

@Composable
private fun RegisterForm(
    viewModel: AuthViewModel,
    onSwitchToLogin: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.register(name, email, password, confirmPassword)
    }

    PanelHeader("Create your account", "You'll choose a username next.")

    // Full name is sent as auth metadata so the on_auth_user_created trigger fills users.name.
    DataraFieldLabel(label = "Full name")
    Spacer(Modifier.height(8.dp))
    DataraTextField(
        value = name,
        onValueChange = {
            name = it
            viewModel.clearError()
        },
        placeholder = "Juan Dela Cruz",
        leadingIcon = DataraIcons.Person,
        contentType = ContentType.PersonFullName,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )

    Spacer(Modifier.height(16.dp))

    DataraFieldLabel(label = "Email address")
    Spacer(Modifier.height(8.dp))
    DataraTextField(
        value = email,
        onValueChange = {
            email = it
            viewModel.clearError()
        },
        placeholder = "you@example.com",
        leadingIcon = DataraIcons.Email,
        contentType = ContentType.EmailAddress,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )

    Spacer(Modifier.height(16.dp))

    DataraFieldLabel(label = "Password")
    Spacer(Modifier.height(8.dp))
    DataraTextField(
        value = password,
        onValueChange = {
            password = it
            viewModel.clearError()
        },
        placeholder = "At least 8 characters",
        leadingIcon = DataraIcons.Lock,
        isPassword = true,
        contentType = ContentType.NewPassword,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )
    PasswordStrengthMeter(AuthValidator.passwordStrength(password))

    Spacer(Modifier.height(16.dp))

    DataraFieldLabel(label = "Confirm password")
    Spacer(Modifier.height(8.dp))
    DataraTextField(
        value = confirmPassword,
        onValueChange = {
            confirmPassword = it
            viewModel.clearError()
        },
        placeholder = "Re-enter your password",
        leadingIcon = DataraIcons.Lock,
        isPassword = true,
        isError = confirmPassword.isNotEmpty() && confirmPassword != password,
        contentType = ContentType.NewPassword,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { submit() })
    )

    DataraErrorBanner((uiState as? AuthUiState.Error)?.message)

    Spacer(Modifier.height(24.dp))
    DataraPrimaryButton(
        text = "Create account",
        onClick = submit,
        enabled = name.isNotBlank() && email.isNotBlank() &&
            password.isNotBlank() && confirmPassword.isNotBlank(),
        isLoading = uiState is AuthUiState.Loading
    )

    Spacer(Modifier.height(20.dp))
    GoogleSignInSection(
        text = "Sign up with Google",
        enabled = uiState !is AuthUiState.Loading,
        viewModel = viewModel
    )

    Spacer(Modifier.height(8.dp))
    SwitchFormLink("Already have an account? ", "Log in", onSwitchToLogin)
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun WelcomePreview() {
    DATAraTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                DataraLogo(size = 112.dp)
                WelcomeCopy()
            }
        }
    }
}
