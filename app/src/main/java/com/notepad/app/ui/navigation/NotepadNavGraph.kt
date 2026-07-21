package com.notepad.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.notepad.app.NotepadApplication
import com.notepad.app.ui.components.UndoDeleteSnackbar
import com.notepad.app.ui.screens.AddEditNoteScreen
import com.notepad.app.ui.screens.ArchivedNotesScreen
import com.notepad.app.ui.screens.DrawingScreen
import com.notepad.app.ui.screens.LabelsScreen
import com.notepad.app.ui.screens.NotesListScreen
import com.notepad.app.ui.screens.SettingsScreen
import com.notepad.app.ui.screens.TrashedNotesScreen
import com.notepad.app.ui.viewmodel.LabelsViewModel
import com.notepad.app.ui.viewmodel.LabelsViewModelFactory
import com.notepad.app.ui.viewmodel.NoteViewModel
import com.notepad.app.ui.viewmodel.SettingsViewModel
import com.notepad.app.util.OcrHelper

private val noteScreenEnter = slideInHorizontally(tween(320)) { it } + fadeIn(tween(320))
private val noteScreenExit = slideOutHorizontally(tween(320)) { it } + fadeOut(tween(320))
private val noteScreenPopEnter = slideInHorizontally(tween(320)) { -it } + fadeIn(tween(320))
private val noteScreenPopExit = slideOutHorizontally(tween(320)) { -it } + fadeOut(tween(320))

