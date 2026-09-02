package com.buildorbreak.core.model.audit

import com.buildorbreak.core.model.enums.DeliveryTier
import java.time.Instant

/**
 * What time an alarm was supposed to fire, and what time it actually did.
 *
 * This is how a claim becomes evidence. Android alarm reliability is the whole
 * product, and the only way to know whether it works on a Redmi at six in the
 * morning is to measure it rather than hope.
 *
 * One row per scheduled alarm, written at schedule time, updated at fire time.
 */
data class DeliveryAudit(
    val id: Long,
    val occurrenceId: Long,
    val scheduledFor: Instant,
    val firedAt: Instant?,
    val tier: DeliveryTier,
    val deviceModel: String,
    val manufacturer: String,
    val sdkInt: Int,
    val wasDeviceIdle: Boolean,
    /** Denormalised so a reliability figure is one aggregate query. */
    val latencySeconds: Long?,
) {
    val fired: Boolean get() = firedAt != null

    fun wasOnTime(toleranceSeconds: Long = ON_TIME_TOLERANCE_SECONDS): Boolean =
        fired && (latencySeconds ?: Long.MAX_VALUE) <= toleranceSeconds

    companion object {
        /** rules.md section 5 measures accuracy within sixty seconds. */
        const val ON_TIME_TOLERANCE_SECONDS = 60L
    }
}
