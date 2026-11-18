package com.thousandlabs.dlock

import android.app.Application
import com.thousandlabs.dlock.data.Store

class DLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
