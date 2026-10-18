package com.capstone.datara.ui.legal

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.R
import com.capstone.datara.ui.components.DataraPageHeader
import com.capstone.datara.ui.components.DataraStatusBadge
import com.capstone.datara.ui.components.DataraSurfaceCard
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraBlueSoft
import com.capstone.datara.ui.theme.DataraDarkBg
import com.capstone.datara.ui.theme.DataraTextPrimary
import com.capstone.datara.ui.theme.DataraTextSecondary
import com.capstone.datara.ui.theme.DataraWarning

private data class LegalSection(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int
)

@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    LegalScreenContent(
        eyebrow = stringResource(R.string.privacy_eyebrow),
        title = stringResource(R.string.privacy_title),
        subtitle = stringResource(R.string.privacy_subtitle),
        badge = stringResource(R.string.privacy_draft_badge),
        sections = PrivacySections,
        footer = stringResource(R.string.privacy_footer),
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun TermsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    LegalScreenContent(
        eyebrow = stringResource(R.string.terms_eyebrow),
        title = stringResource(R.string.terms_title),
        subtitle = stringResource(R.string.terms_subtitle),
        badge = stringResource(R.string.terms_draft_badge),
        sections = TermsSections,
        footer = stringResource(R.string.terms_footer),
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
private fun LegalScreenContent(
    eyebrow: String,
    title: String,
    subtitle: String,
    badge: String,
    sections: List<LegalSection>,
    footer: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DataraDarkBg
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
                        eyebrow = eyebrow,
                        title = title,
                        subtitle = subtitle,
                        onBack = onBack,
                        backContentDescription = stringResource(R.string.action_back),
                        trailingContent = {
                            DataraStatusBadge(badge, DataraBlueSoft)
                        }
                    )
                }
            }
            sections.forEach { section ->
                item(key = section.titleRes) {
                    DataraSurfaceCard(modifier = Modifier.widthIn(max = 760.dp)) {
                        Text(
                            text = stringResource(section.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = DataraTextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(section.bodyRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = DataraTextSecondary
                        )
                    }
                }
            }
            item {
                Text(
                    text = footer,
                    style = MaterialTheme.typography.labelSmall,
                    color = DataraWarning,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(8.dp)
                )
            }
        }
    }
}

private val PrivacySections = listOf(
    LegalSection(R.string.privacy_measure_title, R.string.privacy_measure_body),
    LegalSection(R.string.privacy_not_collect_title, R.string.privacy_not_collect_body),
    LegalSection(R.string.privacy_local_title, R.string.privacy_local_body),
    LegalSection(R.string.privacy_sync_title, R.string.privacy_sync_body),
    LegalSection(R.string.privacy_accuracy_title, R.string.privacy_accuracy_body),
    LegalSection(R.string.privacy_control_title, R.string.privacy_control_body)
)

private val TermsSections = listOf(
    LegalSection(R.string.terms_purpose_title, R.string.terms_purpose_body),
    LegalSection(R.string.terms_estimate_title, R.string.terms_estimate_body),
    LegalSection(R.string.terms_carrier_title, R.string.terms_carrier_body),
    LegalSection(R.string.terms_access_title, R.string.terms_access_body),
    LegalSection(R.string.terms_availability_title, R.string.terms_availability_body)
)

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 1000)
@Composable
private fun PrivacyPreview() {
    DATAraTheme { PrivacyScreen(onBack = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 1000)
@Composable
private fun TermsPreview() {
    DATAraTheme { TermsScreen(onBack = {}) }
}

