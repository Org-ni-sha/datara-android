package com.capstone.datara.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.graphicsLayer
import com.capstone.datara.ui.auth.components.SplashMatchedLogoSize
import com.capstone.datara.ui.theme.SystemBarIcons
import com.capstone.datara.ui.theme.WelcomeTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Which form the auth panel shows. Null on [AuthEntryScreen] means the welcome state. */
enum class AuthPanel { Login, Register }

// Material 3 emphasized easing: things arriving decelerate, things leaving accelerate.
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val ENTER_MS = 400
private const val EXIT_MS = 250
/** Splash-to-welcome logo move, and the pause before it so the handoff registers. */
private const val INTRO_MS = 700
private const val INTRO_HOLD_MS = 150L

private val PanelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

private val WelcomeLogoSize = 112.dp
/** With a form open the logo box takes this share of the gap above the panel, within bounds. */
private const val OPEN_LOGO_GAP_FRACTION = 0.6f
private val OpenLogoMinSize = 48.dp
private val OpenLogoMaxSize = 104.dp

/** Height the collapsed hero (logo only) keeps above the open panel. */
private val CollapsedHeroHeight = 104.dp

/**
 * First screen for signed-out users, on brand navy. The welcome state shows the logo, what the
 * app does, and the ways in. Choosing Log in or Create account shrinks the logo to the top and
 * raises a light panel holding the form; switching between the two forms morphs the panel in
 * place. Back, the panel handle, or dragging the handle down returns to the welcome state.
 *
 * With [playIntro] (first launch, straight after the splash), the first frame matches the splash:
 * the logo at [SplashMatchedLogoSize] in the window center and nothing else visible. Once the
 * layout is measured, the logo shrinks and glides to its welcome position while the copy and the
 * buttons fade in after it.
 *
 * This is an in-screen panel rather than a ModalBottomSheet: forms in a modal sheet fight the
 * keyboard, and Forgot password must navigate to a full screen and come back to the same panel.
 */
@Composable
fun AuthEntryScreen(
    onSignedIn: (needsUsername: Boolean) -> Unit,
    onForgotPasswordClick: () -> Unit,
    playIntro: Boolean = false,
    viewModel: AuthViewModel = hiltViewModel()
) {
    // The panel's forms stay in the app's light scheme; everything else here is navy.
    val formColors = MaterialTheme.colorScheme
    WelcomeTheme {
        AuthEntryContent(onSignedIn, onForgotPasswordClick, playIntro, formColors, viewModel)
    }
}

