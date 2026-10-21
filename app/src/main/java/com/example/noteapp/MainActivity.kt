package com.example.noteapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.io.File
import org.json.JSONObject
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.noteapp.biometric.BiometricPromptManager
import com.example.noteapp.biometric.BiometricResult
import com.example.noteapp.data.settings.AppSettingsManager
import com.example.noteapp.data.security.NoteCryptoManager
import com.example.noteapp.domain.model.Note
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsManager: AppSettingsManager

    @Inject
    lateinit var repository: NoteRepository

    @Inject
    lateinit var cryptoManager: NoteCryptoManager

    @Inject
    lateinit var templateManager: com.example.noteapp.data.template.TemplateManager

    private val biometricPromptManager by lazy {
        BiometricPromptManager(this)
    }

    private val isPrivacyShieldActive = mutableStateOf(false)
    private var navControllerRef: NavController? = null
    private var pendingWidgetIntent by mutableStateOf<Intent?>(null)
    private var wasAppInBackground = false
    private var activeLockedNoteId: Long? = null
    private var backgroundTimestamp: Long = 0L
    private var lastUnlockTimestamp: Long = 0L

    private fun hasRecentDrawingHistory(path: String): Boolean = runCatching {
        val key = path.hashCode().toUInt().toString(16)
        val historyFile = File(noBackupFilesDir, "drawing_$key.json")
        if (!historyFile.exists()) return false
        val history = JSONObject(historyFile.readText())
        val updatedAt = history.optLong("updated", 0L)
        val hasElements = (history.optJSONArray("elements")?.length() ?: 0) > 0
        hasElements && updatedAt > 0L && System.currentTimeMillis() - updatedAt in 0..(60L * 60L * 1000L)
    }.getOrDefault(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)


        pendingWidgetIntent = intent

        setContent {
            val appSettings by settingsManager.settings.collectAsState()
            val isShieldActive by remember { isPrivacyShieldActive }

            // Biyometrik yerine Master PIN doğrulama / belirleme diyaloğu
            var noteToUnlockWithPin by remember { mutableStateOf<Note?>(null) }
            var pinInput by remember { mutableStateOf("") }
            var pinErrorText by remember { mutableStateOf<String?>(null) }

            NoteAppTheme(
                themeMode = appSettings.themeMode,
                dynamicColor = appSettings.dynamicColor,
                colorPalette = appSettings.colorPalette
            ) {
                var showWelcomeScreen by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(1000)
                    showWelcomeScreen = false
                }

                val navController = rememberNavController()
                LaunchedEffect(navController) {
                    navControllerRef = navController
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Widget'tan gelen intent'i kontrol et
                    LaunchedEffect(pendingWidgetIntent) {
                        val incoming = pendingWidgetIntent
                        if (incoming != null) {
                            handleWidgetIntent(
                                intent = incoming,
                                onUnlockWithPinRequired = { note ->
                                    noteToUnlockWithPin = note
                                },
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo("notes_screen") { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            )
                            pendingWidgetIntent = null
                        }
                    }

                    // Master PIN Diyaloğu
                    if (noteToUnlockWithPin != null) {
                        val targetNote = noteToUnlockWithPin!!
                        val hasMasterPin = !appSettings.masterPin.isNullOrBlank()

                        AlertDialog(
                            onDismissRequest = {
                                noteToUnlockWithPin = null
                                pinInput = ""
                                pinErrorText = null
                            },
                            icon = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            title = {
                                Text(
                                    if (hasMasterPin) "Master PIN Girin" else "Kilitli Not - PIN Belirleyin"
                                )
                            },
                            text = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        if (hasMasterPin)
                                            "'${targetNote.title.ifBlank { "Not" }}' kilitli. Açmak için Master PIN kodunuzu girin:"
                                        else
                                            "Cihazınızda biyometrik kilit bulunamadı. Bu notu açmak ve güvenliğinizi korumak için lütfen en az 4 haneli bir Master PIN belirleyin:"
                                    )
                                    OutlinedTextField(
                                        value = pinInput,
                                        onValueChange = {
                                            if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                                                pinInput = it
                                                pinErrorText = null
                                            }
                                        },
                                        label = { Text("PIN Kodu") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.NumberPassword
                                        ),
                                        isError = pinErrorText != null,
                                        supportingText = {
                                            if (pinErrorText != null) {
                                                Text(
                                                    pinErrorText!!,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        if (hasMasterPin) {
                                            if (pinInput == appSettings.masterPin) {
                                                val id = targetNote.id
                                                lastUnlockTimestamp = System.currentTimeMillis()
                                                noteToUnlockWithPin = null
                                                pinInput = ""
                                                pinErrorText = null
                                                navController.navigate("note_detail_screen?noteId=$id") {
                                                    popUpTo("notes_screen") { inclusive = false }
                                                    launchSingleTop = true
                                                }
                                            } else {
                                                pinErrorText = "Hatalı PIN! Lütfen tekrar deneyin."
                                            }
                                        } else {
                                            if (pinInput.length >= 4) {
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    settingsManager.setMasterPin(pinInput)
                                                    Toast.makeText(this@MainActivity, "Master PIN kaydedildi", Toast.LENGTH_SHORT).show()
                                                    val id = targetNote.id
                                                    lastUnlockTimestamp = System.currentTimeMillis()
                                                    noteToUnlockWithPin = null
                                                    pinInput = ""
                                                    pinErrorText = null
                                                    navController.navigate("note_detail_screen?noteId=$id") {
                                                        popUpTo("notes_screen") { inclusive = false }
                                                        launchSingleTop = true
                                                    }
                                                }
                                            } else {
                                                pinErrorText = "PIN en az 4 haneli olmalıdır"
                                            }
                                        }
                                    }
                                ) {
                                    Text(if (hasMasterPin) "Aç" else "Kaydet ve Aç")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        noteToUnlockWithPin = null
                                        pinInput = ""
                                        pinErrorText = null
                                    }
                                ) {
                                    Text("İptal")
                                }
                            }
                        )
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "notes_screen"
                    ) {
                    composable("notes_screen") {
                        val viewModel = hiltViewModel<NotesViewModel>()
                        val state by viewModel.state.collectAsState()
                        val quickSyncState by viewModel.quickSyncState.collectAsState()
                        val noteSettingsState by viewModel.settings.collectAsState()
                        var showTemplateSheet by remember { mutableStateOf(false) }

                        NotesScreen(
                            state = state,
                            quickSyncState = quickSyncState,
                            syncHost = noteSettingsState.lastSyncHost,
                            drawerCollapseMode = noteSettingsState.drawerCollapseMode,
                            cardCornerStyle = noteSettingsState.cardCornerStyle,
                            cardElevationStyle = noteSettingsState.cardElevationStyle,
                            quickActionLocation = noteSettingsState.quickActionLocation,
                            searchBarShape = noteSettingsState.searchBarShape,
                            onQuickSyncSend = viewModel::quickSyncSend,
                            onQuickSyncPull = viewModel::quickSyncPull,
                            onDismissQuickSyncMessage = viewModel::dismissQuickSyncMessage,
                            onSearchChange = viewModel::onSearchQueryChanged,
                            onNoteClick = { note ->
                                if (note.isLocked) {
                                    val policy = appSettings.lockTimeoutPolicy
                                    val isGraceActive = policy != com.example.noteapp.data.settings.LockTimeoutPolicy.IMMEDIATE &&
                                            lastUnlockTimestamp > 0L &&
                                            (System.currentTimeMillis() - lastUnlockTimestamp <= policy.timeoutMs)

                                    if (isGraceActive) {
                                        navController.navigate("note_detail_screen?noteId=${note.id}")
                                    } else {
                                        biometricPromptManager.showBiometricPrompt(
                                            title = "Kilitli Not",
                                            description = "'${note.title}' notunu açmak için parmak izinizi veya PIN'inizi kullanın",
                                            onResult = { result ->
                                                when (result) {
                                                    is BiometricResult.AuthenticationSuccess -> {
                                                        lastUnlockTimestamp = System.currentTimeMillis()
                                                        navController.navigate("note_detail_screen?noteId=${note.id}")
                                                    }
                                                    is BiometricResult.AuthenticationError -> {
                                                        if (!appSettings.masterPin.isNullOrBlank()) {
                                                            noteToUnlockWithPin = note
                                                        } else {
                                                            Toast.makeText(this@MainActivity, result.error, Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    is BiometricResult.AuthenticationFailed -> {
                                                        Toast.makeText(this@MainActivity, "Kimlik doğrulama başarısız", Toast.LENGTH_SHORT).show()
                                                    }
                                                    is BiometricResult.FeatureUnavailable,
                                                    is BiometricResult.HardwareUnavailable,
                                                    is BiometricResult.NoneEnrolled -> {
                                                        // Biyometrik yoksa veya tanımlı değilse Master PIN sor (asla kilidi atlama)
                                                        noteToUnlockWithPin = note
                                                    }
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    navController.navigate("note_detail_screen?noteId=${note.id}")
                                }
                            },
                            onAddNoteClick = { action ->
                                val route = if (action.isNullOrBlank()) "note_detail_screen" else "note_detail_screen?autoAction=$action"
                                navController.navigate(route)
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
                            onDeleteCategory = viewModel::onDeleteCategory,
                            onRenameCategory = viewModel::onRenameCategory,
                            onUpdateCategoryColor = viewModel::onUpdateCategoryColor,
                            onLayoutModeChange = viewModel::setLayoutMode,
                            onToggleNoteSelection = viewModel::toggleNoteSelection,
                            onSelectAllNotes = viewModel::selectAllNotes,
                            onClearSelection = viewModel::clearSelection,
                            onDeleteSelectedNotes = viewModel::deleteSelectedNotes,
                            onUpdateCategoryForSelected = viewModel::updateCategoryForSelected,
                            onUpdateColorForSelected = viewModel::updateColorForSelected,
                            onTogglePinForSelected = viewModel::togglePinForSelected,
                            onDuplicateNote = viewModel::onDuplicateNote,
                            onFilterTypeChange = viewModel::setFilterType,
                            onSortOrderChange = viewModel::setSortOrder,
                            onSelectTag = viewModel::onSelectTag,
                            onNavigateToStatistics = {
                                navController.navigate("statistics_screen")
                            },
                            onOpenTemplates = {
                                showTemplateSheet = true
                            },
                            onSaveSearchQuery = viewModel::saveSearchQuery,
                            onRemoveRecentSearch = viewModel::removeRecentSearch,
                            onClearRecentSearches = viewModel::clearRecentSearches,
                            onMergeSelectedNotes = { moveToTrash ->
                                viewModel.mergeSelectedNotes(moveToTrash) {
                                    android.widget.Toast.makeText(this@MainActivity, "Seçilen notlar başarıyla birleştirildi", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onRenameTag = { oldTag, newTag ->
                                viewModel.renameTagAcrossNotes(oldTag, newTag) {
                                    android.widget.Toast.makeText(this@MainActivity, "'#$oldTag' etiketi '#$newTag' olarak güncellendi", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDeleteTag = { tag ->
                                viewModel.deleteTagAcrossNotes(tag) {
                                    android.widget.Toast.makeText(this@MainActivity, "'#$tag' etiketi tüm notlardan silindi", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            getTagUsageCounts = viewModel::getTagUsageCounts,
                            onToggleAgendaMode = viewModel::onToggleAgendaMode,
                            onSelectAgendaDate = viewModel::onSelectAgendaDate,
                            onSettingsClick = {
                                navController.navigate("settings_screen")
                            }
                        )

                        if (showTemplateSheet) {
                            com.example.noteapp.presentation.components.TemplatePickerBottomSheet(
                                templates = templateManager.getAllTemplates(),
                                onDismiss = { showTemplateSheet = false },
                                onTemplateSelect = { template ->
                                    showTemplateSheet = false
                                    navController.navigate("note_detail_screen?templateId=${template.id}")
                                },
                                onDeleteCustomTemplate = { id ->
                                    templateManager.deleteCustomTemplate(id)
                                }
                            )
                        }
                    }

                    composable(
                        route = "note_detail_screen?noteId={noteId}&autoAction={autoAction}&sharedText={sharedText}&sharedTitle={sharedTitle}&templateId={templateId}",
                        arguments = listOf(
                            navArgument("noteId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument("autoAction") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("sharedText") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("sharedTitle") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("templateId") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val viewModel = hiltViewModel<NoteDetailViewModel>()
                        val state by viewModel.state.collectAsState()
                        SideEffect {
                            activeLockedNoteId = state.currentNoteId?.takeIf { state.isLocked }
                        }
                        val autoAction = backStackEntry.arguments?.getString("autoAction")
                        val sharedText = backStackEntry.arguments?.getString("sharedText")
                        val sharedTitle = backStackEntry.arguments?.getString("sharedTitle")

                        // Paylaşılan içerik varsa NoteDetailViewModel'e aktar
                        LaunchedEffect(sharedText, sharedTitle) {
                            if (!sharedText.isNullOrBlank()) {
                                viewModel.setSharedContent(sharedText, sharedTitle)
                                // Argümanları temizle ki rotasyon vs olduğunda tekrar eklemesin
                                backStackEntry.arguments?.putString("sharedText", "")
                                backStackEntry.arguments?.putString("sharedTitle", "")
                            }
                        }

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

                        val categories by viewModel.categories.collectAsState()
                        val canUndo by viewModel.canUndo.collectAsState()
                        val canRedo by viewModel.canRedo.collectAsState()

                        NoteDetailScreen(
                            state = state,
                            categories = categories,
                            canUndo = canUndo,
                            canRedo = canRedo,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo,
                            onCategoryChange = viewModel::onCategoryChange,
                            onAddCategory = viewModel::onAddCategory,
                            onTitleChange = viewModel::onTitleChange,
                            onContentValueChange = viewModel::onContentValueChange,
                            onColorChange = viewModel::onColorChange,
                            onBackgroundImageChange = viewModel::onBackgroundImageChange,
                            onTogglePin = viewModel::onTogglePin,
                            onToggleLock = viewModel::onToggleLock,
                            onSetReminder = viewModel::onSetReminder,
                            onAddImageUri = viewModel::onAddImage,
                            onToggleAudioRecording = viewModel::toggleAudioRecording,
                            onToggleAudioPlayback = viewModel::toggleAudioPlayback,
                            onDeleteAttachment = viewModel::onDeleteAttachment,
                            onImageEditClick = { imagePath ->
                                val encodedPath = java.net.URLEncoder.encode(imagePath, java.nio.charset.StandardCharsets.UTF_8.toString())
                                if (imagePath.substringAfterLast('/').startsWith("DRAW_") && hasRecentDrawingHistory(imagePath)) {
                                    navController.navigate("drawing_screen?drawingPath=$encodedPath")
                                } else {
                                    navController.navigate("image_edit_screen?imagePath=$encodedPath")
                                }
                            },
                            onAnnotateImage = { imagePath ->
                                val encodedPath = java.net.URLEncoder.encode(imagePath, java.nio.charset.StandardCharsets.UTF_8.toString())
                                navController.navigate("drawing_screen?imagePath=$encodedPath")
                            },
                            onRevertImageEdit = { path ->
                                viewModel.revertImageEdit(path)
                            },
                            canRevertImage = { path ->
                                viewModel.canRevertImage(path)
                            },
                            onExtractText = { path ->
                                viewModel.extractTextFromImage(path) { extracted ->
                                    if (extracted.isNullOrBlank()) {
                                        android.widget.Toast.makeText(this@MainActivity, "Metin bulunamadı veya okunamadı.", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(this@MainActivity, "Metin başarıyla eklendi.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            isExtractingText = state.isExtractingText,
                            onLinkClick = { noteTitle ->
                                viewModel.findNoteByTitle(noteTitle) { foundId ->
                                    if (foundId != null) {
                                        navController.navigate("note_detail_screen?noteId=$foundId")
                                    }
                                }
                            },
                            onNavigateToNote = { targetId ->
                                navController.navigate("note_detail_screen?noteId=$targetId")
                            },
                            onDeleteNoteClick = {
                                viewModel.deleteNote {
                                    navController.popBackStack()
                                }
                            },
                            onArchiveNoteClick = {
                                viewModel.archiveCurrentNote {
                                    navController.popBackStack()
                                }
                            },
                            onDuplicateNote = {
                                viewModel.duplicateCurrentNote {
                                    android.widget.Toast.makeText(this@MainActivity, "Notun bir kopyası oluşturuldu", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAddDrawingClick = {
                                navController.navigate("drawing_screen")
                            },
                            onBackClick = {
                                viewModel.saveNote()
                                if (!navController.popBackStack()) {
                                    navController.navigate("notes_screen") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            },
                            autoAction = autoAction?.ifBlank { null },
                            justEditedImagePath = justEditedImagePath,
                            onClearJustEditedImage = { justEditedImagePath = null },
                            onFontSizeChange = viewModel::onFontSizeChange,
                            onToggleZenMode = viewModel::toggleZenMode,
                            onToggleFocusSound = viewModel::toggleFocusSound,
                            onSelectFocusSound = viewModel::selectFocusSound,
                            onFocusSoundVolumeChange = viewModel::setFocusSoundVolume,
                            onRestoreVersion = viewModel::restoreNoteVersion,
                            onApplyTemplate = viewModel::applyTemplate,
                            onSaveCustomTemplate = viewModel::saveCurrentAsCustomTemplate,
                            onDeleteCustomTemplate = viewModel::deleteCustomTemplate,
                            availableTemplates = viewModel.getAvailableTemplates(),
                            onSeekAudio = viewModel::seekAudioBy,
                            onCycleAudioPlaybackSpeed = viewModel::cycleAudioPlaybackSpeed,
                            onSeekAudioFraction = viewModel::seekAudioToFraction,
                            onCancelAudioRecording = viewModel::cancelAudioRecording,
                            onSetWordGoal = viewModel::setWordGoal,
                            onRestoreDraft = viewModel::restoreDraft,
                            onDiscardDraft = { viewModel.discardDraft(viewModel.state.value.currentNoteId) },
                            onSaveDraftPeriodically = viewModel::saveDraftPeriodically
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
                            cryptoManager = cryptoManager,
                            onSaveSuccess = { originalPath, newPath ->
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("edited_image_pair", Pair(originalPath, newPath))
                                if (!navController.popBackStack()) {
                                    navController.navigate("notes_screen") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            },
                            onBackClick = {
                                if (!navController.popBackStack()) {
                                    navController.navigate("notes_screen") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable(
                        route = "drawing_screen?drawingPath={drawingPath}&imagePath={imagePath}",
                        arguments = listOf(
                            navArgument("drawingPath") { type = NavType.StringType; defaultValue = "" },
                            navArgument("imagePath") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { drawingBackStack ->
                        val rawDrawingPath = drawingBackStack.arguments?.getString("drawingPath")?.takeIf { it.isNotBlank() }
                        val decodedDrawingPath = rawDrawingPath?.let {
                            try { java.net.URLDecoder.decode(it, java.nio.charset.StandardCharsets.UTF_8.toString()) } catch (_: Exception) { it }
                        }
                        val rawImagePath = drawingBackStack.arguments?.getString("imagePath")?.takeIf { it.isNotBlank() }
                        val decodedImagePath = rawImagePath?.let {
                            try { java.net.URLDecoder.decode(it, java.nio.charset.StandardCharsets.UTF_8.toString()) } catch (_: Exception) { it }
                        }

                        DrawingScreen(
                            drawingPath = decodedDrawingPath,
                            backgroundImagePath = decodedImagePath,
                            onDrawingSaved = { savedPath ->
                                val sourcePath = decodedImagePath ?: decodedDrawingPath
                                if (sourcePath.isNullOrBlank()) {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("drawing_path", savedPath)
                                } else {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("edited_image_pair", Pair(sourcePath, savedPath))
                                }
                                if (!navController.popBackStack()) {
                                    navController.navigate("notes_screen") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            },
                            onBackClick = {
                                if (!navController.popBackStack()) {
                                    navController.navigate("notes_screen") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable(
                        route = "settings_screen?focus={focus}",
                        arguments = listOf(navArgument("focus") {
                            type = NavType.StringType
                            defaultValue = ""
                        })
                    ) { backStackEntry ->
                        val focus = backStackEntry.arguments?.getString("focus")
                        val viewModel = hiltViewModel<SettingsViewModel>()
                        val settingsState by viewModel.settings.collectAsState()
                        val backupUiState by viewModel.backupUiState.collectAsState()
                        val syncUiState by viewModel.syncUiState.collectAsState()
                        val updateUiState by viewModel.updateUiState.collectAsState()
                        val maintenanceUiState by viewModel.maintenanceUiState.collectAsState()

                        SettingsScreen(
                            initialFocusSection = focus,
                            settings = settingsState,
                            backupUiState = backupUiState,
                            syncUiState = syncUiState,
                            updateUiState = updateUiState,
                            maintenanceUiState = maintenanceUiState,
                            onWidgetFilterChange = viewModel::setWidgetFilterMode,
                            onWidgetShowLockedChange = viewModel::setWidgetShowLocked,
                            onWidgetShowContentChange = viewModel::setWidgetShowContent,
                            onDefaultColorChange = viewModel::setDefaultNoteColor,
                            onDynamicColorChange = viewModel::setDynamicColor,
                            onColorPaletteChange = viewModel::setColorPalette,
                            onThemeModeChange = viewModel::setThemeMode,
                            onLayoutModeChange = viewModel::setLayoutMode,
                            onDrawerCollapseModeChange = viewModel::setDrawerCollapseMode,
                            onMasterPinChange = viewModel::setMasterPin,
                            onAutoLockChange = viewModel::setAutoLockOnExit,
                            onHighContrastChange = viewModel::setHighContrastNegative,
                            onRefreshWidget = viewModel::refreshWidget,
                            onExportToUri = { uri, pass -> viewModel.exportBackupToUri(uri, pass) },
                            onExportAndShare = { ctx, pass -> viewModel.exportAndShare(ctx, pass) },
                            onImportFromUri = { uri, clear, pass -> viewModel.importBackupFromUri(uri, clear, pass) },
                            onDismissBackupMessage = viewModel::dismissMessage,
                            onCheckSyncConnection = viewModel::checkSyncConnection,
                            onAutoDiscoverServer = viewModel::autoDiscoverServer,
                            onSendNotesToDesktop = viewModel::sendNotesToDesktop,
                            onPullNotesFromDesktop = viewModel::pullNotesFromDesktop,
                            onDismissSyncMessage = viewModel::dismissSyncMessage,
                            onCheckForUpdates = viewModel::checkForUpdates,
                            onCheckAndInstallUpdate = { force -> viewModel.checkAndInstallUpdate(force) },
                            onDownloadAndInstallUpdate = viewModel::downloadAndInstallUpdate,
                            onInstallReadyApk = viewModel::installReadyApk,
                            onDismissUpdateStatus = viewModel::dismissUpdateStatus,
                            onMarkDialogShown = viewModel::markDialogShown,
                            onNavigateToDesignStudio = {
                                navController.navigate("design_studio_screen")
                            },
                            onNavigateToStatistics = {
                                navController.navigate("statistics_screen")
                            },
                            onOptimizeDatabase = viewModel::optimizeDatabase,
                            onDismissMaintenanceMessage = viewModel::dismissMaintenanceMessage,
                            onTrashRetentionPolicyChange = viewModel::setTrashRetentionPolicy,
                            onLockTimeoutPolicyChange = viewModel::setLockTimeoutPolicy,
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("design_studio_screen") {
                        val viewModel = hiltViewModel<SettingsViewModel>()
                        val settingsState by viewModel.settings.collectAsState()

                        com.example.noteapp.presentation.settings.DesignStudioScreen(
                            settings = settingsState,
                            onBackClick = { navController.popBackStack() },
                            onApplyPreset = viewModel::applyDesignPreset,
                            onCardCornerStyleChange = viewModel::setCardCornerStyle,
                            onCardElevationStyleChange = viewModel::setCardElevationStyle,
                            onQuickActionLocationChange = viewModel::setQuickActionLocation,
                            onSearchBarShapeChange = viewModel::setSearchBarShape,
                            onColorPaletteChange = viewModel::setColorPalette,
                            onLayoutModeChange = viewModel::setLayoutMode,
                            onWidgetDesignPresetChange = viewModel::setWidgetDesignPreset,
                            onWidgetFabBehaviorChange = viewModel::setWidgetFabBehavior,
                            onWidgetCornerRadiusChange = viewModel::setWidgetCornerRadius,
                            onWidgetFilterModeChange = viewModel::setWidgetFilterMode,
                            onWidgetShowContentChange = viewModel::setWidgetShowContent,
                            onWidgetShowLockedChange = viewModel::setWidgetShowLocked
                        )
                    }

                    composable("statistics_screen") {
                        val statsViewModel = hiltViewModel<com.example.noteapp.presentation.statistics.StatisticsViewModel>()
                        val stats by statsViewModel.statistics.collectAsState()
                        val isLoading by statsViewModel.isLoading.collectAsState()
                        val statsSettings by statsViewModel.settings.collectAsState()

                        com.example.noteapp.presentation.statistics.StatisticsScreen(
                            stats = stats,
                            isLoading = isLoading,
                            dailyWordGoal = statsSettings.dailyWordGoal,
                            onSetDailyWordGoal = statsViewModel::setDailyWordGoal,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }

                // Kapkara Gizlilik Kalkanı (Task Switcher ve Arka Plan Önizlemesini Tamamen Karartma)
                if (isShieldActive && appSettings.autoLockOnExit) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "Gizlilik Koruması",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "İçerik güvenliğiniz için gizlendi",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showWelcomeScreen,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "welcomeLogo")
                        val bounceScale by infiniteTransition.animateFloat(
                            initialValue = 0.96f,
                            targetValue = 1.04f,
                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                animation = androidx.compose.animation.core.tween(750, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        val glowAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.20f,
                            targetValue = 0.45f,
                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                animation = androidx.compose.animation.core.tween(750, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                            ),
                            label = "glow"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = bounceScale
                                    scaleY = bounceScale
                                }
                            ) {
                                // Kırmızı & Siyah Modern Ambiyans Işıltısı
                                Box(
                                    modifier = Modifier
                                        .size(136.dp)
                                        .background(
                                            brush = Brush.radialGradient(
                                                listOf(
                                                    Color(0xFFDC2626).copy(alpha = glowAlpha),
                                                    Color.Transparent
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                )
                                Image(
                                    painter = painterResource(id = R.drawable.ic_app_logo),
                                    contentDescription = "Not Defterim Logo",
                                    modifier = Modifier
                                        .size(108.dp)
                                        .clip(RoundedCornerShape(26.dp))
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Not Defterim",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Sade • Hızlı • Güvenli",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

    override fun onPause() {
        super.onPause()
        // Kilitli not ekranı FLAG_SECURE'ı kendi yaşam döngüsünde yönetir.
        // Normal notlar, ayarlar ve ana ekran Recents'te gereksiz yere sansürlenmemelidir.
        val currentRoute = navControllerRef?.currentBackStackEntry?.destination?.route
        val isLockedNoteRoute = currentRoute?.startsWith("note_detail_screen") == true &&
                navControllerRef?.currentBackStackEntry?.arguments?.getLong("noteId")?.let { noteId ->
                    noteId > 0L && activeLockedNoteId == noteId
                } == true
        if (isLockedNoteRoute) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            isPrivacyShieldActive.value = true
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            isPrivacyShieldActive.value = false
        }
    }

    override fun onStop() {
        super.onStop()
        wasAppInBackground = true
        backgroundTimestamp = System.currentTimeMillis()

        val policy = settingsManager.settings.value.lockTimeoutPolicy
        if (policy == com.example.noteapp.data.settings.LockTimeoutPolicy.IMMEDIATE) {
            // Anında kilitleme politikası: kullanıcı ayrıldığı anda kilitli notu kapat
            navControllerRef?.let { nav ->
                val currentRoute = nav.currentBackStackEntry?.destination?.route
                if (currentRoute?.startsWith("note_detail_screen") == true) {
                    val noteId = nav.currentBackStackEntry?.arguments?.getLong("noteId") ?: -1L
                    if (noteId != -1L && activeLockedNoteId == noteId) {
                        CoroutineScope(Dispatchers.Main).launch {
                            val note = repository.getNoteById(noteId)
                            if (note != null && note.isLocked) {
                                nav.popBackStack("notes_screen", inclusive = false)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isPrivacyShieldActive.value = false
        wasAppInBackground = false

        val policy = settingsManager.settings.value.lockTimeoutPolicy
        if (policy != com.example.noteapp.data.settings.LockTimeoutPolicy.IMMEDIATE && backgroundTimestamp > 0L) {
            val elapsed = System.currentTimeMillis() - backgroundTimestamp
            if (elapsed > policy.timeoutMs) {
                // Zaman aşımı doldu: kilitli not açıksa kapat ve ana ekrana dön
                navControllerRef?.let { nav ->
                    val currentRoute = nav.currentBackStackEntry?.destination?.route
                    if (currentRoute?.startsWith("note_detail_screen") == true) {
                        val noteId = nav.currentBackStackEntry?.arguments?.getLong("noteId") ?: -1L
                        if (noteId != -1L && activeLockedNoteId == noteId) {
                            CoroutineScope(Dispatchers.Main).launch {
                                val note = repository.getNoteById(noteId)
                                if (note != null && note.isLocked) {
                                    nav.popBackStack("notes_screen", inclusive = false)
                                    Toast.makeText(this@MainActivity, "Kilit zaman aşımına uğradı", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Kullanıcı uygulamaya geri döndüğünde, kilitli bir notta değilsek FLAG_SECURE'ı temizle:
        val currentRoute = navControllerRef?.currentBackStackEntry?.destination?.route
        val isDetailRoute = currentRoute?.startsWith("note_detail_screen") == true
        if (isDetailRoute) {
            val noteId = navControllerRef?.currentBackStackEntry?.arguments?.getLong("noteId") ?: -1L
            if (noteId != -1L) {
                CoroutineScope(Dispatchers.Main).launch {
                    val note = repository.getNoteById(noteId)
                    if (note == null || !note.isLocked) {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWidgetIntent = intent
    }

    private fun handleWidgetIntent(
        intent: Intent?,
        onUnlockWithPinRequired: (Note) -> Unit,
        onNavigate: (String) -> Unit
    ) {
        if (intent == null) return

        // Widget hızlı aksiyon butonları (Metin, Görsel, Ses, Çizim, Ayarlar)
        val actionType = intent.getStringExtra(NotesWidgetProvider.EXTRA_ACTION_TYPE)
            ?: if (intent.action == NotesWidgetProvider.ACTION_TYPE_SETTINGS) NotesWidgetProvider.ACTION_TYPE_SETTINGS else null
        if (actionType != null) {
            intent.removeExtra(NotesWidgetProvider.EXTRA_ACTION_TYPE)
            NotesWidgetProvider.closeAllPopups(this)
            when (actionType) {
                NotesWidgetProvider.ACTION_TYPE_TEXT -> onNavigate("note_detail_screen")
                NotesWidgetProvider.ACTION_TYPE_CHECKLIST -> onNavigate("note_detail_screen?autoAction=checklist")
                NotesWidgetProvider.ACTION_TYPE_IMAGE -> onNavigate("note_detail_screen?autoAction=image")
                NotesWidgetProvider.ACTION_TYPE_VOICE -> onNavigate("note_detail_screen?autoAction=voice")
                NotesWidgetProvider.ACTION_TYPE_DRAW -> onNavigate("note_detail_screen?autoAction=draw")
                NotesWidgetProvider.ACTION_TYPE_SETTINGS -> onNavigate("settings_screen?focus=widget")
            }
            return
        }

        // Web Clipper / Paylaşılan Metin (Share Intent)
        if (intent.action == Intent.ACTION_SEND && "text/plain" == intent.type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            if (!sharedText.isNullOrBlank()) {
                val encodedText = java.net.URLEncoder.encode(sharedText, "UTF-8")
                val encodedTitle = java.net.URLEncoder.encode(sharedSubject ?: "", "UTF-8")
                // Intent action ve verisini temizle ki geri döndüğünde tekrar tetiklenmesin
                intent.action = Intent.ACTION_MAIN
                intent.removeExtra(Intent.EXTRA_TEXT)
                intent.removeExtra(Intent.EXTRA_SUBJECT)
                onNavigate("note_detail_screen?sharedText=$encodedText&sharedTitle=$encodedTitle")
                return
            }
        }

        // Hızlı Ayarlar Kutucuğu (Quick Settings Tile) veya Widget + butonuna basıldıysa yeni not ekranı
        if (intent.getBooleanExtra(NotesWidgetProvider.EXTRA_NEW_NOTE, false) ||
            intent.getBooleanExtra("open_new_note", false) ||
            intent.action == "com.example.noteapp.ACTION_QUICK_NOTE") {
            intent.removeExtra(NotesWidgetProvider.EXTRA_NEW_NOTE)
            intent.removeExtra("open_new_note")
            intent.action = Intent.ACTION_MAIN
            NotesWidgetProvider.closeAllPopups(this)
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
                                when (result) {
                                    is BiometricResult.AuthenticationSuccess -> {
                                        onNavigate("note_detail_screen?noteId=$noteId")
                                    }
                                    is BiometricResult.AuthenticationError -> {
                                        if (!settingsManager.settings.value.masterPin.isNullOrBlank()) {
                                            onUnlockWithPinRequired(note)
                                        } else {
                                            Toast.makeText(this@MainActivity, result.error, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    is BiometricResult.AuthenticationFailed -> {
                                        Toast.makeText(this@MainActivity, "Kimlik doğrulama başarısız", Toast.LENGTH_SHORT).show()
                                    }
                                    is BiometricResult.FeatureUnavailable,
                                    is BiometricResult.HardwareUnavailable,
                                    is BiometricResult.NoneEnrolled -> {
                                        // Biyometrik yoksa veya tanımlı değilse Master PIN sor
                                        onUnlockWithPinRequired(note)
                                    }
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
