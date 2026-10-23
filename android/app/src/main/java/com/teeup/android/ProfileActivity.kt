package com.teeup.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.teeup.android.data.AuthSession
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.nav.BottomNav
import com.teeup.android.profile.ProfileActions
import com.teeup.android.profile.ProfileDestination
import com.teeup.android.profile.ProfileScreen
import com.teeup.android.profile.ProfileViewModel
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.LocaleManager
import com.teeup.android.ui.TeeUpBanner
import com.teeup.android.ui.showNotice
import kotlinx.coroutines.launch
import java.io.File

/** Screen 4 · Profile & Settings. Every row here is a real screen, see each Activity's
 *  own doc comment for what's genuinely backed by the API vs. on-device only. The UI is
 *  [ProfileScreen] and the profile header's data is [ProfileViewModel]; this Activity keeps the
 *  photo pickers, which need registerForActivityResult (Google, n.d.d). */
class ProfileActivity : LocaleComponentActivity() {
    companion object {
        private const val KEY_PENDING_CAMERA_FILE = "pendingCameraFile"
    }

    private val viewModel: ProfileViewModel by viewModels()

    // Set right before the camera intent launches; onActivityResult has no way to
    // hand back the file it wrote to itself, so this is how launchCamera() and the
    // takePicture callback agree on which file was used.
    private var pendingCameraFile: File? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            TeeUpBanner.show(this, getString(R.string.profile_photo_camera_permission_denied), isError = true)
        }
    }

    // Camera app writes the photo to the FileProvider Uri we hand it (Google, n.d.a).
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null) {
            viewModel.savePhoto(Uri.fromFile(file))
        }
    }

    private val pickGalleryImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.savePhoto(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // Survives Android recreating this Activity while the camera app is open.
        // Without it, the takePicture callback finds pendingCameraFile null and
        // drops an otherwise-successful capture.
        pendingCameraFile = savedInstanceState?.getString(KEY_PENDING_CAMERA_FILE)?.let { File(it) }

        val actions = ProfileActions(
            open = ::openDestination,
            takePhoto = ::requestCameraAndLaunch,
            choosePhoto = { pickGalleryImageLauncher.launch("image/*") },
            setLanguage = { tag ->
                LocaleManager.setLanguageTag(this, tag)
                restartAppAtHome()
            },
            signOut = { AuthSession.signOut(this) },
            openTab = { tab -> BottomNav.open(this, tab) }
        )
        val languageTag = LocaleManager.getLanguageTag(this)
        setContent { TeeUpTheme { ProfileScreen(viewModel, languageTag, actions) } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { showNotice(it) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingCameraFile?.let { outState.putString(KEY_PENDING_CAMERA_FILE, it.absolutePath) }
    }

    /** Personal/Playing/Notification Details are separate Activities on the back stack. This
     *  Activity is only resumed, not recreated, when the user backs out of one after saving, so
     *  refreshing on every resume picks up edits made on those screens. */
    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun openDestination(destination: ProfileDestination) {
        val target = when (destination) {
            ProfileDestination.PERSONAL -> PersonalDetailsActivity::class.java
            ProfileDestination.PLAYING -> PlayingDetailsActivity::class.java
            ProfileDestination.NOTIFICATIONS -> NotificationPreferencesActivity::class.java
            ProfileDestination.BIOMETRIC -> BiometricLoginActivity::class.java
            ProfileDestination.OFFLINE_SYNC -> OfflineSyncStatusActivity::class.java
            ProfileDestination.PRIVACY -> PrivacyDataActivity::class.java
        }
        startActivity(Intent(this, target))
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
    }

    private fun requestCameraAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val file = File(filesDir, "profile_photo_${System.currentTimeMillis()}.jpg")
        pendingCameraFile = file
        // Shares this private-storage file with the camera app as a content:// Uri (Google, n.d.c)
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        takePictureLauncher.launch(uri)
    }

    /** Every open Activity was created under the old Locale, so the whole task is
     *  relaunched fresh at Home rather than just recreating this one screen. */
    private fun restartAppAtHome() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}

/*
References:

Google (n.d.a). ActivityResultContracts.TakePicture. [online] Android Developers. Available at: <https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.TakePicture> [Accessed 22 Sep. 2026].

Google (n.d.c). FileProvider. [online] Android Developers. Available at: <https://developer.android.com/reference/kotlin/androidx/core/content/FileProvider> [Accessed 22 Sep. 2026].

Google (n.d.d). Get a result from an activity. [online] Android Developers. Available at: <https://developer.android.com/training/basics/intents/result> [Accessed 22 Sep. 2026].
*/
