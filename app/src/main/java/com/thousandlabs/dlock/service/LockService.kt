package com.thousandlabs.dlock.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.thousandlabs.dlock.data.Apps
import com.thousandlabs.dlock.data.Store
import com.thousandlabs.dlock.data.Vpn
import com.thousandlabs.dlock.ui.LockActivity

/**
 * Service d'accessibilité : repère quelle appli passe au premier plan et affiche l'écran
 * de blocage quand il le faut. Il ne lit aucun contenu à l'écran.
 */
class LockService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: LockService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val cn = ComponentName(context, LockService::class.java)
            return enabled.split(':').any {
                it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
            }
        }
    }

    private val main = Handler(Looper.getMainLooper())

    /** Appli actuellement au premier plan. */
    private var currentPkg: String? = null

    /** Applis déverrouillées jusqu'à ce qu'on les quitte. */
    private val unlocked = mutableSetOf<String>()

    private var pendingAfterScreenOff: String? = null
    private var lastShowPkg: String? = null
    private var lastShowAt = 0L

    /** Après un blocage VPN : appli à rouvrir dès que le VPN se connecte. */
    private var returnTo: String? = null
    private var returnUntil = 0L

    private var imePackages: Set<String> = emptySet()
    private val activityCache = HashMap<String, Boolean>()
    private var vpnCallback: ConnectivityManager.NetworkCallback? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // écran éteint : tout se reverrouille
                    pendingAfterScreenOff = currentPkg
                    unlocked.clear()
                    currentPkg = null
                }
                Intent.ACTION_USER_PRESENT -> main.postDelayed({
                    val p = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
                        ?: pendingAfterScreenOff
                    pendingAfterScreenOff = null
                    if (p != null && p != packageName && !isIgnored(p)) {
                        currentPkg = p
                        evaluate(p)
                    }
                }, 400)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        instance = this
        refreshIme()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        vpnCallback = Vpn.watch(this) { onVpnChanged() }
    }

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(screenReceiver) }
        Vpn.unwatch(this, vpnCallback)
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || isIgnored(pkg)) return

        // seul un vrai écran d'appli (pas une bulle, un menu ou une notification) compte comme changement d'appli
        if (pkg != currentPkg && isActivity(pkg, event.className)) {
            unlocked.retainAll { it == pkg }
            currentPkg = pkg
        }
        if (pkg == currentPkg) evaluate(pkg)
    }

    private fun evaluate(pkg: String) {
        if (pkg in unlocked) return
        if (pkg == Store.YOUTUBE && Store.ytVpnRequired) {
            if (Vpn.isActive(this)) {
                if (returnTo == pkg) returnTo = null
                return
            }
            showLock(pkg, LockActivity.MODE_VPN)
            return
        }
        if (pkg in Store.lockedApps) showLock(pkg, LockActivity.MODE_CODE)
    }

    private fun showLock(pkg: String, mode: String) {
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastShowPkg && now - lastShowAt < 800) return
        lastShowPkg = pkg
        lastShowAt = now
        if (mode == LockActivity.MODE_VPN) {
            returnTo = pkg
            returnUntil = now + 3 * 60_000L
        }
        val intent = Intent(this, LockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(LockActivity.EXTRA_PKG, pkg)
            .putExtra(LockActivity.EXTRA_MODE, mode)
        runCatching { startActivity(intent) }
    }

    private fun onVpnChanged() {
        main.postDelayed({
            if (Vpn.isActive(this)) {
                val target = returnTo
                if (target != null && SystemClock.elapsedRealtime() < returnUntil &&
                    currentPkg != target && Store.ytAutoReturn
                ) {
                    returnTo = null
                    Apps.launch(this, target)
                }
            } else {
                // VPN coupé pendant qu'on regarde YouTube : on bloque tout de suite
                val cur = currentPkg
                if (cur == Store.YOUTUBE && Store.ytVpnRequired && cur !in unlocked) {
                    showLock(cur, LockActivity.MODE_VPN)
                }
            }
        }, 700)
    }

    /** Appelé par l'écran de blocage après un déverrouillage réussi. */
    fun markUnlocked(pkg: String) {
        unlocked.add(pkg)
        currentPkg = pkg
        if (returnTo == pkg) returnTo = null
    }

    fun cancelReturn() {
        returnTo = null
    }

    private fun isIgnored(pkg: String): Boolean =
        pkg == "com.android.systemui" || pkg == "android" ||
            pkg == "com.android.permissioncontroller" || pkg == "com.google.android.permissioncontroller" ||
            pkg in imePackages

    private fun refreshIme() {
        imePackages = runCatching {
            getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
                ?.map { it.packageName }?.toSet()
        }.getOrNull() ?: emptySet()
    }

    private fun isActivity(pkg: String, cls: CharSequence?): Boolean {
        if (cls == null) return false
        val key = "$pkg/$cls"
        activityCache[key]?.let { return it }
        val result = runCatching {
            packageManager.getActivityInfo(ComponentName(pkg, cls.toString()), 0)
            true
        }.getOrDefault(false)
        if (activityCache.size > 500) activityCache.clear()
        activityCache[key] = result
        if (!result && pkg !in imePackages) refreshIme()
        return result
    }
}
