package com.capstone.datara.ui.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.quota.QuotaDetailsModel
import com.capstone.datara.ui.quota.QuotaDetailsScreen
import com.capstone.datara.ui.theme.QuotaTheme
import com.capstone.datara.ui.components.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun alertTime(time: Long): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

/**
 * The alert inbox (Room `notifications`). Tapping an alert marks it read and shows it; its button
 * opens the cycle ([onOpenCycle]). A past cycle comes back as [openedCycle] and is shown read-only.
 */
@Composable
fun NotificationsScreen(state: NotificationCenterState, onBack: () -> Unit, onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit, onOpenCycle: (String) -> Unit = {}, openedCycle: QuotaDetailsModel? = null,
    onCloseCycle: () -> Unit = {}, modifier: Modifier = Modifier) {
    QuotaTheme { NotificationsContent(state, onBack, onMarkRead, onMarkAllRead, onOpenCycle, openedCycle, onCloseCycle, modifier) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsContent(state: NotificationCenterState, onBack: () -> Unit, onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit, onOpenCycle: (String) -> Unit, openedCycle: QuotaDetailsModel?, onCloseCycle: () -> Unit,
    modifier: Modifier) {
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    BackHandler(openedCycle != null) { onCloseCycle() }
    if (openedCycle != null) {
        QuotaDetailsScreen(openedCycle, onBack = onCloseCycle, onEndCycle = {}, onCreateQuota = {}, readOnly = true)
        return
    }
    val visible = state.alerts.filter { !unreadOnly || !it.isRead }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val today = LocalDate.now()
    Scaffold(modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection), containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { DataraCollapsingAppBar("Notifications", scrollBehavior, onBack) }) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (state.isLoading) "Loading…" else if (state.alerts.isEmpty()) "A quiet moment." else if (state.unreadCount == 0) "You’re all caught up." else "${state.unreadCount} unread ${if (state.unreadCount == 1) "update" else "updates"}",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically) {
                        listOf(false to "All", true to "Unread").forEach { (option, label) ->
                            FilterChip(selected = unreadOnly == option, onClick = { unreadOnly = option }, label = { Text(label) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer))
                        }
                        TextButton(onClick = { onMarkAllRead(); scope.launch { snackbar.showSnackbar("All notifications marked as read") } },
                            enabled = state.unreadCount > 0) { Text("Mark all as read") }
                    }
                }
            }
            if (state.isLoading) item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading notifications" })
                }
            }
            if (visible.isEmpty() && !state.isLoading) item {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TonalIcon(DataraIcons.Bell, positive = true)
                        Text(if (unreadOnly && state.alerts.isNotEmpty()) "No unread notifications" else "No notifications yet", style = MaterialTheme.typography.headlineMedium)
                        Text(if (unreadOnly && state.alerts.isNotEmpty()) "Your read updates are still available under All."
                            else "Alerts about your quota will show up here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (unreadOnly) TextButton(onClick = { unreadOnly = false }) { Text("View all notifications") }
                    }
                }
            }
            // Today / Earlier, with headers that stay pinned while their group scrolls
            visible.groupBy { alertDay(it) == today }.toSortedMap(compareByDescending { it }).forEach { (isToday, alerts) ->
                stickyHeader(key = "group-$isToday") {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isToday) "Today" else "Earlier", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() })
                    }
                }
                items(alerts, key = { it.id }) { alert -> AlertRow(alert, showDate = !isToday) { onMarkRead(alert.id); selectedId = alert.id } }
            }
        }
    }
    state.alerts.firstOrNull { it.id == selectedId }?.let { alert ->
        ModalBottomSheet(onDismissRequest = { selectedId = null }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                AlertIcon(alert)
                Text(alert.title, style = MaterialTheme.typography.headlineMedium)
                Text(alertTime(alert.receivedAtMillis), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(alert.message, style = MaterialTheme.typography.bodyLarge)
                Text("The figures are from when this alert was sent. Quota details show the latest totals. DATAra never blocks your data.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                alert.cycleId?.let { cycleId -> DataraActionButton("View quota details", { selectedId = null; onOpenCycle(cycleId) }) }
                TextButton(onClick = { selectedId = null }) { Text("Close") }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

private fun alertDay(alert: AlertItem): LocalDate = Instant.ofEpochMilli(alert.receivedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()

/** Milestones in their quota status colour; forecast alerts in the neutral navy tint. */
@Composable
private fun AlertIcon(alert: AlertItem) {
    val icon = if (alert.category == AlertCategory.FORECAST) DataraIcons.Clock else DataraIcons.Bell
    alert.tone?.let { ToneIcon(icon, it) } ?: TonalIcon(icon)
}

@Composable
private fun AlertRow(alert: AlertItem, showDate: Boolean, onClick: () -> Unit) {
    val container = if (alert.isRead) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.primaryContainer
    Card(onClick = onClick, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (alert.isRead) "Read" else "Unread" }) {
        ListItem(leadingContent = { AlertIcon(alert) },
            headlineContent = { Text(alert.title, style = MaterialTheme.typography.titleMedium,
                fontWeight = if (alert.isRead) FontWeight.Medium else FontWeight.Bold) },
            supportingContent = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(alert.message, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(alertMeta(alert, showDate = showDate), style = MaterialTheme.typography.labelSmall)
            } },
            trailingContent = if (alert.isRead) null else ({ Badge(containerColor = MaterialTheme.colorScheme.primary) }),
            colors = ListItemDefaults.colors(containerColor = container),
            modifier = Modifier.padding(vertical = 10.dp))
    }
}

/** The small line under an alert: its kind and when it arrived. Read state is shown by weight and colour, and announced. */
internal fun alertMeta(alert: AlertItem, zone: ZoneId = ZoneId.systemDefault(), showDate: Boolean = false): String =
    "${alert.category.label} · ${(if (showDate) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        else DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)).format(Instant.ofEpochMilli(alert.receivedAtMillis).atZone(zone))}"

@Composable
internal fun NotificationBell(unreadCount: Int, onClick: () -> Unit) {
    // Plain icon button: no tinted container behind the bell
    IconButton(onClick = onClick, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clearAndSetSemantics {
        contentDescription = "Notifications, $unreadCount unread"
        role = Role.Button
        onClick(label = "Open notifications") { onClick(); true }
    }) {
        BadgedBox(badge = { if (unreadCount > 0) Badge(containerColor = MaterialTheme.colorScheme.primary) {
            Text(unreadCount.toString(), color = MaterialTheme.colorScheme.onPrimary)
        } }) {
            Icon(DataraIcons.Bell, null, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Preview(name = "Notifications large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Preview(name = "Notifications landscape", widthDp = 800, heightDp = 360)
@Composable
private fun NotificationsPreview() {
    NotificationsScreen(NotificationCenterState(listOf(AlertItem("example", "90% of your quota used",
        "9 GB of 10 GB used on “Everyday”. Only 1 GB left. Nothing is blocked.", AlertCategory.QUOTA, 1_793_232_000_000, false, "c1"))), {}, {}, {})
}
