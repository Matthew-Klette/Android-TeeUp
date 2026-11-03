package com.teeup.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.partners.PartnersScreen
import com.teeup.android.partners.PartnersViewModel
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.collectWhileStarted
import com.teeup.android.ui.enableTeeUpEdgeToEdge
import com.teeup.android.ui.finishWithSlideBack
import com.teeup.android.ui.showNotice

/** Profile → Playing Partners (user-defined feature: private groups). UI: [PartnersScreen]. */
class PartnersActivity : LocaleComponentActivity() {
    private val viewModel: PartnersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableTeeUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TeeUpTheme { PartnersScreen(viewModel, onBack = ::finishWithSlideBack) } }
        collectWhileStarted(viewModel.events, ::showNotice)
    }
}
