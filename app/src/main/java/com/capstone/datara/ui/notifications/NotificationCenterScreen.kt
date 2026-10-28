package com.capstone.datara.ui.notifications

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.R
import com.capstone.datara.ui.components.DataraNoticeCard
import com.capstone.datara.ui.components.DataraPageHeader
import com.capstone.datara.ui.components.DataraStatusBadge
import com.capstone.datara.ui.components.DataraSurfaceCard
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraBlueSoft
import com.capstone.datara.ui.theme.DataraError
import com.capstone.datara.ui.theme.DataraInputBorder
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraWarning

enum class NotificationCategory {
    PET,
    MILESTONE,
    PROMO
}

enum class NotificationPriority {
    CRITICAL,
    WARNING,
    INFORMATION
}

data class NotificationItemUiModel(
    val id: String,
    val category: NotificationCategory,
    val priority: NotificationPriority,
    val title: String,
    val message: String,
    val timestamp: String,
    val isRead: Boolean
)

sealed interface NotificationCenterUiState {
    data object Loading : NotificationCenterUiState
    data object Empty : NotificationCenterUiState
    data class Ready(val notifications: List<NotificationItemUiModel>) : NotificationCenterUiState
    data class Error(val message: String) : NotificationCenterUiState
}

@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialState: NotificationCenterUiState = NotificationCenterUiState.Empty
) {
    var localNotifications by remember(initialState) {
        mutableStateOf((initialState as? NotificationCenterUiState.Ready)?.notifications.orEmpty())
    }
    val displayedState = when (initialState) {
        is NotificationCenterUiState.Ready -> {
            if (localNotifications.isEmpty()) NotificationCenterUiState.Empty
            else NotificationCenterUiState.Ready(localNotifications)
        }
        else -> initialState
    }

    NotificationCenterContent(
        state = displayedState,
        onBack = onBack,
        onMarkRead = { id ->
            localNotifications = localNotifications.map { item ->
                if (item.id == id) item.copy(isRead = true) else item
            }
        },
        onMarkAllRead = {
            localNotifications = localNotifications.map { it.copy(isRead = true) }
        },
        modifier = modifier
    )
}

