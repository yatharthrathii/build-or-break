package com.buildorbreak.app.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.Badge
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme

/**
 * Where to write when something is wrong, once there is somewhere to write to.
 *
 * A placeholder on purpose. Every other screen in this app refuses to show a
 * control that does nothing, and the same rule applies here: rather than a
 * form that posts nowhere or an address that is not read, the screen says
 * plainly that the way in is being built. That is a smaller promise than a
 * dead contact form, and it is one the app can keep.
 *
 * The privacy and terms screens point here, so this is the one place the
 * answer has to change when it changes.
 */
@Composable
fun ContactScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        BackHeader(
            kicker = stringResource(R.string.contact_kicker),
            title = stringResource(R.string.contact_title),
            onBack = onBack,
        )

        Badge(
            text = stringResource(R.string.contact_soon),
            modifier = Modifier.padding(start = Theme.spacing.medium, top = Theme.spacing.medium),
        )

        Paragraph(text = stringResource(R.string.contact_body))

        Text(
            text = stringResource(R.string.contact_meanwhile),
            style = MaterialTheme.typography.bodySmall,
            color = Theme.colours.faint,
            modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.small),
        )
    }
}

@Preview(name = "Contact", showBackground = true)
@Composable
private fun ContactPreview() {
    BuildOrBreakTheme { ContactScreen(onBack = {}) }
}
