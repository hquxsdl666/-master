package com.tcmpulse.pulseapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PulseApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // 初始化应用
    }
}