@Composable
fun NotepadNavGraph(
    navController: NavHostController,
    noteViewModel: NoteViewModel,
    settingsViewModel: SettingsViewModel,
    application: NotepadApplication
) {
    val sharedText by noteViewModel.sharedTextIntent.collectAsState()
    val undoSnackbarState by noteViewModel.undoSnackbarState.collectAsState()

    LaunchedEffect(sharedText) {
        sharedText?.let { text ->
            noteViewModel.prepareNoteFromSharedText(text)
            navController.navigate(Routes.NOTE_DETAIL) { launchSingleTop = true }
            noteViewModel.clearSharedTextIntent()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.NOTES_LIST) {
            composable(Routes.NOTES_LIST) {
                val notes by noteViewModel.notes.collectAsState()
                val searchQuery by noteViewModel.searchQuery.collectAsState()
                val noteFilter by noteViewModel.noteFilter.collectAsState()
                val noteSortOrder by noteViewModel.noteSortOrder.collectAsState()
                val allLabels by noteViewModel.allLabels.collectAsState()
                val selectedLabelId by noteViewModel.selectedLabelId.collectAsState()

                NotesListScreen(
                    notes = notes,
                    searchQuery = searchQuery,
                    onSearchQueryChange = noteViewModel::onSearchQueryChange,
                    noteFilter = noteFilter,
                    onFilterChange = noteViewModel::setNoteFilter,
                    noteSortOrder = noteSortOrder,
                    onSortOrderChange = noteViewModel::setNoteSortOrder,
                    allLabels = allLabels,
                    selectedLabelId = selectedLabelId,
                    onLabelSelect = noteViewModel::setSelectedLabelId,
                    onAddNote = {
                        noteViewModel.prepareNewNote()
                        navController.navigate(Routes.NOTE_DETAIL)
                    },
                    onNoteClick = { navController.navigate(Routes.noteDetail(it)) },
                    onDeleteNote = noteViewModel::deleteNote,
                    onArchiveNote = noteViewModel::archiveNote,
                    onTogglePinNote = noteViewModel::togglePinNote,
                    onDuplicateNote = noteViewModel::duplicateNote,
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    getChecklistPreview = noteViewModel::getChecklistPreview,
                    getNoteLabels = noteViewModel::getNoteLabels
                )
            }

            composable(
                route = Routes.SETTINGS,
                enterTransition = { noteScreenEnter },
                exitTransition = { noteScreenExit },
                popEnterTransition = { noteScreenPopEnter },
                popExitTransition = { noteScreenPopExit }
            ) {
                val themeMode by settingsViewModel.themeMode.collectAsState()
                val lockEnabled by settingsViewModel.lockEnabled.collectAsState()
                val biometricEnabled by settingsViewModel.biometricEnabled.collectAsState()
                val securityBusy by settingsViewModel.securityBusy.collectAsState()
                val securityMessage by settingsViewModel.securityMessage.collectAsState()
                val restartRequired by settingsViewModel.restartRequired.collectAsState()
                val context = LocalContext.current
                LaunchedEffect(restartRequired) {
                    if (restartRequired) {
                        settingsViewModel.consumeRestartRequired()
                        (context as? android.app.Activity)?.recreate()
                    }
                }
                SettingsScreen(
                    currentTheme = themeMode,
                    onThemeChange = settingsViewModel::setThemeMode,
                    lockEnabled = lockEnabled,
                    biometricEnabled = biometricEnabled,
                    securityBusy = securityBusy,
                    securityMessage = securityMessage,
                    onClearSecurityMessage = settingsViewModel::clearSecurityMessage,
                    onSetupPassword = settingsViewModel::setupPassword,
                    onChangePassword = settingsViewModel::changePassword,
                    onRemovePassword = settingsViewModel::removePassword,
                    onCreateBiometricEncryptCipher = settingsViewModel::createBiometricEncryptCipher,
                    onEnableBiometric = settingsViewModel::enableBiometric,
                    onDisableBiometric = settingsViewModel::disableBiometric,
                    onArchivedNotesClick = { navController.navigate(Routes.ARCHIVED_NOTES) },
                    onTrashClick = { navController.navigate(Routes.TRASH) },
                    onLabelsClick = { navController.navigate(Routes.LABELS) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.LABELS,
                enterTransition = { noteScreenEnter },
                exitTransition = { noteScreenExit },
                popEnterTransition = { noteScreenPopEnter },
                popExitTransition = { noteScreenPopExit }
            ) {
                val labelsViewModel: LabelsViewModel = viewModel(
                    factory = LabelsViewModelFactory(application.repository)
                )
                val labels by labelsViewModel.labels.collectAsState()
                LabelsScreen(
                    labels = labels,
                    onAddLabel = labelsViewModel::addLabel,
                    onUpdateLabel = labelsViewModel::updateLabel,
                    onDeleteLabel = labelsViewModel::deleteLabel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.ARCHIVED_NOTES,
                enterTransition = { noteScreenEnter },
                exitTransition = { noteScreenExit },
                popEnterTransition = { noteScreenPopEnter },
                popExitTransition = { noteScreenPopExit }
            ) {
                val archivedNotes by noteViewModel.archivedNotes.collectAsState()
                ArchivedNotesScreen(
                    notes = archivedNotes,
                    onBack = { navController.popBackStack() },
                    onNoteClick = { navController.navigate(Routes.noteDetail(it)) },
                    onUnarchiveNote = noteViewModel::unarchiveNote,
                    onDeleteNote = noteViewModel::deleteNote
                )
            }

            composable(
                route = Routes.TRASH,
                enterTransition = { noteScreenEnter },
                exitTransition = { noteScreenExit },
                popEnterTransition = { noteScreenPopEnter },
                popExitTransition = { noteScreenPopExit }
            ) {
                val trashedNotes by noteViewModel.trashedNotes.collectAsState()
                TrashedNotesScreen(
                    notes = trashedNotes,
                    onBack = { navController.popBackStack() },
                    onRestoreNote = noteViewModel::restoreFromTrash,
                    onDeletePermanently = noteViewModel::permanentlyDeleteNote,
                    onEmptyTrash = noteViewModel::emptyTrash
                )
            }

            composable(
                route = Routes.DRAWING,
                enterTransition = { noteScreenEnter },
                exitTransition = { noteScreenExit },
                popEnterTransition = { noteScreenPopEnter },
                popExitTransition = { noteScreenPopExit }
            ) {
                DrawingScreen(
                    onDone = { imagePath ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("drawing_result", imagePath)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.NOTE_DETAIL_WITH_ID,
                arguments = listOf(navArgument("noteId") { type = NavType.LongType; defaultValue = 0L }),
                enterTransition = { noteScreenEnter },
                exitTransition = { noteScreenExit },
                popEnterTransition = { noteScreenPopEnter },
                popExitTransition = { noteScreenPopExit }
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L
                LaunchedEffect(noteId) {
                    if (noteId > 0L) noteViewModel.loadNote(noteId) else noteViewModel.prepareNewNote()
                }
                NoteDetailRoute(
                    noteViewModel = noteViewModel,
                    navController = navController,
                    application = application
                )
            }

            composable(
                route = Routes.NOTE_DETAIL,
                enterTransition = { noteScreenEnter },
                exitTransition = { noteScreenExit },
                popEnterTransition = { noteScreenPopEnter },
                popExitTransition = { noteScreenPopExit }
            ) {
                NoteDetailRoute(
                    noteViewModel = noteViewModel,
                    navController = navController,
                    application = application
                )
            }
        }

        undoSnackbarState?.let { state ->
            UndoDeleteSnackbar(
                visible = true,
                message = state.message,
                actionLabel = "Annulla",
                onAction = state.onUndo,
                onDismiss = noteViewModel::dismissUndoSnackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun NoteDetailRoute(
    noteViewModel: NoteViewModel,
    navController: NavHostController,
    application: NotepadApplication
) {
    val detailState by noteViewModel.detailUiState.collectAsState()
    val allLabels by noteViewModel.allLabels.collectAsState()
    val context = LocalContext.current
    val backStackEntry = navController.currentBackStackEntry

    LaunchedEffect(backStackEntry) {
        backStackEntry?.savedStateHandle?.getStateFlow<String?>("drawing_result", null)
            ?.collect { path ->
                if (path != null) {
                    noteViewModel.onAddImage(path)
                    backStackEntry.savedStateHandle.remove<String>("drawing_result")
                }
            }
    }

    AddEditNoteScreen(
        uiState = detailState,
        allLabels = allLabels,
        onTitleChange = noteViewModel::onTitleChange,
        onContentChange = noteViewModel::onContentChange,
        onColorChange = noteViewModel::onColorChange,
        onTogglePin = noteViewModel::togglePin,
        onNoteTypeChange = noteViewModel::onNoteTypeChange,
        onChecklistItemTextChange = noteViewModel::onChecklistItemTextChange,
        onChecklistItemCheckedChange = noteViewModel::onChecklistItemCheckedChange,
        onAddChecklistItem = noteViewModel::addChecklistItem,
        onRemoveChecklistItem = noteViewModel::removeChecklistItem,
        onReminderChange = { millis ->
            noteViewModel.onReminderChange(millis)
            if (millis != null && !application.reminderScheduler.canScheduleExactAlarms()) {
                application.reminderScheduler.openExactAlarmSettings()
            }
        },
        onAddImage = noteViewModel::onAddImage,
        onRemoveImage = noteViewModel::onRemoveImage,
        onAudioChange = noteViewModel::onAudioChange,
        onToggleLabel = noteViewModel::toggleLabel,
        onOpenDrawing = { navController.navigate(Routes.DRAWING) },
        onOcrExtract = { imagePath -> OcrHelper.extractTextFromPath(context, imagePath) },
        onShare = noteViewModel::getShareText,
        onSave = { noteViewModel.saveNote { navController.popBackStack() } },
        onDelete = { noteViewModel.deleteCurrentNote { navController.popBackStack() } },
        onArchive = { noteViewModel.archiveCurrentNote { navController.popBackStack() } },
        onBack = { navController.popBackStack() }
    )
}
