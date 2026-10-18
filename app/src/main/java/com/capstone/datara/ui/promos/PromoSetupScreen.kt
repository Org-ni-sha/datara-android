package com.capstone.datara.ui.promos

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.R
import com.capstone.datara.ui.components.DataraNoticeCard
import com.capstone.datara.ui.components.DataraPageHeader
import com.capstone.datara.ui.components.DataraSectionTitle
import com.capstone.datara.ui.components.DataraStatusBadge
import com.capstone.datara.ui.components.DataraSurfaceCard
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraBlueSoft
import com.capstone.datara.ui.theme.DataraCardElevated
import com.capstone.datara.ui.theme.DataraDarkBg
import com.capstone.datara.ui.theme.DataraDivider
import com.capstone.datara.ui.theme.DataraError
import com.capstone.datara.ui.theme.DataraInputBorder
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraNeonGreen
import com.capstone.datara.ui.theme.DataraTextPrimary
import com.capstone.datara.ui.theme.DataraTextSecondary
import com.capstone.datara.ui.theme.DataraWarning
import java.util.Locale

enum class CarrierUiModel(val displayName: String) {
    SMART("Smart"),
    GLOBE("Globe"),
    DITO("DITO")
}

enum class PromoCategory {
    ALL,
    DATA,
    COMBO
}

data class PromoCatalogItemUiModel(
    val id: String,
    val carrier: CarrierUiModel,
    val name: String,
    val dataGb: Double,
    val validityDays: Int,
    val price: Double,
    val category: PromoCategory
)

data class FrontendPromoSelection(
    val name: String,
    val carrier: String,
    val totalGb: Double,
    val remainingGb: Double,
    val validityDays: Int,
    val expiryLabel: String,
    val isDesignPreview: Boolean = true
)

sealed interface PromoSetupUiState {
    data object Loading : PromoSetupUiState
    data object CarrierUnavailable : PromoSetupUiState
    data class Ready(
        val carrier: CarrierUiModel,
        val catalog: List<PromoCatalogItemUiModel> = SamplePromoCatalog,
        val isDesignPreview: Boolean = true
    ) : PromoSetupUiState
    data class Error(val message: String) : PromoSetupUiState
}

@Composable
fun PromoSetupScreen(
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onPromoSaved: (FrontendPromoSelection) -> Unit,
    modifier: Modifier = Modifier,
    initialState: PromoSetupUiState = PromoSetupUiState.CarrierUnavailable
) {
    var previewCarrierName by rememberSaveable { mutableStateOf<String?>(null) }
    val displayedState = previewCarrierName?.let {
        PromoSetupUiState.Ready(carrier = CarrierUiModel.valueOf(it))
    } ?: initialState

    PromoSetupContent(
        state = displayedState,
        onBack = onBack,
        onSkip = onSkip,
        onPreviewCatalog = { previewCarrierName = CarrierUiModel.GLOBE.name },
        onPromoSaved = onPromoSaved,
        modifier = modifier
    )
}

