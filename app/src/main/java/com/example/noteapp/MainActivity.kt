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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

    private val biometricPromptManager by lazy {
        BiometricPromptManager(this)
    }

    private val isPrivacyShieldActive = mutableStateOf(false)
    private var navControllerRef: NavController? = null
    private var pendingWidgetIntent by mutableStateOf<Intent?>(null)
    private var wasAppInBackground = false
    private var activeLockedNoteId: Long? = null

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
                dynamicColor = appSettings.dynamicColor
            ) {
                var showWelcomeScreen by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(100)
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
                            onLayoutModeChange = viewModel::setLayoutMode,
                            onToggleNoteSelection = viewModel::toggleNoteSelection,
                            onSelectAllNotes = viewModel::selectAllNotes,
                            onClearSelection = viewModel::clearSelection,
                            onDeleteSelectedNotes = viewModel::deleteSelectedNotes,
                            onUpdateCategoryForSelected = viewModel::updateCategoryForSelected,
                            onTogglePinForSelected = viewModel::togglePinForSelected,
                            onDuplicateNote = viewModel::onDuplicateNote,
                            onFilterTypeChange = viewModel::setFilterType,
                            onSortOrderChange = viewModel::setSortOrder,
                            onSelectTag = viewModel::onSelectTag,
                            onSettingsClick = {
                                navController.navigate("settings_screen")
                            }
                        )
                    }

                    composable(
                        route = "note_detail_screen?noteId={noteId}&autoAction={autoAction}&sharedText={sharedText}&sharedTitle={sharedTitle}",
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
                            onFontSizeChange = viewModel::onFontSizeChange
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
                        route = "drawing_screen?drawingPath={drawingPath}",
                        arguments = listOf(navArgument("drawingPath") { type = NavType.StringType; defaultValue = "" })
                    ) { drawingBackStack ->
                        DrawingScreen(
                            onDrawingSaved = { drawingPath ->
                                val sourcePath = drawingBackStack.arguments?.getString("drawingPath")
                                if (sourcePath.isNullOrBlank()) {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("drawing_path", drawingPath)
                                } else {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("edited_image_pair", Pair(sourcePath, drawingPath))
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
                            },
                            drawingPath = drawingBackStack.arguments?.getString("drawingPath")?.takeIf { it.isNotBlank() }?.let {
                                try { java.net.URLDecoder.decode(it, java.nio.charset.StandardCharsets.UTF_8.toString()) } catch (_: Exception) { it }
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
                            onWidgetShowContentChange = viewModel::setWidgetShowContent,
                            onDefaultColorChange = viewModel::setDefaultNoteColor,
                            onDynamicColorChange = viewModel::setDynamicColor,
                            onThemeModeChange = viewModel::setThemeMode,
                            onLayoutModeChange = viewModel::setLayoutMode,
                            onMasterPinChange = viewModel::setMasterPin,
                            onAutoLockChange = viewModel::setAutoLockOnExit,
                            onHighContrastChange = viewModel::setHighContrastNegative,
                            onRefreshWidget = viewModel::refreshWidget,
                            onExportToUri = { uri, pass -> viewModel.exportBackupToUri(uri, pass) },
                            onExportAndShare = { ctx, pass -> viewModel.exportAndShare(ctx, pass) },
                            onImportFromUri = { uri, clear, pass -> viewModel.importBackupFromUri(uri, clear, pass) },
                            onDismissBackupMessage = viewModel::dismissMessage,
                            onBackClick = {
                                navController.popBackStack()
                            }
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

                if (showWelcomeScreen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Not Defterim",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Hoş geldiniz",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
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
        // Kullanıcı uygulamadan ayrıldığında (arka plana geçtiğinde), eğer kilitli bir nottaysa
        // not ekranını kapatıp ana ekrana dönsün ki tekrar girildiğinde şifresiz açılmasın!
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

    override fun onResume() {
        super.onResume()
        isPrivacyShieldActive.value = false
        wasAppInBackground = false

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

        // Widget hızlı aksiyon butonları (Metin, Görsel, Ses, Çizim)
        val actionType = intent.getStringExtra(NotesWidgetProvider.EXTRA_ACTION_TYPE)
        if (actionType != null) {
            intent.removeExtra(NotesWidgetProvider.EXTRA_ACTION_TYPE)
            NotesWidgetProvider.closeAllPopups(this)
            when (actionType) {
                NotesWidgetProvider.ACTION_TYPE_TEXT -> onNavigate("note_detail_screen")
                NotesWidgetProvider.ACTION_TYPE_CHECKLIST -> onNavigate("note_detail_screen?autoAction=checklist")
                NotesWidgetProvider.ACTION_TYPE_IMAGE -> onNavigate("note_detail_screen?autoAction=image")
                NotesWidgetProvider.ACTION_TYPE_VOICE -> onNavigate("note_detail_screen?autoAction=voice")
                NotesWidgetProvider.ACTION_TYPE_DRAW -> onNavigate("note_detail_screen?autoAction=draw")
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

        // Widget eski + butonuna basıldıysa yeni not ekranı
        if (intent.getBooleanExtra(NotesWidgetProvider.EXTRA_NEW_NOTE, false)) {
            intent.removeExtra(NotesWidgetProvider.EXTRA_NEW_NOTE)
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
