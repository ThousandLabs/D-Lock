package com.thousandlabs.dlock.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thousandlabs.dlock.BuildConfig
import com.thousandlabs.dlock.data.AppEntry
import com.thousandlabs.dlock.data.Apps
import com.thousandlabs.dlock.data.Bio
import com.thousandlabs.dlock.data.Store
import com.thousandlabs.dlock.data.Vpn
import com.thousandlabs.dlock.service.MonitorService
import com.thousandlabs.dlock.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val REPO_URL = "https://github.com/ThousandLabs/D-Lock"
const val SITE_URL = "https://thousandlabs.github.io"

private enum class Screen { HOME, APPS, CHANGE_CODE, GUIDE, ABOUT }

@Composable
fun MainRoot(activity: ComponentActivity, unlocked: Boolean, onUnlocked: () -> Unit, resumeTick: Int) {
    var hasSecret by remember { mutableStateOf(Store.hasSecret()) }
    when {
        !hasSecret -> Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
            SetupScreen(
                title = "Bienvenue dans D-Lock",
                intro = "Choisissez comment déverrouiller vos applis et D-Lock lui-même. Vous pourrez changer plus tard.",
                onDone = { hasSecret = true; onUnlocked() },
                onCancel = null,
            )
        }
        !unlocked -> Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("D-Lock", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Spacer(Modifier.height(20.dp))
            UnlockPanel(activity, "Ouvrir D-Lock", onSuccess = onUnlocked)
        }
        else -> Navigation(activity, resumeTick)
    }
}

