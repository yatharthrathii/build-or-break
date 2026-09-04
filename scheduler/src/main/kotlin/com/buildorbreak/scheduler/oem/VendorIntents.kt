package com.buildorbreak.scheduler.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * The settings screen a particular phone hides its autostart switch on.
 *
 * This is the part of Android that no API covers. Every vendor ships a battery
 * manager that will stop a background app, none of them are reachable through
 * the framework, and the activity names are undocumented and change between
 * versions. So this is a list of known component names, tried in order, with the
 * app's own settings page as the last resort.
 *
 * **Every candidate is resolved before it is offered.** A component that does
 * not exist on this build throws `ActivityNotFoundException`, and an app that
 * crashes while trying to help somebody fix their alarms has made the situation
 * considerably worse.
 */
object VendorIntents {

    /**
     * Xiaomi is first because it is the case this app was written for. MIUI
     * hides autostart three menus deep in Security, and without it the app is
     * stopped overnight however many permissions are granted.
     */
    private val AUTOSTART_CANDIDATES = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
    )

    /** True when this phone is one the guidance is written for. */
    fun needsAutostartGuidance(context: Context): Boolean = autostartIntent(context) != null

    /**
     * The autostart screen, or null when this phone does not have one.
     *
     * Null is a genuine answer. A Pixel has nothing to fix here, and sending
     * somebody hunting through settings for a switch their phone does not have
     * is how a help screen loses its credibility.
     */
    fun autostartIntent(context: Context): Intent? = AUTOSTART_CANDIDATES
        .asSequence()
        .map { (pkg, activity) -> Intent().setComponent(ComponentName(pkg, activity)) }
        .firstOrNull { it.resolvesOn(context) }

    /**
     * The battery optimisation exemption prompt.
     *
     * Deliberately the settings list rather than the direct
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog. That one is a policy
     * restricted action on Play and asking for it wrongly gets a release
     * rejected, while sending the user to the list is always allowed.
     */
    fun batterySettingsIntent(context: Context): Intent? =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).takeIf { it.resolvesOn(context) }

    /** The system screen for granting exact alarms, from Android 12. */
    fun exactAlarmSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null

        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.fromParts("package", context.packageName, null))
            .takeIf { it.resolvesOn(context) }
    }

    /** This app's notification settings, where a silenced channel is turned back up. */
    fun notificationSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Always available, and the honest fallback when nothing more specific resolves. */
    fun appSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))

    private fun Intent.resolvesOn(context: Context): Boolean = context.packageManager.resolveActivity(this, 0) != null
}
