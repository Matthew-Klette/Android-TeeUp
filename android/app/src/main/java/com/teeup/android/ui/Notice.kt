package com.teeup.android.ui

import android.app.Activity
import androidx.annotation.StringRes

/**
 * A banner message from a ViewModel. [text] is the API's own message when there is one;
 * otherwise [fallback] is shown, resolved by the Activity so it follows the in-app language
 * (an Application context isn't wrapped by LocaleManager).
 */
data class Notice(val text: String?, @param:StringRes val fallback: Int, val isError: Boolean = false)

/** Shows [notice] as a [TeeUpBanner]. */
fun Activity.showNotice(notice: Notice) {
    TeeUpBanner.show(this, notice.text?.takeIf { it.isNotBlank() } ?: getString(notice.fallback), notice.isError)
}
