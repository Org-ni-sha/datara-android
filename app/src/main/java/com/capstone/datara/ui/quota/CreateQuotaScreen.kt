package com.capstone.datara.ui.quota

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.components.*
import com.capstone.datara.ui.theme.QuotaTheme

@Composable
fun CreateQuotaScreen(onBack: () -> Unit, onCreateQuota: (Long, String) -> Unit, modifier: Modifier = Modifier) {
    QuotaTheme { CreateQuotaContent(onBack, onCreateQuota, modifier) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CreateQuotaContent(onBack: () -> Unit, onCreateQuota: (Long, String) -> Unit, modifier: Modifier) {
    var amount by rememberSaveable { mutableStateOf("") }
    var unitName by rememberSaveable { mutableStateOf(QuotaUnit.GB.name) }
    var name by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val unit = QuotaUnit.valueOf(unitName)
    val focus = LocalFocusManager.current
    val submit = {
        when (val result = QuotaInput.parseAmount(amount, unit)) {
            is QuotaAmountResult.Invalid -> error = result.message
            is QuotaAmountResult.Valid -> { focus.clearFocus(); onCreateQuota(result.bytes, name) }
        }
    }
    Scaffold(modifier = modifier.imePadding(), containerColor = MaterialTheme.colorScheme.background,
        topBar = { DataraAppBar("Create quota", onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DataraActionButton("Start tracking", { submit() })
                    Text("Sample cycle - Tracking is not connected yet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { TonalIcon(DataraIcons.Data); SampleLabel("New cycle") }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set your tracking amount", style = MaterialTheme.typography.headlineMedium)
                Text("Choose an amount to track. We’ll help you see when you’re likely to reach it.", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(amount, { amount = it; error = null }, label = { Text("Data amount") }, placeholder = { Text("10") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, isError = error != null,
                        textStyle = MaterialTheme.typography.displayLarge,
                        suffix = { Text(unit.name, style = MaterialTheme.typography.titleLarge) },
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        supportingText = { Text(error ?: "At least 1 MB | 1 GB = 1,000 MB") }, shape = MaterialTheme.shapes.medium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        QuotaUnit.entries.forEachIndexed { index, option ->
                            SegmentedButton(selected = unit == option, onClick = { unitName = option.name; error = null },
                                shape = SegmentedButtonDefaults.itemShape(index, QuotaUnit.entries.size)) { Text(option.name) }
                        }
                    }
                    Text("Quick pick", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1", "5", "10").forEach { value ->
                            SuggestionChip(onClick = { amount = value; unitName = QuotaUnit.GB.name; error = null }, label = { Text("$value GB") })
                        }
                    }
                }
            }
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Quota name (optional)") }, placeholder = { Text("Everyday data") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }),
                supportingText = { Text("Give this cycle a name you’ll recognize.") })
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Text("What counts?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
                ListItem(leadingContent = { TonalIcon(DataraIcons.Data) }, headlineContent = { Text("Mobile data counts") },
                    supportingContent = { Text("Uploads, downloads and promo freebies.") })
                ListItem(leadingContent = { TonalIcon(DataraIcons.Shield, positive = true) }, headlineContent = { Text("No internet restrictions") },
                    supportingContent = { Text("Wi-Fi is excluded. Quotas never block access.") })
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Preview(name = "Create quota large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun CreateQuotaPreview() { CreateQuotaScreen({}, { _, _ -> }) }