@Composable
private fun AuthEntryContent(
    onSignedIn: (needsUsername: Boolean) -> Unit,
    onForgotPasswordClick: () -> Unit,
    playIntro: Boolean,
    formColors: ColorScheme,
    viewModel: AuthViewModel
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
    // closes and leave it drawn in its focused state.
    val focusManager = LocalFocusManager.current
    // Duration of the next hero (logo) move: the intro, opening or closing the panel.
    var heroDurationMs by remember { mutableIntStateOf(EXIT_MS) }

    fun openPanel(target: AuthPanel) {
        focusManager.clearFocus()
        viewModel.clearError()
        heroDurationMs = ENTER_MS
        lastPanel = target
        panel = target
    }

    fun closePanel() {
        focusManager.clearFocus()
        viewModel.clearError()
        heroDurationMs = EXIT_MS
        panel = null
    }

    BackHandler(enabled = panel != null) { closePanel() }

    val isOpen = panel != null
    // White status icons over navy always; navigation icons go dark over the open light panel.
    SystemBarIcons(darkStatusIcons = false, darkNavigationIcons = isOpen)

    // Measured sizes, used to center the hero in whatever space the bottom content leaves.
    val density = LocalDensity.current
    var copyHeightPx by remember { mutableIntStateOf(0) }
    var actionsHeightPx by remember { mutableIntStateOf(0) }
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val measured = copyHeightPx > 0 && actionsHeightPx > 0

    // Until the first real measurement lands, jump to positions instead of animating, so the
    // hero doesn't visibly slide into place. The intro then starts from the splash position.
    var layoutSettled by remember { mutableStateOf(false) }
    var introPending by rememberSaveable { mutableStateOf(playIntro) }
    LaunchedEffect(measured) {
        if (!measured) return@LaunchedEffect
        withFrameNanos { }
        layoutSettled = true
        if (introPending) {
            delay(INTRO_HOLD_MS)
            heroDurationMs = INTRO_MS
            introPending = false
        }
    }

    val heroSpec: AnimationSpec<Dp> =
        if (layoutSettled) tween(heroDurationMs, easing = EmphasizedDecelerate) else snap()
    // Copy follows the logo; the buttons come last.
    val copyAlpha by animateFloatAsState(
        targetValue = if (introPending) 0f else 1f,
        animationSpec = tween(450, delayMillis = INTRO_MS / 3),
        label = "IntroCopyAlpha"
    )
    val actionsAlpha by animateFloatAsState(
        targetValue = if (introPending) 0f else 1f,
        animationSpec = tween(450, delayMillis = INTRO_MS / 2),
        label = "IntroActionsAlpha"
    )

    val statusBarTopPx = WindowInsets.statusBars.getTop(density)

    // Only the status bar is padded here. The panel runs behind the navigation bar (gesture
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
        // With a form open, the logo fills part of the navy gap above the panel: large over the
        // short Login form, compact over the taller Create account form (and with the keyboard).
        val openLogoSize = with(density) {
            val gap = (constraints.maxHeight - panelHeightPx).toFloat().toDp()
            (gap * OPEN_LOGO_GAP_FRACTION - 24.dp).coerceIn(OpenLogoMinSize, OpenLogoMaxSize)
        }
        val logoSize by animateDpAsState(
            targetValue = when {
                isOpen -> openLogoSize
                introPending -> SplashMatchedLogoSize
                else -> WelcomeLogoSize
            },
            animationSpec = heroSpec,
            label = "LogoSize"
        )

        val heroTopTarget = with(density) {
            val available = constraints.maxHeight.toFloat()
            when {
                isOpen -> {
                    // Logo (plus DataraLogo's 24dp clear space) centered above the panel.
                    val logoBox = (openLogoSize + 24.dp).toPx()
                    ((available - panelHeightPx - logoBox) / 2).toDp().coerceAtLeast(4.dp)
                }
                introPending -> {
                    // Window center, like the splash: this box starts below the status bar.
                    val logoBox = (SplashMatchedLogoSize + 24.dp).toPx()
                    ((available - statusBarTopPx - logoBox) / 2).toDp()
                }
                else -> {
                    val heroHeight = (WelcomeLogoSize + 24.dp).toPx() + copyHeightPx
                    ((available - actionsHeightPx - heroHeight) / 2).toDp().coerceAtLeast(16.dp)
                }
            }
        }
        val heroTop by animateDpAsState(heroTopTarget, heroSpec, label = "HeroTop")

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
                    if (!isOpen && !introPending && actionsHeightPx > 0) {
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
                WelcomeCopy(
                    Modifier
                        .onSizeChanged { copyHeightPx = it.height }
                        .graphicsLayer { alpha = copyAlpha }
                )
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
                modifier = Modifier
                    .onSizeChanged { actionsHeightPx = it.height }
                    .graphicsLayer {
                        alpha = actionsAlpha
                        translationY = (1f - actionsAlpha) * 24.dp.toPx()
                    }
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
            MaterialTheme(
                colorScheme = formColors,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
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
            text = "See when you'll reach your data limit.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Set a quota. DATAra forecasts the rest from your usage.",
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

/** The form panel: a white sheet over the off-white welcome screen. */
@Composable
private fun AuthPanelSurface(
    panel: AuthPanel,
    maxHeight: Dp,
    onDismiss: () -> Unit,
    onSwitch: (AuthPanel) -> Unit,
    onForgotPasswordClick: () -> Unit,
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
            // 48dp touch target around the short link text (accessibility minimum)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onForgotPasswordClick)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Forgot password?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.register(username, email, password)
    }
    // Format rules shown as the user types; availability is checked right after sign-up
    val usernameHint = username.takeIf { it.isNotBlank() }?.let(AuthValidator::usernameError)

    PanelHeader("Create your account", "Sign in later with your username or email.")

    DataraFieldLabel(label = "Username")
    Spacer(Modifier.height(8.dp))
    DataraTextField(
        value = username,
        onValueChange = {
            username = it
            viewModel.clearError()
        },
        placeholder = "juan_dc",
        leadingIcon = DataraIcons.Person,
        contentType = ContentType.NewUsername,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )
    Spacer(Modifier.height(6.dp))
    Text(usernameHint ?: "3–20 letters, numbers or underscores.", style = MaterialTheme.typography.bodySmall,
        color = if (usernameHint != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)

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
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { submit() })
    )
    // One password field: Show/Hide and the strength meter catch typos without a confirm field,
    // and Forgot password recovers a mistyped one.
    PasswordStrengthMeter(AuthValidator.passwordStrength(password))

    DataraErrorBanner((uiState as? AuthUiState.Error)?.message)

    Spacer(Modifier.height(24.dp))
    DataraPrimaryButton(
        text = "Create account",
        onClick = submit,
        enabled = username.isNotBlank() && usernameHint == null && email.isNotBlank() && password.isNotBlank(),
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
