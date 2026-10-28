package com.capstone.datara.ui.promos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.datara.R
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraError
import com.capstone.datara.ui.theme.DataraPrimaryBlue
import java.util.Locale

enum class CarrierUiModel(val displayName: String) {
    SMART("Smart"),
    GLOBE("Globe"),
    DITO("DITO")
}

enum class PromoCategory { ALL, DATA, COMBO }

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
    val price: Double? = null,
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

private enum class PromoPage { CATALOG, MANUAL, ADJUST_CATALOG }

@Composable
fun PromoSetupScreen(
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onPromoSaved: (FrontendPromoSelection) -> Unit,
    modifier: Modifier = Modifier,
    initialState: PromoSetupUiState = PromoSetupUiState.Ready(CarrierUiModel.GLOBE)
) {
    var previewCarrierName by rememberSaveable { mutableStateOf<String?>(null) }
    val displayedState = previewCarrierName?.let {
        PromoSetupUiState.Ready(CarrierUiModel.valueOf(it))
    } ?: initialState

    when (displayedState) {
        PromoSetupUiState.Loading -> PromoMessageScreen(
            title = stringResource(R.string.promo_page_title),
            message = stringResource(R.string.promo_loading_body),
            onBack = onBack,
            showLoading = true,
            modifier = modifier
        )
        PromoSetupUiState.CarrierUnavailable -> PromoUnavailableScreen(
            onBack = onBack,
            onSkip = onSkip,
            onPreviewCatalog = { previewCarrierName = CarrierUiModel.GLOBE.name },
            modifier = modifier
        )
        is PromoSetupUiState.Error -> PromoMessageScreen(
            title = stringResource(R.string.promo_error_title),
            message = displayedState.message,
            onBack = onBack,
            modifier = modifier
        )
        is PromoSetupUiState.Ready -> PromoReadyScreen(
            state = displayedState,
            onBack = onBack,
            onPromoSaved = onPromoSaved,
            modifier = modifier
        )
    }
}

