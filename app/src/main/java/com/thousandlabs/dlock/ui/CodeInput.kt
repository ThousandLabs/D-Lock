package com.thousandlabs.dlock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thousandlabs.dlock.data.LockMethod

/**
 * Saisie du code selon la méthode choisie.
 * [autoLength] > 0 : le code PIN est vérifié automatiquement dès qu'il a cette longueur.
 */
@Composable
fun CodeInput(method: LockMethod, enabled: Boolean, autoLength: Int, onSubmit: (String) -> Unit) {
    when (method) {
        LockMethod.PIN -> PinInput(enabled, autoLength, onSubmit)
        LockMethod.PATTERN -> PatternPad(enabled, onSubmit)
        LockMethod.PASSWORD -> PasswordInput(enabled, onSubmit)
    }
}

@Composable
private fun PinInput(enabled: Boolean, autoLength: Int, onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.height(18.dp)) {
            val slots = if (autoLength > 0) autoLength else maxOf(4, pin.length)
            repeat(slots) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(if (i < pin.length) Accent else Card2)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("<", "0", "ok"))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { k ->
                        when (k) {
                            "<" -> PadKey(icon = Icons.AutoMirrored.Outlined.Backspace, enabled = enabled && pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                            }
                            "ok" -> PadKey(icon = Icons.Outlined.Check, enabled = enabled && pin.length >= 4) {
                                onSubmit(pin)
                            }
                            else -> PadKey(label = k, enabled = enabled) {
                                if (pin.length < 8) {
                                    pin += k
                                    if (autoLength > 0 && pin.length == autoLength) onSubmit(pin)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(label: String? = null, icon: ImageVector? = null, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(Card)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = if (enabled) TextMain else TextDim)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) TextMain else TextDim.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun PasswordInput(enabled: Boolean, onSubmit: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            enabled = enabled,
            singleLine = true,
            label = { Text("Mot de passe") },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (value.isNotEmpty()) onSubmit(value) }),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSubmit(value) }, enabled = enabled && value.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Valider")
        }
    }
}

/** Grille de 3 × 3 points à relier, comme le verrouillage d'Android. */
@Composable
private fun PatternPad(enabled: Boolean, onSubmit: (String) -> Unit) {
    var selected by remember { mutableStateOf(listOf<Int>()) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val submit by rememberUpdatedState(onSubmit)
    val isEnabled by rememberUpdatedState(enabled)

    fun center(i: Int): Offset {
        val cell = size.width / 3f
        return Offset(cell * (i % 3) + cell / 2f, cell * (i / 3) + cell / 2f)
    }

    fun hit(p: Offset): Int? {
        val radius = size.width / 3f * 0.32f
        return (0..8).firstOrNull { (center(it) - p).getDistance() < radius }
    }

    fun add(i: Int) {
        if (i in selected) return
        val last = selected.lastOrNull()
        var list = selected
        if (last != null) {
            // point du milieu traversé automatiquement (ex. de 0 à 2 passe par 1)
            val sameParity = (last % 3 + i % 3) % 2 == 0 && (last / 3 + i / 3) % 2 == 0
            val mid = (last + i) / 2
            if (sameParity && mid !in list) list = list + mid
        }
        selected = list + i
    }

    Canvas(
        Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { p ->
                        if (!isEnabled) return@detectDragGestures
                        selected = emptyList()
                        hit(p)?.let { add(it) }
                        finger = p
                    },
                    onDrag = { change, _ ->
                        if (!isEnabled) return@detectDragGestures
                        finger = change.position
                        hit(change.position)?.let { add(it) }
                    },
                    onDragEnd = {
                        val s = selected
                        finger = null
                        selected = emptyList()
                        if (isEnabled && s.isNotEmpty()) submit(s.joinToString(""))
                    },
                    onDragCancel = {
                        finger = null
                        selected = emptyList()
                    },
                )
            }
    ) {
        val cell = this.size.width / 3f
        for (k in 1 until selected.size) {
            drawLine(Accent, center(selected[k - 1]), center(selected[k]), strokeWidth = 8f, cap = StrokeCap.Round)
        }
        val f = finger
        val last = selected.lastOrNull()
        if (f != null && last != null) {
            drawLine(Accent.copy(alpha = 0.5f), center(last), f, strokeWidth = 8f, cap = StrokeCap.Round)
        }
        for (i in 0..8) {
            val on = i in selected
            drawCircle(if (on) Accent.copy(alpha = 0.22f) else Card, radius = cell * 0.22f, center = center(i))
            drawCircle(if (on) Accent else TextDim, radius = cell * 0.07f, center = center(i))
        }
    }
}

/** Carte de choix utilisée à la configuration. */
@Composable
fun ChoiceCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)
            .clickable(onClick = onClick).padding16(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, color = TextMain)
            Text(subtitle, color = TextDim, fontSize = 13.sp)
        }
    }
}