@Composable
private fun NotificationCenterContent(
    state: NotificationCenterUiState,
    onBack: () -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notifications = (state as? NotificationCenterUiState.Ready)?.notifications.orEmpty()
    val unreadCount = notifications.count { !it.isRead }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                    DataraPageHeader(
                        eyebrow = stringResource(R.string.notifications_eyebrow),
                        title = stringResource(R.string.notifications_title),
                        subtitle = stringResource(R.string.notifications_subtitle),
                        onBack = onBack,
                        backContentDescription = stringResource(R.string.action_back),
                        trailingContent = {
                            DataraStatusBadge(
                                text = if (unreadCount > 0) {
                                    stringResource(R.string.notifications_unread_format, unreadCount)
                                } else {
                                    stringResource(R.string.notifications_all_read)
                                },
                                color = if (unreadCount > 0) DataraWarning else DataraBlueSoft
                            )
                        }
                    )
                }
            }

            item {
                DataraNoticeCard(
                    title = stringResource(R.string.notifications_frontend_notice_title),
                    message = stringResource(R.string.notifications_frontend_notice_body),
                    accent = DataraBlueSoft,
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }

            when (state) {
                NotificationCenterUiState.Loading -> item {
                    DataraSurfaceCard(modifier = Modifier.widthIn(max = 760.dp)) {
                        CircularProgressIndicator(color = DataraNeonBlue, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.notifications_loading_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                NotificationCenterUiState.Empty -> item {
                    NotificationEmptyState()
                }
                is NotificationCenterUiState.Error -> item {
                    DataraNoticeCard(
                        title = stringResource(R.string.notifications_error_title),
                        message = state.message,
                        accent = DataraError,
                        modifier = Modifier.widthIn(max = 760.dp)
                    )
                }
                is NotificationCenterUiState.Ready -> {
                    if (unreadCount > 0) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onMarkAllRead) {
                                    Text(stringResource(R.string.notifications_mark_all_read), color = DataraBlueSoft)
                                }
                            }
                        }
                    }
                    items(state.notifications, key = { it.id }) { notification ->
                        NotificationCard(notification = notification, onMarkRead = onMarkRead)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationEmptyState() {
    DataraSurfaceCard(modifier = Modifier.widthIn(max = 760.dp)) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(DataraNeonBlue.copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("0", color = DataraBlueSoft, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.notifications_empty_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(5.dp))
        Text(stringResource(R.string.notifications_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationItemUiModel,
    onMarkRead: (String) -> Unit
) {
    val accent = when (notification.priority) {
        NotificationPriority.CRITICAL -> DataraError
        NotificationPriority.WARNING -> DataraWarning
        NotificationPriority.INFORMATION -> DataraBlueSoft
    }
    val priorityLabel = when (notification.priority) {
        NotificationPriority.CRITICAL -> stringResource(R.string.notification_priority_critical)
        NotificationPriority.WARNING -> stringResource(R.string.notification_priority_warning)
        NotificationPriority.INFORMATION -> stringResource(R.string.notification_priority_information)
    }
    val categoryLabel = when (notification.category) {
        NotificationCategory.PET -> stringResource(R.string.notification_type_pet)
        NotificationCategory.MILESTONE -> stringResource(R.string.notification_type_milestone)
        NotificationCategory.PROMO -> stringResource(R.string.notification_type_promo)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .background(
                if (notification.isRead) MaterialTheme.colorScheme.surface else accent.copy(alpha = 0.08f),
                RoundedCornerShape(20.dp)
            )
            .clickable(
                enabled = !notification.isRead,
                role = Role.Button,
                onClick = { onMarkRead(notification.id) }
            )
            .semantics { role = Role.Button }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DataraStatusBadge(priorityLabel, accent)
            Spacer(Modifier.width(8.dp))
            Text(categoryLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(notification.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text(notification.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(notification.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!notification.isRead) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.notifications_mark_read),
                style = MaterialTheme.typography.labelLarge,
                color = DataraBlueSoft
            )
        }
    }
}

@Composable
private fun previewNotificationItems(): List<NotificationItemUiModel> = listOf(
    NotificationItemUiModel(
        id = "pet",
        category = NotificationCategory.PET,
        priority = NotificationPriority.CRITICAL,
        title = stringResource(R.string.notification_preview_pet_title),
        message = stringResource(R.string.notification_preview_pet_message),
        timestamp = stringResource(R.string.notification_preview_time_now),
        isRead = false
    ),
    NotificationItemUiModel(
        id = "milestone-80",
        category = NotificationCategory.MILESTONE,
        priority = NotificationPriority.WARNING,
        title = stringResource(R.string.notification_preview_80_title),
        message = stringResource(R.string.notification_preview_80_message),
        timestamp = stringResource(R.string.notification_preview_time_hour),
        isRead = false
    ),
    NotificationItemUiModel(
        id = "milestone-90",
        category = NotificationCategory.MILESTONE,
        priority = NotificationPriority.CRITICAL,
        title = stringResource(R.string.notification_preview_90_title),
        message = stringResource(R.string.notification_preview_90_message),
        timestamp = stringResource(R.string.notification_preview_time_two_hours),
        isRead = true
    ),
    NotificationItemUiModel(
        id = "milestone-50",
        category = NotificationCategory.MILESTONE,
        priority = NotificationPriority.INFORMATION,
        title = stringResource(R.string.notification_preview_50_title),
        message = stringResource(R.string.notification_preview_50_message),
        timestamp = stringResource(R.string.notification_preview_time_two_days),
        isRead = true
    ),
    NotificationItemUiModel(
        id = "promo",
        category = NotificationCategory.PROMO,
        priority = NotificationPriority.INFORMATION,
        title = stringResource(R.string.notification_preview_promo_title),
        message = stringResource(R.string.notification_preview_promo_message),
        timestamp = stringResource(R.string.notification_preview_time_day),
        isRead = true
    )
)

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 900)
@Composable
private fun NotificationCenterEmptyPreview() {
    DATAraTheme {
        NotificationCenterScreen(onBack = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 1000)
@Composable
private fun NotificationCenterReadyPreview() {
    DATAraTheme {
        NotificationCenterScreen(
            onBack = {},
            initialState = NotificationCenterUiState.Ready(previewNotificationItems())
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 760)
@Composable
private fun NotificationCenterLoadingPreview() {
    DATAraTheme {
        NotificationCenterScreen(
            onBack = {},
            initialState = NotificationCenterUiState.Loading
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 760)
@Composable
private fun NotificationCenterErrorPreview() {
    DATAraTheme {
        NotificationCenterScreen(
            onBack = {},
            initialState = NotificationCenterUiState.Error(
                stringResource(R.string.notifications_frontend_notice_body)
            )
        )
    }
}
