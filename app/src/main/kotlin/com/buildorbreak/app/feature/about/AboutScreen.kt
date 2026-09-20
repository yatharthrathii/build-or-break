package com.buildorbreak.app.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.BuildConfig
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.SectionLabel
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import java.util.Locale

/**
 * What the app is, in the words somebody would use to a friend.
 *
 * The screen every store listing links to and almost nobody reads, kept
 * short for the few who do: what it does, what it does not do, where the
 * data is, and the two documents the law asks for. The version is here
 * because a bug report without one is a guess.
 */
@Composable
fun AboutScreen(
    onOpenLegal: (LegalDocument) -> Unit,
    onOpenContact: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AboutContent(
        version = BuildConfig.VERSION_NAME,
        onOpenLegal = onOpenLegal,
        onOpenContact = onOpenContact,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun AboutContent(
    version: String,
    onOpenLegal: (LegalDocument) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenContact: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        BackHeader(
            kicker = stringResource(R.string.about_kicker, version),
            title = stringResource(R.string.about_title),
            onBack = onBack,
        )

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Paragraph(text = stringResource(R.string.about_what))
            Paragraph(text = stringResource(R.string.about_not))
            Paragraph(text = stringResource(R.string.about_data))

            SectionLabel(text = stringResource(R.string.about_section_documents), underlined = true)
            DocumentRow(title = stringResource(R.string.about_privacy), onClick = {
                onOpenLegal(LegalDocument.PRIVACY)
            })
            DocumentRow(title = stringResource(R.string.about_terms), onClick = { onOpenLegal(LegalDocument.TERMS) })
            DocumentRow(title = stringResource(R.string.about_contact), onClick = onOpenContact)

            SectionLabel(text = stringResource(R.string.about_section_made), underlined = true)
            Paragraph(text = stringResource(R.string.about_made_by))
            Paragraph(text = stringResource(R.string.about_licence))
        }
    }
}

/** A back arrow, a kicker and a title. The same shape as every screen that is not a tab. */
@Composable
internal fun BackHeader(kicker: String, title: String, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Theme.spacing.medium, end = Theme.spacing.medium, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(role = Role.Button, onClick = onBack),
            )

            Column(modifier = Modifier.padding(start = Theme.spacing.inset)) {
                Kicker(text = kicker)
                Text(
                    text = title.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HeavyRule()
    }
}

@Composable
internal fun Paragraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 8.dp),
    )
}

@Composable
private fun DocumentRow(title: String, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Theme.colours.faint,
                modifier = Modifier.size(18.dp),
            )
        }

        HairlineRule()
    }
}

@Preview(showBackground = true)
@Composable
private fun AboutPreview() {
    BuildOrBreakTheme { AboutContent(version = "0.3.0", onOpenLegal = {}, onBack = {}) }
}
