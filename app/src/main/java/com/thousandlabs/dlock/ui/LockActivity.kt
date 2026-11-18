package com.thousandlabs.dlock.ui

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thousandlabs.dlock.data.Apps
import com.thousandlabs.dlock.data.Store
import com.thousandlabs.dlock.data.Vpn
import com.thousandlabs.dlock.service.MonitorService

/** Écran affiché par-dessus une appli verrouillée. */
class LockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_MODE = "mode"
        const val MODE_CODE = "code"
        const val MODE_VPN = "vpn"
    }

    private var target by mutableStateOf("")
    private var mode by mutableStateOf(MODE_CODE)
    private var generation by mutableStateOf(0)
    private var vpnCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        readIntent(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            DLockTheme {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    androidx.compose.runtime.key(target, mode, generation) {
                        LockContent()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
        generation++
    }

    private fun readIntent(i: Intent?) {
        target = i?.getStringExtra(EXTRA_PKG) ?: ""
        mode = i?.getStringExtra(EXTRA_MODE) ?: MODE_CODE
        if (target.isEmpty()) finish()
    }

    override fun onStart() {
        super.onStart()
        // VPN activé pendant que l'écran est affiché : YouTube s'ouvre aussitôt
        vpnCallback = Vpn.watch(this) {
            if (mode == MODE_VPN && Vpn.isActive(this)) finish()
        }
        if (mode == MODE_VPN && Vpn.isActive(this)) finish()
    }

    override fun onStop() {
        Vpn.unwatch(this, vpnCallback)
        vpnCallback = null
        super.onStop()
        // on a quitté l'écran (accueil, Privado…) : il réapparaîtra si besoin
        if (!isChangingConfigurations) finish()
    }

    private fun goHome() {
        MonitorService.instance?.cancelReturn()
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
        finish()
    }

    private fun unlocked() {
        MonitorService.instance?.markUnlocked(target)
        finish()
    }

    @Composable
    private fun LockContent() {
        val appName = remember(target) { Apps.label(this, target) ?: "Cette appli" }
        var showCode by remember { mutableStateOf(mode == MODE_CODE) }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(target, 64.dp)
            Spacer(Modifier.height(12.dp))
            Text(appName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextMain, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))

            if (showCode) {
                if (mode == MODE_VPN) {
                    Text(
                        "Accès exceptionnel sans VPN",
                        color = Warn, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                UnlockPanel(this@LockActivity, "Déverrouiller $appName", onSuccess = { unlocked() })
                Spacer(Modifier.height(16.dp))
                if (mode == MODE_VPN) {
                    TextButton(onClick = { showCode = false }) { Text("Retour") }
                }
                TextButton(onClick = { goHome() }) {
                    Icon(Icons.Outlined.Home, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retour à l'accueil")
                }
            } else {
                VpnBlock(appName, onOverride = { showCode = true })
            }
        }
    }

    @Composable
    private fun VpnBlock(appName: String, onOverride: () -> Unit) {
        val vpnPkg = remember { Apps.vpnApp(this) }
        val vpnName = remember(vpnPkg) { vpnPkg?.let { Apps.label(this, it) } ?: "le VPN" }
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.VpnLock, contentDescription = null, tint = Accent, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("VPN requis", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
            Spacer(Modifier.height(8.dp))
            Text(
                "$appName ne s'ouvre qu'avec $vpnName activé. Activez-le pour continuer" +
                    if (Store.ytAutoReturn) " : $appName se rouvrira tout seul." else ".",
                color = TextDim, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (vpnPkg != null) Apps.launch(this@LockActivity, vpnPkg) },
                enabled = vpnPkg != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.VpnLock, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(if (vpnPkg != null) "Activer $vpnName" else "Appli VPN introuvable")
            }
            if (vpnPkg == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Choisissez votre appli VPN dans D-Lock, rubrique YouTube.",
                    color = Warn, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }
            if (Store.ytOverride) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onOverride, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.VpnKey, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Accès exceptionnel avec le code")
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { goHome() }) {
                Icon(Icons.Outlined.Home, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Retour à l'accueil")
            }
        }
    }
}