@Composable
private fun PromoSetupContent(
    state: PromoSetupUiState,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onPreviewCatalog: () -> Unit,
    onPromoSaved: (FrontendPromoSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var categoryName by rememberSaveable { mutableStateOf(PromoCategory.ALL.name) }
    var selectedPromoId by rememberSaveable { mutableStateOf<String?>(null) }
    var showManualDialog by rememberSaveable { mutableStateOf(false) }
    val selectedCategory = PromoCategory.valueOf(categoryName)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DataraDarkBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 18.dp,
                bottom = 28.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().widthIn(max = 840.dp)) {
                    DataraPageHeader(
                        eyebrow = stringResource(R.string.promo_setup_eyebrow),
                        title = stringResource(R.string.promo_setup_title),
                        subtitle = stringResource(R.string.promo_setup_subtitle),
                        onBack = onBack,
                        backContentDescription = stringResource(R.string.action_back),
                        trailingContent = {
                            DataraStatusBadge(
                                text = when (state) {
                                    PromoSetupUiState.Loading -> stringResource(R.string.status_loading)
                                    PromoSetupUiState.CarrierUnavailable, is PromoSetupUiState.Error -> stringResource(R.string.status_unavailable)
                                    is PromoSetupUiState.Ready -> stringResource(R.string.status_design_preview)
                                },
                                color = when (state) {
                                    is PromoSetupUiState.Ready -> DataraBlueSoft
                                    else -> DataraWarning
                                }
                            )
                        }
                    )
                }
            }

            when (state) {
                PromoSetupUiState.Loading -> item {
                    PromoLoadingState()
                }
                PromoSetupUiState.CarrierUnavailable -> item {
                    PromoCarrierUnavailableState(
                        onPreviewCatalog = onPreviewCatalog,
                        onSkip = onSkip
                    )
                }
                is PromoSetupUiState.Error -> item {
                    DataraNoticeCard(
                        title = stringResource(R.string.promo_error_title),
                        message = state.message,
                        accent = DataraError,
                        modifier = Modifier.widthIn(max = 840.dp)
                    )
                }
                is PromoSetupUiState.Ready -> {
                    val carrierCatalog = state.catalog.filter { it.carrier == state.carrier }
                    val visiblePromos = carrierCatalog.filter { promo ->
                        val matchesSearch = promo.name.contains(searchQuery, ignoreCase = true)
                        val matchesCategory = selectedCategory == PromoCategory.ALL || promo.category == selectedCategory
                        matchesSearch && matchesCategory
                    }
                    val selectedPromo = carrierCatalog.firstOrNull { it.id == selectedPromoId }

                    item {
                        CarrierPreviewCard(state.carrier)
                    }
                    item {
                        PromoCatalogControls(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            selectedCategory = selectedCategory,
                            onCategorySelected = { categoryName = it.name },
                            carrier = state.carrier,
                            onManualClick = { showManualDialog = true }
                        )
                    }

                    if (visiblePromos.isEmpty()) {
                        item { PromoCatalogEmptyState(onManualClick = { showManualDialog = true }) }
                    } else {
                        items(visiblePromos, key = { it.id }) { promo ->
                            PromoCatalogCard(
                                promo = promo,
                                selected = promo.id == selectedPromoId,
                                onClick = { selectedPromoId = promo.id }
                            )
                        }
                    }

                    if (selectedPromo != null) {
                        item {
                            SelectedPromoCard(
                                promo = selectedPromo,
                                onChange = { selectedPromoId = null },
                                onSave = onPromoSaved
                            )
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.promo_preview_only_footer),
                            style = MaterialTheme.typography.labelSmall,
                            color = DataraTextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 840.dp)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    val readyState = state as? PromoSetupUiState.Ready
    if (showManualDialog && readyState != null) {
        ManualPromoDialog(
            carrier = readyState.carrier,
            onDismiss = { showManualDialog = false },
            onSave = {
                showManualDialog = false
                onPromoSaved(it)
            }
        )
    }
}

@Composable
private fun PromoLoadingState() {
    DataraSurfaceCard(modifier = Modifier.widthIn(max = 840.dp)) {
        CircularProgressIndicator(color = DataraNeonBlue, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.promo_loading_title), style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
        Text(stringResource(R.string.promo_loading_body), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
    }
}

@Composable
private fun PromoCarrierUnavailableState(
    onPreviewCatalog: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DataraNoticeCard(
            title = stringResource(R.string.promo_carrier_unavailable_title),
            message = stringResource(R.string.promo_carrier_unavailable_body),
            accent = DataraWarning
        )
        DataraSurfaceCard {
            Text(
                stringResource(R.string.promo_preview_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = DataraTextSecondary
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onPreviewCatalog,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DataraNeonBlue)
            ) {
                Text(stringResource(R.string.promo_preview_catalog))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                border = BorderStroke(1.dp, DataraInputBorder)
            ) {
                Text(stringResource(R.string.action_skip_setup), color = DataraBlueSoft)
            }
        }
    }
}

@Composable
private fun CarrierPreviewCard(carrier: CarrierUiModel) {
    DataraSurfaceCard(modifier = Modifier.widthIn(max = 840.dp), borderColor = DataraNeonBlue.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(DataraNeonBlue.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(carrier.displayName.take(2).uppercase(), color = DataraBlueSoft, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.promo_detected_carrier), style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary)
                Text(
                    stringResource(R.string.promo_sample_carrier_format, carrier.displayName),
                    style = MaterialTheme.typography.titleMedium,
                    color = DataraTextPrimary
                )
            }
            DataraStatusBadge(stringResource(R.string.status_design_preview), DataraBlueSoft)
        }
    }
}

@Composable
private fun PromoCatalogControls(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: PromoCategory,
    onCategorySelected: (PromoCategory) -> Unit,
    carrier: CarrierUiModel,
    onManualClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text(stringResource(R.string.promo_search_label)) },
            placeholder = { Text(stringResource(R.string.promo_search_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PromoCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = {
                        Text(
                            when (category) {
                                PromoCategory.ALL -> stringResource(R.string.promo_filter_all)
                                PromoCategory.DATA -> stringResource(R.string.promo_filter_data)
                                PromoCategory.COMBO -> stringResource(R.string.promo_filter_combo)
                            }
                        )
                    }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DataraSectionTitle(
                title = stringResource(R.string.promo_catalog_title),
                supporting = stringResource(R.string.promo_catalog_supporting, carrier.displayName),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onManualClick) {
                Text(stringResource(R.string.promo_manual_add), color = DataraBlueSoft)
            }
        }
    }
}

