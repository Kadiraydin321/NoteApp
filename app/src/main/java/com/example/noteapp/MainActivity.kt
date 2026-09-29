package com.example.noteapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.noteapp.biometric.BiometricPromptManager
import com.example.noteapp.biometric.BiometricResult
import com.example.noteapp.data.settings.AppSettingsManager
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.presentation.detail.NoteDetailScreen
import com.example.noteapp.presentation.detail.NoteDetailViewModel
import com.example.noteapp.presentation.drawing.DrawingScreen
import com.example.noteapp.presentation.notes.NotesScreen
import com.example.noteapp.presentation.notes.NotesViewModel
import com.example.noteapp.presentation.settings.SettingsScreen
import com.example.noteapp.presentation.settings.SettingsViewModel
import com.example.noteapp.presentation.theme.NoteAppTheme
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsManager: AppSettingsManager

    @Inject
    lateinit var repository: NoteRepository

    private val biometricPromptManager by lazy {
        BiometricPromptManager(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val appSettings by settingsManager.settings.collectAsState()

            NoteAppTheme(dynamicColor = appSettings.dynamicColor) {
                val navController = rememberNavController()

                // Widget'tan gelen intent'i kontrol et
                LaunchedEffect(Unit) {
                    handleWidgetIntent(intent) { route ->
                        navController.navigate(route)
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "notes_screen"
                ) {
                    composable("notes_screen") {
                        val viewModel = hiltViewModel<NotesViewModel>()
                        val state by viewModel.state.collectAsState()

                        NotesScreen(
                            state = state,
                            onSearchChange = viewModel::onSearchQueryChanged,
                            onNoteClick = { note ->
                                if (note.isLocked) {
                                    biometricPromptManager.showBiometricPrompt(
                                        title = "Kilitli Not",
                                        description = "'${note.title}' notunu açmak için parmak izinizi veya PIN'inizi kullanın",
                                        onResult = { result ->
                                            when (result) {
                                                is BiometricResult.AuthenticationSuccess -> {
                                                    navController.navigate("note_detail_screen?noteId=${note.id}")
                                                }
                                                is BiometricResult.AuthenticationError -> {
                                                    Toast.makeText(this@MainActivity, result.error, Toast.LENGTH_SHORT).show()
                                                }
                                                is BiometricResult.AuthenticationFailed -> {
                                                    Toast.makeText(this@MainActivity, "Kimlik doğrulama başarısız", Toast.LENGTH_SHORT).show()
                                                }
                                                is BiometricResult.FeatureUnavailable,
                                                is BiometricResult.HardwareUnavailable,
                                                is BiometricResult.NoneEnrolled -> {
                                                    // Emülatör veya biyometrik donanımı/kaydı olmayan cihazlarda erişime izin ver
                                                    Toast.makeText(this@MainActivity, "Biyometrik kilit bulunamadı, not açılıyor", Toast.LENGTH_SHORT).show()
                                                    navController.navigate("note_detail_screen?noteId=${note.id}")
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    navController.navigate("note_detail_screen?noteId=${note.id}")
                                }
                            },
                            onAddNoteClick = {
                                navController.navigate("note_detail_screen")
                            },
                            onPinNote = viewModel::onPinNote,
                            onArchiveNote = viewModel::onArchiveNote,
                            onMoveToTrash = viewModel::onMoveToTrash,
                            onRestoreNote = viewModel::onRestoreNote,
                            onDeletePermanently = viewModel::onDeleteNotePermanently,
                            onEmptyTrash = viewModel::onEmptyTrash,
                            onCategorySelect = viewModel::onCategorySelect,
                            onViewModeChange = viewModel::setViewMode,
                            onAddCategory = viewModel::onAddCategory,
                            onSettingsClick = {
                                navController.navigate("settings_screen")
                            }
                        )
                    }

                    composable(
                        route = "note_detail_screen?noteId={noteId}&autoAction={autoAction}",
                        arguments = listOf(
                            navArgument("noteId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument("autoAction") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val viewModel = hiltViewModel<NoteDetailViewModel>()
                        val state by viewModel.state.collectAsState()
                        val autoAction = backStackEntry.arguments?.getString("autoAction")

                        // Çizim ekranından dönen çizim dosyasını yakala
                        val savedDrawingPath by backStackEntry.savedStateHandle
                            .getStateFlow<String?>("drawing_path", null)
                            .collectAsState()

                        LaunchedEffect(savedDrawingPath) {
                            savedDrawingPath?.let { path ->
                                viewModel.onAddAttachment(path)
                                backStackEntry.savedStateHandle.remove<String>("drawing_path")
                            }
                        }

                        // Düzenlenen görsel dosyasını yakala (oldPath, newPath)
                        val editedImagePair by backStackEntry.savedStateHandle
                            .getStateFlow<Pair<String, String>?>("edited_image_pair", null)
                            .collectAsState()

                        var justEditedImagePath by remember { mutableStateOf<String?>(null) }

                        LaunchedEffect(editedImagePair) {
                            editedImagePair?.let { (oldPath, newPath) ->
                                viewModel.onUpdateAttachment(oldPath, newPath)
                                justEditedImagePath = newPath
                                backStackEntry.savedStateHandle.remove<Pair<String, String>>("edited_image_pair")
                            }
                        }

                        NoteDetailScreen(
                            state = state,
                            onTitleChange = viewModel::onTitleChange,
                            onContentValueChange = viewModel::onContentValueChange,
                            onColorChange = viewModel::onColorChange,
                            onTogglePin = viewModel::onTogglePin,
                            onToggleLock = viewModel::onToggleLock,
                            onSetReminder = viewModel::onSetReminder,
                            onAddImageUri = viewModel::onAddImage,
                            onToggleAudioRecording = viewModel::toggleAudioRecording,
                            onToggleAudioPlayback = viewModel::toggleAudioPlayback,
                            onDeleteAttachment = viewModel::onDeleteAttachment,
                            onImageClick = { imagePath ->
                                val encodedPath = java.net.URLEncoder.encode(imagePath, java.nio.charset.StandardCharsets.UTF_8.toString())
                                navController.navigate("image_edit_screen?imagePath=$encodedPath")
                            },
                            onRevertImageEdit = { path ->
                                viewModel.revertImageEdit(path)
                            },
                            canRevertImage = { path ->
                                viewModel.canRevertImage(path)
                            },
                            onDeleteNoteClick = {
                                viewModel.deleteNote {
                                    navController.popBackStack()
                                }
                            },
                            onAddDrawingClick = {
                                navController.navigate("drawing_screen")
                            },
                            onSaveClick = {
                                viewModel.saveNote()
                                navController.popBackStack()
                            },
                            onBackClick = {
                                navController.popBackStack()
                            },
                            autoAction = autoAction?.ifBlank { null },
                            justEditedImagePath = justEditedImagePath,
                            onClearJustEditedImage = { justEditedImagePath = null }
                        )
                    }

                    composable(
                        route = "image_edit_screen?imagePath={imagePath}",
                        arguments = listOf(
                            navArgument("imagePath") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStack ->
                        val rawPath = backStack.arguments?.getString("imagePath") ?: ""
                        val decodedPath = try {
                            java.net.URLDecoder.decode(rawPath, java.nio.charset.StandardCharsets.UTF_8.toString())
                        } catch (_: Exception) {
                            rawPath
                        }

                        com.example.noteapp.presentation.imageedit.ImageEditScreen(
                            imagePath = decodedPath,
                            onSaveSuccess = { originalPath, newPath ->
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("edited_image_pair", Pair(originalPath, newPath))
                                navController.popBackStack()
                            },
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("drawing_screen") {
                        DrawingScreen(
                            onDrawingSaved = { drawingPath ->
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("drawing_path", drawingPath)
                                navController.popBackStack()
                            },
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("settings_screen") {
                        val viewModel = hiltViewModel<SettingsViewModel>()
                        val settingsState by viewModel.settings.collectAsState()
                        val backupUiState by viewModel.backupUiState.collectAsState()

                        SettingsScreen(
                            settings = settingsState,
                            backupUiState = backupUiState,
                            onWidgetFilterChange = viewModel::setWidgetFilterMode,
                            onWidgetShowLockedChange = viewModel::setWidgetShowLocked,
                            onDefaultColorChange = viewModel::setDefaultNoteColor,
                            onDynamicColorChange = viewModel::setDynamicColor,
                            onRefreshWidget = viewModel::refreshWidget,
                            onExportToUri = viewModel::exportBackupToUri,
                            onExportAndShare = { viewModel.exportAndShare(it) },
                            onImportFromUri = { uri, clear -> viewModel.importBackupFromUri(uri, clear) },
                            onLoadSampleNotes = viewModel::loadSampleNotes,
                            onDismissBackupMessage = viewModel::dismissMessage,
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?, onNavigate: (String) -> Unit) {
        if (intent == null) return

        // Widget hızlı aksiyon butonları (Metin, Görsel, Ses, Çizim)
        val actionType = intent.getStringExtra(NotesWidgetProvider.EXTRA_ACTION_TYPE)
        if (actionType != null) {
            intent.removeExtra(NotesWidgetProvider.EXTRA_ACTION_TYPE)
            when (actionType) {
                NotesWidgetProvider.ACTION_TYPE_TEXT -> onNavigate("note_detail_screen")
                NotesWidgetProvider.ACTION_TYPE_IMAGE -> onNavigate("note_detail_screen?autoAction=image")
                NotesWidgetProvider.ACTION_TYPE_VOICE -> onNavigate("note_detail_screen?autoAction=voice")
                NotesWidgetProvider.ACTION_TYPE_DRAW -> onNavigate("note_detail_screen?autoAction=draw")
            }
            return
        }

        // Widget eski + butonuna basıldıysa yeni not ekranı
        if (intent.getBooleanExtra(NotesWidgetProvider.EXTRA_NEW_NOTE, false)) {
            intent.removeExtra(NotesWidgetProvider.EXTRA_NEW_NOTE)
            onNavigate("note_detail_screen")
            return
        }

        // Widget'tan belirli bir nota tıklandıysa
        val noteId = intent.getLongExtra("noteId", -1L)
        if (noteId != -1L) {
            intent.removeExtra("noteId")
            CoroutineScope(Dispatchers.Main).launch {
                val note = repository.getNoteById(noteId)
                if (note != null) {
                    if (note.isLocked) {
                        biometricPromptManager.showBiometricPrompt(
                            title = "Kilitli Not",
                            description = "'${note.title}' notunu açmak için parmak izinizi veya PIN'inizi kullanın",
                            onResult = { result ->
                                if (result is BiometricResult.AuthenticationSuccess) {
                                    onNavigate("note_detail_screen?noteId=$noteId")
                                } else {
                                    Toast.makeText(this@MainActivity, "Kimlik doğrulanmadı", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        onNavigate("note_detail_screen?noteId=$noteId")
                    }
                }
            }
        }
    }
}
