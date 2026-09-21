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

        /** Same as [appContext], but null instead of crashing when unset — real app usage
         *  always has it set (Application.onCreate() runs before any Activity), but plain
         *  JVM unit tests (Formatting/InputValidation/RoundModels) never create the
         *  Application at all, so callers there need a way to fall back gracefully. */
        val appContextOrNull: Application?
            get() = if (::appContext.isInitialized) appContext else null
    }
}
