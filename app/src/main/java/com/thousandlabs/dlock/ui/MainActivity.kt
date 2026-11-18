package com.thousandlabs.dlock.ui

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thousandlabs.dlock.data.Store
import com.thousandlabs.dlock.service.MonitorService
import com.thousandlabs.dlock.update.Updater

class MainActivity : ComponentActivity() {

    /** D-Lock lui-même est protégé par le code. */
    private var unlocked by mutableStateOf(false)
    private var resumeTick by mutableIntStateOf(0)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent {
            DLockTheme {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    MainRoot(this, unlocked, onUnlocked = { unlocked = true }, resumeTick = resumeTick)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // petit délai de grâce pour aller activer un réglage d'Android puis revenir
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > 60_000L) unlocked = false
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        // démarre la surveillance dès que les autorisations sont accordées
        if (Store.hasSecret()) MonitorService.start(this)
        Updater.onAppResumed(this)
        if (unlocked) Updater.autoCheck()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }
}
