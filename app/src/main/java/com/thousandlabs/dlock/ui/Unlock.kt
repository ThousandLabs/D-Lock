package com.thousandlabs.dlock.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Pattern
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thousandlabs.dlock.data.Bio
import com.thousandlabs.dlock.data.LockMethod
import com.thousandlabs.dlock.data.Store
import kotlinx.coroutines.delay

/** Bloc de déverrouillage : saisie du code, empreinte, blocage après trop d'essais. */
@Composable
fun UnlockPanel(activity: Activity, bioTitle: String, onSuccess: () -> Unit, autoBiometric: Boolean = true) {
    val method = remember { Store.method }
    var message by remember { mutableStateOf<String?>(null) }
    var lockLeft by remember { mutableLongStateOf(Store.lockoutRemainingMs()) }
    var resetKey by remember { mutableIntStateOf(0) }
    val bio = remember { Store.biometric && Bio.available(activity) }
    val success = {
        Store.resetFailures()
        onSuccess()
    }

    LaunchedEffect(lockLeft > 0) {
        while (lockLeft > 0) {
            delay(250)
            lockLeft = Store.lockoutRemainingMs()
        }
    }
    LaunchedEffect(Unit) {
        if (bio && autoBiometric && Store.lockoutRemainingMs() == 0L) Bio.prompt(activity, bioTitle) { success() }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val locked = lockLeft > 0
        Text(
            when {
                locked -> "Trop d'essais. Réessayez dans ${(lockLeft + 999) / 1000} s."
                message != null -> message!!
                else -> method.prompt
            },
            color = if (locked || message != null) Danger else TextDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        key(resetKey) {
            CodeInput(method, enabled = !locked, autoLength = Store.pinLength) { value ->
                if (Store.lockoutRemainingMs() > 0) {
                    lockLeft = Store.lockoutRemainingMs()
                } else if (Store.check(value)) {
                    success()
                } else {
                    Store.registerFailure()
                    message = method.wrong
                    lockLeft = Store.lockoutRemainingMs()
                    resetKey++
                }
            }
        }
        if (bio) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { if (lockLeft == 0L) Bio.prompt(activity, bioTitle) { success() } }) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Utiliser l'empreinte")
            }
        }
    }
}

fun LockMethod.icon() = when (this) {
    LockMethod.PIN -> Icons.Outlined.Dialpad
    LockMethod.PATTERN -> Icons.Outlined.Pattern
    LockMethod.PASSWORD -> Icons.Outlined.Password
}

/** Création (ou changement) du code : choix de la méthode, saisie, confirmation. */
@Composable
fun SetupScreen(title: String, intro: String, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    var method by remember { mutableStateOf<LockMethod?>(null) }
    var first by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var k by remember { mutableIntStateOf(0) }

    BackHandler(enabled = method != null || onCancel != null) {
        if (method != null) {
            method = null; first = null; msg = null
        } else {
            onCancel?.invoke()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextMain, textAlign = TextAlign.Center)
        val m = method
        if (m == null) {
            Text(intro, color = TextDim, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            LockMethod.entries.forEach { lm ->
                ChoiceCard(lm.icon(), lm.label, lm.rule) { method = lm }
            }
            if (onCancel != null) TextButton(onClick = onCancel) { Text("Annuler") }
        } else {
            Icon(m.icon(), contentDescription = null, tint = Accent, modifier = Modifier.size(36.dp))
            Text(
                if (first == null) "Choisissez votre ${m.label.lowercase()}" else "Confirmez votre ${m.label.lowercase()}",
                fontWeight = FontWeight.SemiBold, color = TextMain,
            )
            Text(msg ?: m.rule, color = if (msg != null) Danger else TextDim, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            key(k, first) {
                CodeInput(m, enabled = true, autoLength = 0) { v ->
                    val f = first
                    if (f == null) {
                        val err = m.validate(v)
                        if (err != null) { msg = err; k++ } else { first = v; msg = null }
                    } else if (v == f) {
                        Store.setSecret(m, v)
                        onDone()
                    } else {
                        msg = "Les deux saisies ne correspondent pas. Recommencez."
                        first = null
                        k++
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { method = null; first = null; msg = null }) { Text("Changer de méthode") }
        }
        Spacer(Modifier.fillMaxWidth().height(8.dp))
    }
}
