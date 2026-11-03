package com.capstone.datara.ui.auth.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.theme.SystemBarIcons
import com.capstone.datara.ui.theme.WelcomeTheme

/**
 * Full-screen layout for the stand-alone auth screens (Forgot password, Choose a username):
 * brand navy background with the back arrow and logo, and a floating white card that opens with
 * [title] and [subtitle] followed by the form. It matches the navy welcome screen, while the
 * card keeps the app's light theme.
 *
 * The content is centered when it fits and scrolls when it doesn't (keyboard open, large font,
 * small screen). [footer] sits under the card on navy, for links such as "Log in".
 */
@Composable
fun AuthCardScaffold(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit = {},
    card: @Composable ColumnScope.() -> Unit
) {
    // The card's fields and buttons stay in the app's light scheme.
    val formColors = MaterialTheme.colorScheme
    WelcomeTheme {
        SystemBarIcons(darkStatusIcons = false, darkNavigationIcons = false)
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = DataraIcons.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    DataraLogo(size = 88.dp, contentDescription = null)
                    Spacer(Modifier.height(16.dp))

                    MaterialTheme(
                        colorScheme = formColors,
                        typography = MaterialTheme.typography,
                        shapes = MaterialTheme.shapes
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .animateContentSize(tween(300))
                                    .padding(20.dp)
                            ) {
                                // The heading opens the card, like "Welcome back" in the Login
                                // panel; it cross-fades when a multi-step screen changes step.
                                AnimatedContent(
                                    targetState = title to subtitle,
                                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                                    label = "AuthCardHeading"
                                ) { (heading, body) ->
                                    Column {
                                        Text(
                                            text = heading,
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.semantics { heading() }
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = body,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                                card()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    footer()
                }
            }
        }
    }
}

/** "Prompt Action" link for the navy area under the card (e.g. "Remember your password? Log in"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthFooterLink(prompt: String, action: String, onClick: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onClick) {
            Text(
                text = action,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