@Composable
private fun Navigation(activity: ComponentActivity, resumeTick: Int) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        when (screen) {
            Screen.HOME -> HomeScreen(activity, resumeTick) { screen = it }
            Screen.APPS -> AppsScreen { screen = Screen.HOME }
            Screen.CHANGE_CODE -> SetupScreen(
                title = "Changer de code",
                intro = "Choisissez la nouvelle méthode de déverrouillage.",
                onDone = { screen = Screen.HOME },
                onCancel = { screen = Screen.HOME },
            )
            Screen.GUIDE -> GuideScreen { screen = Screen.HOME }
            Screen.ABOUT -> AboutScreen(activity) { screen = Screen.HOME }
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
private fun HomeScreen(activity: ComponentActivity, resumeTick: Int, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val usageOk = remember(resumeTick) { MonitorService.hasUsageAccess(context) }
    val overlayOk = remember(resumeTick) { MonitorService.hasOverlay(context) }
    val batteryOk = remember(resumeTick) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val ytInstalled = remember(resumeTick) { Apps.isInstalled(context, Store.YOUTUBE) }
    var vpnPkg by remember(resumeTick) { mutableStateOf(Apps.vpnApp(context)) }
    val vpnName = vpnPkg?.let { Apps.label(context, it) }
    val vpnActive = remember(resumeTick) { mutableStateOf(Vpn.isActive(context)) }
    val lockedCount = remember(resumeTick) { Store.lockedApps.size }
    var ytVpn by remember { mutableStateOf(Store.ytVpnRequired) }
    var ytOverride by remember { mutableStateOf(Store.ytOverride) }
    var ytReturn by remember { mutableStateOf(Store.ytAutoReturn) }
    var bio by remember { mutableStateOf(Store.biometric) }
    val bioAvailable = remember(resumeTick) { Bio.available(context) }
    var pickVpn by remember { mutableStateOf(false) }
    val update by Updater.state.collectAsState()
    val bannerDismissed by Updater.bannerDismissed.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("D-Lock", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Text("Verrouillage d'applis", color = TextDim, fontSize = 13.sp)
            }
        }

        val avail = update as? Updater.State.Available
        if (avail != null && !bannerDismissed) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card2)
                    .clickable { go(Screen.ABOUT) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(10.dp))
                Text("Nouvelle version ${avail.versionName} disponible", color = TextMain, modifier = Modifier.weight(1f))
                TextButton(onClick = { Updater.dismissBanner() }) { Text("Plus tard") }
            }
        }

        SectionCard("Protection", Icons.Outlined.Shield) {
            val allOk = usageOk && overlayOk
            StatusLine(allOk, if (allOk) "D-Lock surveille l'ouverture des applis" else "Deux autorisations manquent : rien n'est verrouillé")
            StatusLine(usageOk, "Accès aux données d'utilisation")
            if (!usageOk) {
                Text(
                    "Permet de savoir quelle appli vient d'être ouverte. Dans la liste, choisissez D-Lock et activez l'accès.",
                    color = TextDim, fontSize = 13.sp,
                )
                Button(onClick = {
                    val withPkg = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    runCatching { activity.startActivity(withPkg) }.onFailure {
                        runCatching { activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Autoriser l'accès aux données d'utilisation") }
            }
            StatusLine(overlayOk, "Affichage par-dessus les autres applis")
            if (!overlayOk) {
                Text(
                    "Permet d'afficher l'écran de verrouillage devant l'appli ouverte.",
                    color = TextDim, fontSize = 13.sp,
                )
                Button(onClick = {
                    runCatching {
                        activity.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Autoriser l'affichage par-dessus") }
            }
            StatusLine(batteryOk, if (batteryOk) "Fonctionne en arrière-plan sans restriction" else "Batterie : D-Lock risque d'être fermé par le téléphone")
            if (!batteryOk) {
                OutlinedButton(onClick = {
                    runCatching {
                        activity.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                        )
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Autoriser en arrière-plan")
                }
            }
        }

        SectionCard("YouTube (clipping)", Icons.Outlined.VpnLock) {
            if (!ytInstalled) Text("L'appli YouTube officielle n'est pas installée.", color = Warn, fontSize = 13.sp)
            SwitchRow(
                "Exiger le VPN pour ouvrir YouTube",
                "Seule l'appli YouTube officielle est concernée (pas ReVanced).",
                ytVpn,
            ) { ytVpn = it; Store.ytVpnRequired = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Appli VPN", color = TextMain, fontSize = 15.sp)
                    Text(vpnName ?: "Aucune trouvée", color = if (vpnName != null) TextDim else Warn, fontSize = 13.sp)
                }
                TextButton(onClick = { pickVpn = true }) { Text("Changer") }
            }
            StatusLine(vpnActive.value, if (vpnActive.value) "VPN actuellement activé" else "VPN actuellement désactivé")
            SwitchRow(
                "Accès exceptionnel avec le code",
                "Sans VPN, permet quand même d'ouvrir YouTube avec votre code.",
                ytOverride, enabled = ytVpn,
            ) { ytOverride = it; Store.ytOverride = it }
            SwitchRow(
                "Rouvrir YouTube une fois le VPN activé",
                "Après « Activer le VPN », YouTube revient tout seul dès la connexion.",
                ytReturn, enabled = ytVpn,
            ) { ytReturn = it; Store.ytAutoReturn = it }
        }

        SectionCard("Applis verrouillées", Icons.Outlined.Apps) {
            Text(
                when (lockedCount) {
                    0 -> "Aucune appli verrouillée pour l'instant."
                    1 -> "1 appli demande votre code à l'ouverture."
                    else -> "$lockedCount applis demandent votre code à l'ouverture."
                },
                color = TextDim, fontSize = 14.sp,
            )
            Text("Pas de VPN demandé pour ces applis : seulement votre code ou votre empreinte.", color = TextDim, fontSize = 13.sp)
            Button(onClick = { go(Screen.APPS) }, modifier = Modifier.fillMaxWidth()) { Text("Choisir les applis") }
        }

        SectionCard("Sécurité", Icons.Outlined.Security) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Store.method.icon(), contentDescription = null, tint = TextDim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Méthode : ${Store.method.label}", color = TextMain, modifier = Modifier.weight(1f))
                TextButton(onClick = { go(Screen.CHANGE_CODE) }) { Text("Changer") }
            }
            SwitchRow(
                "Déverrouiller avec l'empreinte",
                if (bioAvailable) "Utilise l'empreinte déjà enregistrée dans le téléphone." else "Aucune empreinte enregistrée dans le téléphone.",
                bio && bioAvailable, enabled = bioAvailable,
            ) { bio = it; Store.biometric = it }
            Text(
                "Après 5 erreurs, la saisie est bloquée 30 secondes (puis de plus en plus longtemps).",
                color = TextDim, fontSize = 13.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { go(Screen.GUIDE) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Comment faire")
            }
            OutlinedButton(onClick = { go(Screen.ABOUT) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("À propos")
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (pickVpn) {
        AppPickerDialog(
            title = "Choisir l'appli VPN",
            onDismiss = { pickVpn = false },
            onPick = { pkg ->
                Store.vpnPackage = pkg
                vpnPkg = pkg
                pickVpn = false
            },
        )
    }
}

@Composable
private fun rememberApps(): List<AppEntry>? {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { Apps.launchable(context) }
    }
    return apps
}

@Composable
private fun AppPickerDialog(title: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val apps = rememberApps()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (apps == null) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = { it.pkg }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(app.pkg) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.pkg, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, color = TextMain)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour", tint = TextMain) }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
    }
}

@Composable
private fun AppsScreen(onBack: () -> Unit) {
    val apps = rememberApps()
    var query by remember { mutableStateOf("") }
    var locked by remember { mutableStateOf(Store.lockedApps) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Applis verrouillées", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Rechercher une appli") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Text(
            "YouTube officiel n'apparaît pas ici : il est géré par la règle VPN. ReVanced et les autres se verrouillent par code.",
            color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (apps == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val q = query.trim()
            val list = apps.filter { it.pkg != Store.YOUTUBE && (q.isEmpty() || it.label.contains(q, ignoreCase = true)) }
                .sortedByDescending { it.pkg in locked }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(list, key = { it.pkg }) { app ->
                    val on = app.pkg in locked
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            Store.setLocked(app.pkg, !on); locked = Store.lockedApps
                        }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.pkg, 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(app.label, color = TextMain, modifier = Modifier.weight(1f))
                        Switch(
                            checked = on,
                            onCheckedChange = { Store.setLocked(app.pkg, it); locked = Store.lockedApps },
                            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideBlock(title: String, lines: List<String>) {
    SectionCard(title, Icons.AutoMirrored.Outlined.HelpOutline) {
        lines.forEachIndexed { i, l ->
            Row {
                Text("${i + 1}.", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(24.dp))
                Text(l, color = TextMain, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun GuideScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Comment faire", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GuideBlock(
                "Mise en route (une seule fois)",
                listOf(
                    "Installez D-Lock et créez votre code (PIN, schéma ou mot de passe).",
                    "Touchez « Autoriser l'accès aux données d'utilisation », choisissez D-Lock dans la liste et activez-le. D-Lock sait ainsi quelle appli vient d'être ouverte.",
                    "Touchez « Autoriser l'affichage par-dessus » et activez-le : c'est ce qui permet d'afficher l'écran de verrouillage.",
                    "Touchez « Autoriser en arrière-plan » pour que le téléphone ne ferme pas D-Lock. Si HiOS le ferme quand même, autorisez son démarrage automatique dans Phone Master.",
                    "Une notification discrète « D-Lock actif » reste affichée : Android l'exige pour que la surveillance continue. Vous pouvez la masquer dans les réglages de notification de D-Lock sans arrêter la protection.",
                ),
            )
            GuideBlock(
                "YouTube et le VPN",
                listOf(
                    "Avec Privado VPN activé, YouTube s'ouvre normalement.",
                    "Sans VPN, un écran de blocage s'affiche : touchez « Activer Privado VPN », connectez-vous, et YouTube se rouvre tout seul.",
                    "Si le VPN se coupe pendant que vous regardez YouTube, l'écran de blocage revient aussitôt.",
                    "En cas de besoin, « Accès exceptionnel avec le code » ouvre YouTube sans VPN (désactivable).",
                    "Seule l'appli YouTube officielle est concernée : ReVanced n'est jamais soumise au VPN.",
                ),
            )
            GuideBlock(
                "Verrouiller d'autres applis",
                listOf(
                    "Touchez « Choisir les applis » et activez les applis à protéger.",
                    "À l'ouverture, elles demandent votre code ou votre empreinte, sans VPN.",
                    "Une appli déverrouillée le reste tant que vous ne la quittez pas. Elle se reverrouille quand vous changez d'appli ou éteignez l'écran.",
                ),
            )
            GuideBlock(
                "Conseils",
                listOf(
                    "Pour éviter de désactiver D-Lock sur un coup de tête, verrouillez aussi l'appli Paramètres.",
                    "Si vous oubliez votre code, il faut effacer les données de D-Lock (Paramètres › Applis › D-Lock › Stockage) puis le reconfigurer.",
                    "D-Lock ne lit rien à l'écran et n'envoie aucune donnée : il voit seulement le nom de l'appli ouverte. Internet ne sert qu'aux mises à jour.",
                ),
            )
            SectionCard("Nouveautés", Icons.Outlined.SystemUpdate) {
                Text("Version 1.2", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "D-Lock n'utilise plus le service d'accessibilité, que Google Play Protect bloquait à l'installation. " +
                        "Il fonctionne désormais avec « Accès aux données d'utilisation » et « Affichage par-dessus les autres applis », " +
                        "sans « paramètres restreints » à débloquer. Relance automatique après un redémarrage du téléphone.",
                    color = TextDim, fontSize = 13.sp,
                )
                Text("Version 1.1 : première version de D-Lock.", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "YouTube officiel réservé au VPN (Privado), avec retour automatique après connexion et accès exceptionnel par code ; " +
                        "verrouillage des autres applis par code PIN, schéma, mot de passe ou empreinte ; D-Lock protégé par le même code ; " +
                        "blocage temporaire après 5 erreurs ; mises à jour intégrées.",
                    color = TextDim, fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AboutScreen(activity: ComponentActivity, onBack: () -> Unit) {
    val update by Updater.state.collectAsState()
    var auto by remember { mutableStateOf(Updater.autoEnabled()) }
    fun open(url: String) = runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    Column(Modifier.fillMaxSize()) {
        TopBar("À propos", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard("D-Lock ${BuildConfig.VERSION_NAME}", Icons.Outlined.Lock) {
                Text("Une appli Thousand Labs.", color = TextDim)
                OutlinedButton(onClick = { open(REPO_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Page officielle sur GitHub")
                }
                OutlinedButton(onClick = { open(SITE_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Site Thousand Labs")
                }
            }
            SectionCard("Mises à jour", Icons.Outlined.SystemUpdate) {
                SwitchRow("Vérifier automatiquement", "À l'ouverture de D-Lock, avec Internet.", auto) {
                    auto = it; Updater.setAutoEnabled(it)
                }
                when (val s = update) {
                    is Updater.State.Idle -> Text("Version installée : ${BuildConfig.VERSION_NAME}", color = TextDim)
                    is Updater.State.Checking -> Text("Recherche en cours…", color = TextDim)
                    is Updater.State.UpToDate -> StatusLine(true, "D-Lock est à jour.")
                    is Updater.State.Available -> {
                        Text("Version ${s.versionName} disponible.", color = TextMain, fontWeight = FontWeight.SemiBold)
                        if (s.notes.isNotBlank()) Text(s.notes, color = TextDim, fontSize = 13.sp)
                    }
                    is Updater.State.Downloading -> {
                        Text("Téléchargement… ${(s.progress * 100).toInt()} %", color = TextDim)
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is Updater.State.Installing -> Text("Installation… Confirmez dans la fenêtre d'Android.", color = TextDim)
                    is Updater.State.Error -> Text(s.message, color = Danger)
                }
                val s = update
                val canInstall = s is Updater.State.Available || (s is Updater.State.Error && s.release != null)
                if (canInstall) {
                    Button(onClick = {
                        if (!Updater.canInstall(activity)) {
                            runCatching { activity.startActivity(Updater.installPermissionIntent(activity)) }
                        } else {
                            Updater.downloadAndInstall(activity)
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Installer la mise à jour") }
                    if (!Updater.canInstall(activity)) {
                        Text(
                            "Autorisez d'abord D-Lock à installer des applis, puis revenez toucher le bouton.",
                            color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Start,
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { Updater.check() },
                        enabled = s !is Updater.State.Checking && s !is Updater.State.Downloading && s !is Updater.State.Installing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Rechercher une mise à jour") }
                }
            }
        }
    }
}
