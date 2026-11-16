package com.ayoub.baclimits.full

import android.os.Bundle
import com.ayoub.baclimits.CoreMainActivity
import com.ayoub.baclimits.data.OfflineLimitsEngineHolder

/**
 * 2BAC Limits — Full Edition Entry Point.
 * Carries the complete Python 3.11 + SymPy procedural CAS arsenal for 100% exact offline generation.
 */
class MainActivity : CoreMainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        OfflineLimitsEngineHolder.set(FullPythonLimitsEngine(applicationContext))
        super.onCreate(savedInstanceState)
    }
}
