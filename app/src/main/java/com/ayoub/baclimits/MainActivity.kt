package com.ayoub.baclimits

import android.os.Bundle
import com.ayoub.baclimits.data.LiteKotlinLimitsEngine
import com.ayoub.baclimits.data.OfflineLimitsEngineHolder

/**
 * 2BAC Limits — Lite Edition Entry Point.
 * Ultra-lightweight APK (~2.4 MB), fast instant start, backed by the local Kotlin CAS engine
 * and synced with the cloud backend whenever online.
 */
class MainActivity : CoreMainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        OfflineLimitsEngineHolder.set(LiteKotlinLimitsEngine)
        super.onCreate(savedInstanceState)
    }
}
