package com.buildorbreak.app.feature.about

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.Label
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme

/** One heading and the paragraph under it. */
private data class Section(@param:StringRes val title: Int, @param:StringRes val body: Int)

/**
 * The privacy policy and the terms, as pages in the app.
 *
 * Written in the same plain voice as the rest of the app rather than in the
 * voice of a contract, because the person reading this is the person who
 * uses the app, and a wall of clauses would tell them nothing about where
 * their weight readings are kept. The answer is: on the phone, and nowhere
 * else.
 */
@Composable
fun LegalScreen(document: LegalDocument, onBack: () -> Unit, modifier: Modifier = Modifier) {
    LegalContent(document = document, onBack = onBack, modifier = modifier)
}

@Composable
fun LegalContent(document: LegalDocument, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        BackHeader(
            kicker = stringResource(R.string.legal_updated, stringResource(R.string.legal_updated_on)),
            title = stringResource(titleOf(document)),
            onBack = onBack,
        )

        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            sectionsOf(document).forEach { section ->
                Label(
                    text = stringResource(section.title),
                    modifier = Modifier.padding(
                        start = Theme.spacing.medium,
                        end = Theme.spacing.medium,
                        top = Theme.spacing.medium,
                    ),
                )
                Paragraph(text = stringResource(section.body))
            }
        }
    }
}

private fun titleOf(document: LegalDocument): Int = when (document) {
    LegalDocument.PRIVACY -> R.string.about_privacy
    LegalDocument.TERMS -> R.string.about_terms
}

private fun sectionsOf(document: LegalDocument): List<Section> = when (document) {
    LegalDocument.PRIVACY -> listOf(
        Section(R.string.privacy_short_title, R.string.privacy_short_body),
        Section(R.string.privacy_stored_title, R.string.privacy_stored_body),
        Section(R.string.privacy_leaves_title, R.string.privacy_leaves_body),
        Section(R.string.privacy_points_title, R.string.privacy_points_body),
        Section(R.string.privacy_ads_title, R.string.privacy_ads_body),
        Section(R.string.privacy_permissions_title, R.string.privacy_permissions_body),
        Section(R.string.privacy_delete_title, R.string.privacy_delete_body),
        Section(R.string.privacy_children_title, R.string.privacy_children_body),
        Section(R.string.privacy_changes_title, R.string.privacy_changes_body),
        Section(R.string.privacy_contact_title, R.string.privacy_contact_body),
    )

    LegalDocument.TERMS -> listOf(
        Section(R.string.terms_use_title, R.string.terms_use_body),
        Section(R.string.terms_alarms_title, R.string.terms_alarms_body),
        Section(R.string.terms_health_title, R.string.terms_health_body),
        Section(R.string.terms_data_title, R.string.terms_data_body),
        Section(R.string.terms_points_title, R.string.terms_points_body),
        Section(R.string.terms_points_change_title, R.string.terms_points_change_body),
        Section(R.string.terms_ads_title, R.string.terms_ads_body),
        Section(R.string.terms_licence_title, R.string.terms_licence_body),
        Section(R.string.terms_changes_title, R.string.terms_changes_body),
        Section(R.string.terms_contact_title, R.string.terms_contact_body),
    )
}

@Preview(showBackground = true)
@Composable
private fun PrivacyPreview() {
    BuildOrBreakTheme { LegalContent(document = LegalDocument.PRIVACY, onBack = {}) }
}
