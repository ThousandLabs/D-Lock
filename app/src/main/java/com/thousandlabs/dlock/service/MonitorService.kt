package com.thousandlabs.dlock.service

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.thousandlabs.dlock.R
import com.thousandlabs.dlock.data.Apps
import com.thousandlabs.dlock.data.Store
import com.thousandlabs.dlock.data.Vpn
import com.thousandlabs.dlock.ui.LockActivity
import com.thousandlabs.dlock.ui.MainActivity

/**
 * Surveillance de l'appli au premier plan (via « Accès aux données d'utilisation »)
 * et affichage de l'écran de blocage (via « Afficher par-dessus les autres applis »).
 * Ne lit aucun contenu à l'écran.
 */
class MonitorService : Service() {

    companion object {
        private const val CHANNEL = "dlock_actif"
        private const val NOTIF_ID = 1
        private const val POLL_MS = 400L

        @Volatile
        var instance: MonitorService? = null
            private set

        fun hasUsageAccess(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun ready(context: Context) = hasUsageAccess(context) && hasOverlay(context)

        /** Lance la surveillance si les deux autorisations sont accordées. */
        fun start(context: Context) {
            if (!ready(context)) return
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
            }
        }

        fun isRunning() = instance != null
    }

    private val main = Handler(Looper.getMainLooper())
    private var usm: UsageStatsManager? = null
    private var lastEventTime = 0L

    /** Appli actuellement au premier plan. */
    private var currentPkg: String? = null

    /** Applis déverrouillées jusqu'à ce qu'on les quitte. */
    private val unlocked = mutableSetOf<String>()

    private var lastShowPkg: String? = null
    private var lastShowAt = 0L

    /** Après un blocage VPN : appli à rouvrir dès que le VPN se connecte. */
    private var returnTo: String? = null
    private var returnUntil = 0L

    private var polling = false
    private var vpnCallback: ConnectivityManager.NetworkCallback? = null

    private val poll = object : Runnable {
        override fun run() {
            if (!polling) return
            checkForeground()
            main.postDelayed(this, POLL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // écran éteint : tout se reverrouille, et la surveillance se met en pause
                    unlocked.clear()
                    stopPolling()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    startPolling()
                    if (intent.action == Intent.ACTION_USER_PRESENT) {
                        main.postDelayed({ currentPkg?.let { evaluate(it) } }, 350)
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        instance = this
        usm = getSystemService(UsageStatsManager::class.java)
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        vpnCallback = Vpn.watch(this) { onVpnChanged() }
        // retrouver l'appli déjà ouverte au démarrage
        lastEventTime = System.currentTimeMillis() - 60 * 60_000L
        if (getSystemService(PowerManager::class.java)?.isInteractive != false) startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ready(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopPolling()
        instance = null
        runCatching { unregisterReceiver(screenReceiver) }
        Vpn.unwatch(this, vpnCallback)
        super.onDestroy()
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        main.post(poll)
    }

    private fun stopPolling() {
        polling = false
        main.removeCallbacks(poll)
    }

    /** Lit les derniers changements d'appli au premier plan. */
    private fun checkForeground() {
        val manager = usm ?: return
        val now = System.currentTimeMillis()
        val events = runCatching { manager.queryEvents(lastEventTime, now) }.getOrNull() ?: return
        val e = UsageEvents.Event()
        var latest: String? = null
        var latestClass: String? = null
        var latestTime = lastEventTime
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED && e.timeStamp >= latestTime) {
                latest = e.packageName
                latestClass = e.className
                latestTime = e.timeStamp
            }
        }
        if (latestTime > lastEventTime) lastEventTime = latestTime + 1
        val pkg = latest ?: return
        if (pkg == packageName) {
            // l'écran de blocage ne compte pas ; l'écran principal de D-Lock, si (tout se reverrouille)
            if (latestClass != LockActivity::class.java.name) {
                unlocked.clear()
                currentPkg = pkg
            }
            return
        }
        onForeground(pkg)
    }

    private fun onForeground(pkg: String) {
        if (pkg == packageName || isIgnored(pkg)) return
        if (pkg != currentPkg) {
            unlocked.retainAll { it == pkg }
            currentPkg = pkg
        }
        evaluate(pkg)
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
            pkg == "com.android.permissioncontroller" || pkg == "com.google.android.permissioncontroller"

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, "D-Lock actif", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Notification permanente nécessaire pour que D-Lock surveille l'ouverture des applis."
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("D-Lock actif")
            .setContentText("Vos applis sont protégées.")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}

/** Relance la surveillance après un redémarrage du téléphone ou une mise à jour de D-Lock. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        MonitorService.start(context)
    }
}
