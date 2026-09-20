package com.buildorbreak.app.feature.settings

import androidx.annotation.StringRes
import com.buildorbreak.app.R
import com.buildorbreak.core.model.enums.DeliveryTier

// Copy lookups shared by every screen that mentions the delivery tier.
//
// Kept as small mappings rather than fields on a state, so no ViewModel carries
// a user visible word and the whole thing can be translated by editing one
// resource file.

/** The one line the timeline and the first run show. */
@StringRes
fun tierShortText(tier: DeliveryTier): Int = when (tier) {
    DeliveryTier.FULL_SCREEN_ALARM -> R.string.tier_full_screen_short
    DeliveryTier.EXACT_HEADS_UP -> R.string.tier_heads_up_short
    DeliveryTier.INEXACT_NOTIFICATION -> R.string.tier_inexact_short
    DeliveryTier.IN_APP_ONLY -> R.string.tier_in_app_short
}

@StringRes
fun tierHeadline(tier: DeliveryTier): Int = when (tier) {
    DeliveryTier.FULL_SCREEN_ALARM -> R.string.tier_full_screen_headline
    DeliveryTier.EXACT_HEADS_UP -> R.string.tier_heads_up_headline
    DeliveryTier.INEXACT_NOTIFICATION -> R.string.tier_inexact_headline
    DeliveryTier.IN_APP_ONLY -> R.string.tier_in_app_headline
}

@StringRes
fun tierBody(tier: DeliveryTier): Int = when (tier) {
    DeliveryTier.FULL_SCREEN_ALARM -> R.string.tier_full_screen_body
    DeliveryTier.EXACT_HEADS_UP -> R.string.tier_heads_up_body
    DeliveryTier.INEXACT_NOTIFICATION -> R.string.tier_inexact_body
    DeliveryTier.IN_APP_ONLY -> R.string.tier_in_app_body
}