@Composable
private fun PromoReadyScreen(
    state: PromoSetupUiState.Ready,
    onBack: () -> Unit,
    onPromoSaved: (FrontendPromoSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    var pageName by rememberSaveable { mutableStateOf(PromoPage.CATALOG.name) }
    var selectedPromoId by rememberSaveable { mutableStateOf<String?>(null) }
    val carrierPromos = state.catalog.filter { it.carrier == state.carrier }
    val selectedPromo = carrierPromos.firstOrNull { it.id == selectedPromoId }

    val navigateBack = {
        if (PromoPage.valueOf(pageName) == PromoPage.CATALOG) onBack()
        else {
            pageName = PromoPage.CATALOG.name
            selectedPromoId = null
        }
    }

    when (PromoPage.valueOf(pageName)) {
        PromoPage.CATALOG -> PromoCatalogPage(
            carrier = state.carrier,
            promos = carrierPromos,
            onBack = navigateBack,
            onManualClick = { pageName = PromoPage.MANUAL.name },
            onPromoClick = {
                selectedPromoId = it.id
                pageName = PromoPage.ADJUST_CATALOG.name
            },
            modifier = modifier
        )
        PromoPage.MANUAL -> ManualPromoPage(
            carrier = state.carrier,
            onBack = navigateBack,
            onSave = onPromoSaved,
            modifier = modifier
        )
        PromoPage.ADJUST_CATALOG -> {
            if (selectedPromo != null) {
                AdjustCatalogPromoPage(
                    promo = selectedPromo,
                    onBack = navigateBack,
                    onSave = onPromoSaved,
                    modifier = modifier
                )
            } else {
                pageName = PromoPage.CATALOG.name
            }
        }
    }
}

@Composable
private fun PromoCatalogPage(
    carrier: CarrierUiModel,
    promos: List<PromoCatalogItemUiModel>,
    onBack: () -> Unit,
    onManualClick: () -> Unit,
    onPromoClick: (PromoCatalogItemUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val visiblePromos = if (showAll) promos else promos.take(3)

    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
                    PromoHeader(
                        title = stringResource(R.string.promo_page_title),
                        subtitle = stringResource(R.string.promo_page_subtitle),
                        onBack = onBack
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = onManualClick,
                        modifier = Modifier.fillMaxWidth().height(62.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            stringResource(R.string.promo_add_manual_button),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (visiblePromos.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.promo_catalog_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(vertical = 28.dp)
                    )
                }
            } else {
                items(visiblePromos, key = { it.id }) { promo ->
                    PromoCatalogCard(promo = promo, onClick = { onPromoClick(promo) })
                }
            }

            if (promos.size > 3) {
                item {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(
                            if (showAll) stringResource(R.string.promo_show_less) else stringResource(R.string.promo_see_more),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            item {
                Text(
                    text = "${carrier.displayName} • ${stringResource(R.string.promo_preview_only_footer)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
                )
            }
        }
    }
}

@Composable
private fun PromoCatalogCard(promo: PromoCatalogItemUiModel, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 5.dp
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        promo.name.uppercase(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        stringResource(R.string.promo_catalog_source),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Text(
                        promo.carrier.displayName.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(1f, 0f..1f) }
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary, CircleShape))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.promo_catalog_total_format, promo.dataGb.cleanNumber()),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
                Text(
                    stringResource(R.string.promo_catalog_validity_short, promo.validityDays),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ManualPromoPage(
    carrier: CarrierUiModel,
    onBack: () -> Unit,
    onSave: (FrontendPromoSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf("") }
    var allowance by rememberSaveable { mutableStateOf("") }
    var remaining by rememberSaveable { mutableStateOf("") }
    var validity by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val allowanceValue = allowance.toDoubleOrNull()
    val remainingValue = remaining.toDoubleOrNull()
    val validityValue = validity.toIntOrNull()
    val priceValue = price.toDoubleOrNull()
    val nameValid = name.isNotBlank()
    val allowanceValid = allowanceValue != null && allowanceValue > 0
    val remainingValid = allowanceValue != null && remainingValue != null && remainingValue in 0.0..allowanceValue
    val validityValid = validityValue != null && validityValue > 0
    val expiryValid = expiry.isNotBlank()
    val priceValid = price.isBlank() || (priceValue != null && priceValue >= 0)
    val allValid = nameValid && allowanceValid && remainingValid && validityValid && expiryValid && priceValid

    PromoFormScaffold(
        title = stringResource(R.string.promo_manual_page_title),
        subtitle = stringResource(R.string.promo_manual_page_subtitle),
        onBack = onBack,
        modifier = modifier
    ) {
        CarrierLine(carrier)
        PromoField(name, { name = it }, stringResource(R.string.promo_name_label), submitted && !nameValid)
        PromoField(
            allowance,
            { allowance = it.filterDecimal() },
            stringResource(R.string.promo_data_allowance_label),
            submitted && !allowanceValid,
            keyboardType = KeyboardType.Decimal
        )
        PromoField(
            remaining,
            { remaining = it.filterDecimal() },
            stringResource(R.string.promo_remaining_label),
            submitted && !remainingValid,
            errorText = stringResource(R.string.promo_validation_balance_range),
            keyboardType = KeyboardType.Decimal
        )
        PromoField(
            validity,
            { validity = it.filter(Char::isDigit) },
            stringResource(R.string.promo_validity_days_label),
            submitted && !validityValid,
            keyboardType = KeyboardType.Number
        )
        PromoField(
            expiry,
            { expiry = it },
            stringResource(R.string.promo_expiry_label),
            submitted && !expiryValid,
            placeholder = stringResource(R.string.promo_expiry_placeholder)
        )
        PromoField(
            price,
            { price = it.filterDecimal() },
            stringResource(R.string.promo_price_label),
            submitted && !priceValid,
            keyboardType = KeyboardType.Decimal
        )
        if (submitted && !allValid) FormError()
        PrimaryPromoButton(stringResource(R.string.promo_save_button)) {
            submitted = true
            if (allValid) {
                onSave(
                    FrontendPromoSelection(
                        name = name.trim(),
                        carrier = carrier.displayName,
                        totalGb = allowanceValue ?: 0.0,
                        remainingGb = remainingValue ?: 0.0,
                        validityDays = validityValue ?: 0,
                        expiryLabel = expiry.trim(),
                        price = priceValue
                    )
                )
            }
        }
    }
}

@Composable
private fun AdjustCatalogPromoPage(
    promo: PromoCatalogItemUiModel,
    onBack: () -> Unit,
    onSave: (FrontendPromoSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    var remaining by rememberSaveable(promo.id) { mutableStateOf(promo.dataGb.cleanNumber()) }
    var expiry by rememberSaveable(promo.id) { mutableStateOf("") }
    var submitted by rememberSaveable(promo.id) { mutableStateOf(false) }
    val remainingValue = remaining.toDoubleOrNull()
    val remainingValid = remainingValue != null && remainingValue in 0.0..promo.dataGb
    val expiryValid = expiry.isNotBlank()

    PromoFormScaffold(
        title = stringResource(R.string.promo_adjust_page_title),
        subtitle = stringResource(R.string.promo_adjust_page_subtitle),
        onBack = onBack,
        modifier = modifier
    ) {
        PromoCatalogCard(promo = promo, onClick = {})
        PromoField(
            value = remaining,
            onValueChange = { remaining = it.filterDecimal() },
            label = stringResource(R.string.promo_starting_balance_label),
            isError = submitted && !remainingValid,
            errorText = stringResource(R.string.promo_validation_balance_range),
            supportingText = stringResource(R.string.promo_balance_note, promo.dataGb.cleanNumber()),
            keyboardType = KeyboardType.Decimal
        )
        PromoField(
            value = expiry,
            onValueChange = { expiry = it },
            label = stringResource(R.string.promo_catalog_expiry_label),
            isError = submitted && !expiryValid,
            placeholder = stringResource(R.string.promo_expiry_placeholder),
            supportingText = stringResource(R.string.promo_catalog_expiry_support)
        )
        if (submitted && (!remainingValid || !expiryValid)) FormError()
        PrimaryPromoButton(stringResource(R.string.promo_apply_button)) {
            submitted = true
            if (remainingValid && expiryValid) {
                onSave(
                    FrontendPromoSelection(
                        name = promo.name,
                        carrier = promo.carrier.displayName,
                        totalGb = promo.dataGb,
                        remainingGb = remainingValue ?: promo.dataGb,
                        validityDays = promo.validityDays,
                        expiryLabel = expiry.trim(),
                        price = promo.price
                    )
                )
            }
        }
    }
}

@Composable
private fun PromoFormScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().widthIn(max = 560.dp).align(Alignment.CenterHorizontally)) {
                PromoHeader(title = title, subtitle = subtitle, onBack = onBack)
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun PromoHeader(title: String, subtitle: String, onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier
            .size(46.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Icon(DataraIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
    Spacer(Modifier.height(10.dp))
    Text(
        title,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.8).sp
    )
    Text(
        subtitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
}

@Composable
private fun CarrierLine(carrier: CarrierUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(stringResource(R.string.promo_detected_carrier), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text(carrier.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Text(
                carrier.displayName.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun PromoField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    errorText: String = stringResource(R.string.promo_validation_required),
    placeholder: String? = null,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { valueText -> { Text(valueText) } },
        supportingText = when {
            isError -> ({ Text(errorText) })
            supportingText != null -> ({ Text(supportingText) })
            else -> null
        },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            errorTextColor = DataraError,
            errorBorderColor = DataraError,
            errorLabelColor = DataraError
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FormError() {
    Text(
        stringResource(R.string.promo_validation_fix_fields),
        color = DataraError,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Composable
private fun PrimaryPromoButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun PromoUnavailableScreen(
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onPreviewCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    PromoFormScaffold(
        title = stringResource(R.string.promo_page_title),
        subtitle = stringResource(R.string.promo_page_subtitle),
        onBack = onBack,
        modifier = modifier
    ) {
        Text(stringResource(R.string.promo_carrier_unavailable_title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.promo_carrier_unavailable_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryPromoButton(stringResource(R.string.promo_preview_catalog), onPreviewCatalog)
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(stringResource(R.string.action_skip_setup), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PromoMessageScreen(
    title: String,
    message: String,
    onBack: () -> Unit,
    showLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    PromoFormScaffold(title, message, onBack, modifier) {
        if (showLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

private fun String.filterDecimal(): String {
    var decimalFound = false
    return filter { char ->
        when {
            char.isDigit() -> true
            char == '.' && !decimalFound -> {
                decimalFound = true
                true
            }
            else -> false
        }
    }
}

private fun Double.cleanNumber(): String =
    if (this % 1.0 == 0.0) String.format(Locale.US, "%.0f", this)
    else String.format(Locale.US, "%.1f", this)

private val SamplePromoCatalog = listOf(
    PromoCatalogItemUiModel("globe-go99", CarrierUiModel.GLOBE, "Go+99", 8.0, 7, 99.0, PromoCategory.COMBO),
    PromoCatalogItemUiModel("globe-go50", CarrierUiModel.GLOBE, "Go50", 5.0, 3, 50.0, PromoCategory.DATA),
    PromoCatalogItemUiModel("globe-go140", CarrierUiModel.GLOBE, "Go+140", 12.0, 7, 140.0, PromoCategory.COMBO),
    PromoCatalogItemUiModel("globe-go250", CarrierUiModel.GLOBE, "Go+250", 20.0, 15, 250.0, PromoCategory.COMBO),
    PromoCatalogItemUiModel("smart-power99", CarrierUiModel.SMART, "Power All 99", 8.0, 7, 99.0, PromoCategory.COMBO),
    PromoCatalogItemUiModel("smart-magic199", CarrierUiModel.SMART, "Magic Data 199", 6.0, 30, 199.0, PromoCategory.DATA),
    PromoCatalogItemUiModel("dito-level99", CarrierUiModel.DITO, "Level-Up 99", 8.0, 7, 99.0, PromoCategory.COMBO),
    PromoCatalogItemUiModel("dito-data50", CarrierUiModel.DITO, "Data 50", 5.0, 7, 50.0, PromoCategory.DATA)
)

@Preview(showBackground = true, backgroundColor = 0xFFFAFAFA, heightDp = 920)
@Composable
private fun PromoCatalogPreview() {
    DATAraTheme { PromoSetupScreen(onBack = {}, onSkip = {}, onPromoSaved = {}) }
}
