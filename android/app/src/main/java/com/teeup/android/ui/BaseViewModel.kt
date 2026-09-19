package com.teeup.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel

/** Every ViewModel logs its creation/teardown so state transitions are traceable in Logcat. */
abstract class BaseViewModel : ViewModel() {
    protected val tag: String = javaClass.simpleName

    init {
        Log.d(tag, "created")
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(tag, "cleared")
    }
}
