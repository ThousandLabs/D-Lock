package com.thousandlabs.dlock.data

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/** Empreinte digitale (celle déjà enregistrée dans le téléphone). */
object Bio {
    fun available(context: Context): Boolean {
        val bm = context.getSystemService(BiometricManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 30) {
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    fun prompt(activity: Activity, title: String, onSuccess: () -> Unit) {
        if (activity.isFinishing) return
        val executor = activity.mainExecutor
        runCatching {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle("Posez votre doigt sur le capteur")
                .setNegativeButton("Utiliser le code", executor) { _, _ -> }
                .build()
            prompt.authenticate(CancellationSignal(), executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }
            })
        }
    }
}
