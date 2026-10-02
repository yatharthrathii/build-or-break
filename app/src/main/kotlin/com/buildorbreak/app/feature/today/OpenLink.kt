package com.buildorbreak.app.feature.today

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.buildorbreak.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FILE_SCHEME = "content:"

/** Picked from the phone rather than typed. Its label is the file name, not the address. */
internal fun String.isFileLink(): Boolean = trim().startsWith(FILE_SCHEME)

/**
 * A link as typed, made openable.
 *
 * Somebody who types "youtube.com/..." has typed a link, and a step that
 * refused to open it over a missing "https://" would be a step that
 * punishes the ordinary way of writing an address.
 */
internal fun normaliseLink(raw: String): String? {
    val link = raw.trim()
    if (link.isEmpty()) return null

    return if ("://" in link || link.startsWith(FILE_SCHEME) || link.startsWith("mailto:")) link else "https://$link"
}

/**
 * Opens whatever the step carries, in whatever app the phone has for it.
 *
 * Says so when nothing can, rather than doing nothing. A button that does
 * nothing and does not say why is the most annoying thing a screen can
 * contain, and a file picked on another phone is exactly the case.
 */
internal fun Context.openLink(raw: String) {
    val target = normaliseLink(raw) ?: return
    val intent = Intent(Intent.ACTION_VIEW, target.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    @Suppress("SwallowedException")
    try {
        startActivity(intent)
    } catch (missing: ActivityNotFoundException) {
        Toast.makeText(this, R.string.today_link_failed, Toast.LENGTH_SHORT).show()
    } catch (denied: SecurityException) {
        // A file picked on another phone, or one whose permission has lapsed.
        Toast.makeText(this, R.string.today_link_failed, Toast.LENGTH_SHORT).show()
    }
}

/**
 * The link in a word or two: the site for an address, the name for a file.
 *
 * The file name needs the content resolver, so it is read off the main
 * thread and the label says "File" until it arrives.
 */
@Composable
internal fun rememberLinkLabel(raw: String): String {
    val context = LocalContext.current
    val fallback = stringResource(R.string.today_link_file)
    val label by produceState(initialValue = quickLabel(raw, fallback), raw) {
        if (raw.isFileLink()) {
            value = withContext(Dispatchers.IO) { context.fileName(raw) } ?: fallback
        }
    }

    return label
}

/** What can be said about a link without asking the phone anything. */
private fun quickLabel(raw: String, fileLabel: String): String {
    if (raw.isFileLink()) return fileLabel

    val target = normaliseLink(raw) ?: return ""

    return runCatching { target.toUri().host }.getOrNull()?.removePrefix("www.") ?: target
}

private fun Context.fileName(raw: String): String? = runCatching {
    contentResolver.query(raw.trim().toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull()
