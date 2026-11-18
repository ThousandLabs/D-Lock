package com.thousandlabs.dlock.data

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val pkg: String, val label: String)

object Apps {
    private val knownVpn = listOf("io.privado.android", "io.privado.android.vpn", "com.privado.vpn")

    /** Toutes les applis visibles dans le tiroir d'applications, sauf D-Lock. */
    fun launchable(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val list = pm.queryIntentActivities(intent, 0)
        return list
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg }
            .filter { it.pkg != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, pkg: String): String? = runCatching {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    fun isInstalled(context: Context, pkg: String?): Boolean = pkg != null && label(context, pkg) != null

    fun icon(context: Context, pkg: String, sizePx: Int = 112): ImageBitmap? = runCatching {
        context.packageManager.getApplicationIcon(pkg).toBitmap(sizePx, sizePx).asImageBitmap()
    }.getOrNull()

    /** Appli VPN choisie, ou Privado VPN détecté automatiquement. */
    fun vpnApp(context: Context): String? {
        Store.vpnPackage?.let { if (isInstalled(context, it)) return it }
        knownVpn.firstOrNull { isInstalled(context, it) }?.let { return it }
        return launchable(context).firstOrNull { it.label.contains("Privado", ignoreCase = true) }?.pkg
    }

    fun launch(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
