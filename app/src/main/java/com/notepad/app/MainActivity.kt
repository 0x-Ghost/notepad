package com.notepad.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.notepad.app.ui.navigation.NotepadNavGraph
import com.notepad.app.ui.screens.LockScreen
import com.notepad.app.ui.theme.NotepadTheme
import com.notepad.app.ui.viewmodel.NoteViewModel
import com.notepad.app.ui.viewmodel.NoteViewModelFactory
import com.notepad.app.ui.viewmodel.SettingsViewModel
import com.notepad.app.ui.viewmodel.SettingsViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val incomingShareText = mutableStateOf<String?>(null)
    private val pendingOpenNoteId = mutableLongStateOf(-1L)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        incomingShareText.value = extractShareText(intent)
        pendingOpenNoteId.longValue = intent.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L)

        val application = application as NotepadApplication
        val lockRepository = application.lockRepository

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                lockRepository.lockEnabled.collect { enabled ->
                    applyFlagSecure(enabled)
                }
            }
        }

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(
                    application.settingsRepository,
                    application.lockRepository
                )
            )
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val lockEnabled by lockRepository.lockEnabled.collectAsState()
            val isUnlocked by lockRepository.isUnlocked.collectAsState()
            val biometricEnabled by lockRepository.biometricEnabled.collectAsState()
            val databaseMigrating by lockRepository.databaseMigrating.collectAsState()

            NotepadTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (lockEnabled && !isUnlocked) {
                        LockScreen(
                            lockRepository = lockRepository,
                            biometricEnabled = biometricEnabled,
                            onUnlocked = { }
                        )
                    } else if (databaseMigrating || !application.isDatabaseReady()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val navController = rememberNavController()
                        val noteViewModel: NoteViewModel = viewModel(
                            factory = NoteViewModelFactory(
                                application.repository,
                                application.settingsRepository,
                                application.reminderScheduler,
                                application.applicationContext
                            )
                        )

                        LaunchedEffect(incomingShareText.value, isUnlocked) {
                            if (!isUnlocked) return@LaunchedEffect
                            incomingShareText.value?.let { text ->
                                noteViewModel.handleShareIntent(text)
                                incomingShareText.value = null
                            }
                        }

                        LaunchedEffect(pendingOpenNoteId.longValue, isUnlocked) {
                            if (!isUnlocked) return@LaunchedEffect
                            val noteId = pendingOpenNoteId.longValue
                            if (noteId > 0L) {
                                navController.navigate(
                                    com.notepad.app.ui.navigation.Routes.noteDetail(noteId)
                                )
                                pendingOpenNoteId.longValue = -1L
                            }
                        }

                        NotepadNavGraph(
                            navController = navController,
                            noteViewModel = noteViewModel,
                            settingsViewModel = settingsViewModel,
                            application = application
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val lockRepository = (application as NotepadApplication).lockRepository
        if (lockRepository.isLockEnabled() && lockRepository.isSessionUnlocked()) {
            lockRepository.wentToBackgroundAt = System.currentTimeMillis()
        }
    }

    override fun onStart() {
        super.onStart()
        val lockRepository = (application as NotepadApplication).lockRepository
        if (lockRepository.shouldRelock()) {
            lockRepository.lockSession()
        } else {
            lockRepository.wentToBackgroundAt = 0L
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingShareText.value = extractShareText(intent)
        val noteId = intent.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L)
        if (noteId > 0L) pendingOpenNoteId.longValue = noteId
    }

    private fun applyFlagSecure(lockEnabled: Boolean) {
        if (lockEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun extractShareText(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            return intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        }
        return null
    }

    companion object {
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"

        fun noteIntent(context: Context, noteId: Long): Intent {
            return Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_NOTE_ID, noteId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }
}
