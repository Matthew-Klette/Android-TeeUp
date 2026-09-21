package com.teeup.android

import android.app.Application

/** Holds an app-wide Context so singletons like DevIdentity can reach SharedPreferences
 *  without every call site threading one through. */
class TeeUpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
    }

    companion object {
        lateinit var appContext: Application
            private set
    }
}