@Composable
private fun PromoCatalogCard(
    promo: PromoCatalogItemUiModel,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .background(
                if (selected) DataraNeonBlue.copy(alpha = 0.10f) else DataraCardElevated,
                RoundedCornerShape(18.dp)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(promo.name, style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
                Text(
                    stringResource(R.string.promo_validity_format, promo.validityDays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DataraTextSecondary
                )
            }
            DataraStatusBadge(
                text = stringResource(R.string.promo_price_format, String.format(Locale.US, "%.0f", promo.price)),
                color = if (selected) DataraNeonGreen else DataraBlueSoft
            )
        }
        Spacer(Modifier.height(14.dp))
        val allowanceProgress = 1f
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(DataraDivider, CircleShape)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(allowanceProgress, 0f..1f)
                }
        ) {
            Box(
                Modifier
                    .fillMaxWidth(allowanceProgress)
                    .height(7.dp)
                    .background(DataraNeonBlue, CircleShape)
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            stringResource(R.string.promo_data_format, String.format(Locale.US, "%.1f", promo.dataGb)),
            style = MaterialTheme.typography.labelLarge,
            color = DataraBlueSoft
        )
    }
}

@Composable
private fun PromoCatalogEmptyState(onManualClick: () -> Unit) {
    DataraSurfaceCard(modifier = Modifier.widthIn(max = 840.dp)) {
        Text(stringResource(R.string.promo_empty_title), style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
        Text(stringResource(R.string.promo_empty_body), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onManualClick) {
            Text(stringResource(R.string.promo_manual_add), color = DataraBlueSoft)
        }
    }
}

@Composable
private fun SelectedPromoCard(
    promo: PromoCatalogItemUiModel,
    onChange: () -> Unit,
    onSave: (FrontendPromoSelection) -> Unit
) {
    var remainingText by rememberSaveable(promo.id) { mutableStateOf(String.format(Locale.US, "%.1f", promo.dataGb)) }
    var showValidation by rememberSaveable(promo.id) { mutableStateOf(false) }
    val remaining = remainingText.toDoubleOrNull()
    val isValid = remaining != null && remaining in 0.0..promo.dataGb
    val previewExpiryLabel = stringResource(R.string.status_design_preview)

    DataraSurfaceCard(
        modifier = Modifier.widthIn(max = 840.dp),
        borderColor = DataraNeonGreen.copy(alpha = 0.35f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DataraSectionTitle(
                title = stringResource(R.string.promo_selected_title),
                supporting = promo.name,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onChange) {
                Text(stringResource(R.string.promo_change_selection), color = DataraBlueSoft)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = remainingText,
            onValueChange = { remainingText = it.filter { char -> char.isDigit() || char == '.' } },
            label = { Text(stringResource(R.string.promo_starting_balance_label)) },
            supportingText = {
                Text(
                    if (showValidation && !isValid) {
                        stringResource(R.string.promo_validation_balance_range)
                    } else {
                        stringResource(R.string.promo_starting_balance_support)
                    }
                )
            },
            isError = showValidation && !isValid,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        DataraNoticeCard(
            title = stringResource(R.string.promo_estimate_title),
            message = stringResource(R.string.promo_estimate_body),
            accent = DataraWarning
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                showValidation = true
                if (isValid) {
                    onSave(
                        FrontendPromoSelection(
                            name = promo.name,
                            carrier = promo.carrier.displayName,
                            totalGb = promo.dataGb,
                            remainingGb = remaining ?: promo.dataGb,
                            validityDays = promo.validityDays,
                            expiryLabel = previewExpiryLabel
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DataraNeonBlue)
        ) {
            Text(stringResource(R.string.promo_use_selected))
        }
    }
}

@Composable
private fun ManualPromoDialog(
    carrier: CarrierUiModel,
    onDismiss: () -> Unit,
    onSave: (FrontendPromoSelection) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var allowance by rememberSaveable { mutableStateOf("") }
    var validity by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var remaining by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val allowanceValue = allowance.toDoubleOrNull()
    val validityValue = validity.toIntOrNull()
    val remainingValue = remaining.toDoubleOrNull()
    val nameValid = name.isNotBlank()
    val allowanceValid = allowanceValue != null && allowanceValue > 0
    val validityValid = validityValue != null && validityValue > 0
    val expiryValid = expiry.isNotBlank()
    val remainingValid = allowanceValue != null && remainingValue != null && remainingValue in 0.0..allowanceValue
    val allValid = nameValid && allowanceValid && validityValid && expiryValid && remainingValid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DataraCardElevated,
        title = { Text(stringResource(R.string.promo_manual_title), color = DataraTextPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.promo_manual_body), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
                DataraStatusBadge(
                    text = stringResource(R.string.promo_sample_carrier_format, carrier.displayName),
                    color = DataraBlueSoft
                )
                PromoDialogField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.promo_name_label),
                    isError = submitted && !nameValid,
                    errorText = stringResource(R.string.promo_validation_required)
                )
                PromoDialogField(
                    value = allowance,
                    onValueChange = { allowance = it.filterDecimal() },
                    label = stringResource(R.string.promo_data_allowance_label),
                    isError = submitted && !allowanceValid,
                    errorText = stringResource(R.string.promo_validation_positive_number)
                )
                PromoDialogField(
                    value = validity,
                    onValueChange = { validity = it.filter(Char::isDigit) },
                    label = stringResource(R.string.promo_validity_days_label),
                    isError = submitted && !validityValid,
                    errorText = stringResource(R.string.promo_validation_positive_number)
                )
                PromoDialogField(
                    value = expiry,
                    onValueChange = { expiry = it },
                    label = stringResource(R.string.promo_expiry_label),
                    placeholder = stringResource(R.string.promo_expiry_placeholder),
                    isError = submitted && !expiryValid,
                    errorText = stringResource(R.string.promo_validation_required)
                )
                PromoDialogField(
                    value = remaining,
                    onValueChange = { remaining = it.filterDecimal() },
                    label = stringResource(R.string.promo_remaining_label),
                    isError = submitted && !remainingValid,
                    errorText = stringResource(R.string.promo_validation_balance_range)
                )
                if (submitted && !allValid) {
                    Text(
                        stringResource(R.string.promo_validation_fix_fields),
                        color = DataraError,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    submitted = true
                    if (allValid) {
                        onSave(
                            FrontendPromoSelection(
                                name = name.trim(),
                                carrier = carrier.displayName,
                                totalGb = allowanceValue ?: 0.0,
                                remainingGb = remainingValue ?: 0.0,
                                validityDays = validityValue ?: 0,
                                expiryLabel = expiry.trim()
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.promo_save_manual), color = DataraBlueSoft)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = DataraTextSecondary)
            }
        }
    )
}

@Composable
private fun PromoDialogField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    errorText: String,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { placeholderText -> { Text(placeholderText) } },
        isError = isError,
        supportingText = if (isError) ({ Text(errorText) }) else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun String.filterDecimal(): String = filter { it.isDigit() || it == '.' }

private val SamplePromoCatalog = listOf(
    PromoCatalogItemUiModel("globe-go99", CarrierUiModel.GLOBE, "Go+99", 8.0, 7, 99.0, PromoCategory.COMBO),
    PromoCatalogItemUiModel("globe-go50", CarrierUiModel.GLOBE, "Go50", 5.0, 3, 50.0, PromoCategory.DATA),
    PromoCatalogItemUiModel("globe-go140", CarrierUiModel.GLOBE, "Go+140", 12.0, 7, 140.0, PromoCategory.COMBO),
    PromoCatalogItemUiModel("smart-power99", CarrierUiModel.SMART, "Power All 99", 8.0, 7, 99.0, PromoCategory.COMBO),
    PromoCatalogItemUiModel("smart-magic199", CarrierUiModel.SMART, "Magic Data 199", 6.0, 30, 199.0, PromoCategory.DATA),
    PromoCatalogItemUiModel("dito-level99", CarrierUiModel.DITO, "Level-Up 99", 8.0, 7, 99.0, PromoCategory.COMBO),
    PromoCatalogItemUiModel("dito-data50", CarrierUiModel.DITO, "Data 50", 5.0, 7, 50.0, PromoCategory.DATA)
)

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 920)
@Composable
private fun PromoUnavailablePreview() {
    DATAraTheme {
        PromoSetupScreen(onBack = {}, onSkip = {}, onPromoSaved = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 1100)
@Composable
private fun PromoReadyPreview() {
    DATAraTheme {
        PromoSetupScreen(
            onBack = {},
            onSkip = {},
            onPromoSaved = {},
            initialState = PromoSetupUiState.Ready(CarrierUiModel.GLOBE)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 720)
@Composable
private fun PromoLoadingPreview() {
    DATAraTheme {
        PromoSetupScreen(
            onBack = {},
            onSkip = {},
            onPromoSaved = {},
            initialState = PromoSetupUiState.Loading
        )
    }
}
