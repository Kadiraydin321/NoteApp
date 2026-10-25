package com.example.noteapp.desktop.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.desktop.data.backup.DesktopBackupManager
import com.example.noteapp.desktop.data.local.DatabaseHelper
import com.example.noteapp.desktop.data.local.DesktopNoteDao
import com.example.noteapp.desktop.data.security.DesktopCryptoManager
import com.example.noteapp.desktop.data.security.DesktopSecurityManager
import com.example.noteapp.desktop.data.sync.DesktopSyncServer
import com.example.noteapp.desktop.data.sync.SyncServerState
import com.example.noteapp.desktop.domain.model.Category
import com.example.noteapp.desktop.domain.model.Note
import com.example.noteapp.desktop.presentation.components.*
import com.example.noteapp.desktop.presentation.dialogs.*
import com.example.noteapp.desktop.presentation.theme.NotePresets
import com.example.noteapp.desktop.presentation.theme.getNoteColorSpec
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

enum class NavFilter {
    ALL,
    PINNED,
    VAULT,
    REMINDERS,
    ARCHIVE,
    TRASH
}

enum class NoteSortOrder(val title: String) {
    MODIFIED_DESC("Değiştirilme (En Yeni)"),
    MODIFIED_ASC("Değiştirilme (En Eski)"),
    CREATED_DESC("Oluşturulma (En Yeni)"),
    CREATED_ASC("Oluşturulma (En Eski)"),
    TITLE_AZ("Başlık (A - Z)"),
    TITLE_ZA("Başlık (Z - A)")
}

@Composable
fun MainScreen(
    dao: DesktopNoteDao,
    backupManager: DesktopBackupManager,
    syncServer: DesktopSyncServer,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    // Veri durumları
    var activeNotes by remember { mutableStateOf(dao.getActiveNotes()) }
    var archivedNotes by remember { mutableStateOf(dao.getArchivedNotes()) }
    var trashNotes by remember { mutableStateOf(dao.getTrashNotes()) }
    var reminderNotes by remember { mutableStateOf(dao.getReminderNotes()) }
    var categories by remember { mutableStateOf(dao.getAllCategories()) }

    var selectedNav by remember { mutableStateOf(NavFilter.ALL) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(NoteSortOrder.MODIFIED_DESC) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf(setOf<Long>()) }

    var selectedNoteId by remember { mutableStateOf<Long?>(null) }
    var editorTitle by remember { mutableStateOf("") }
    var editorContent by remember { mutableStateOf("") }
    var editorColor by remember { mutableStateOf(0) }
    var editorCategoryId by remember { mutableStateOf<Long?>(null) }
    var editorIsPinned by remember { mutableStateOf(false) }
    var editorIsLocked by remember { mutableStateOf(false) }
    var editorReminderTime by remember { mutableStateOf<Long?>(null) }
    var editorAttachments by remember { mutableStateOf(listOf<String>()) }
    var editorBackgroundImage by remember { mutableStateOf<String?>(null) }
    var markdownViewMode by remember { mutableStateOf(MarkdownViewMode.SPLIT) }
    var backlinks by remember { mutableStateOf<List<Note>>(emptyList()) }

    // Undo / Redo Stacks
    val undoStack = remember { mutableStateListOf<Pair<String, String>>() }
    val redoStack = remember { mutableStateListOf<Pair<String, String>>() }
    var lastSavedPair by remember { mutableStateOf(Pair("", "")) }

    // Dialog Durumları
    var showVaultDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showDrawingDialog by remember { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    // Sync Server State
    var syncServerState by remember {
        mutableStateOf(
            SyncServerState(
                isRunning = syncServer.isRunning(),
                localIp = DesktopSyncServer.getLocalIpAddress(),
                port = 8765,
                pairingPin = syncServer.pairingPin,
                lastSyncMessage = syncServer.lastMessage
            )
        )
    }

    fun refreshAllData() {
        activeNotes = dao.getActiveNotes()
        archivedNotes = dao.getArchivedNotes()
        trashNotes = dao.getTrashNotes()
        reminderNotes = dao.getReminderNotes()
        categories = dao.getAllCategories()
        if (selectedNoteId != null && editorTitle.isNotBlank()) {
            backlinks = dao.getNotesLinkingTo(editorTitle, selectedNoteId!!)
        }
    }

    LaunchedEffect(Unit) {
        val sevenDays = 7L * 24 * 60 * 60 * 1000L
        dao.cleanUpOldTrashNotes(System.currentTimeMillis() - sevenDays)
        refreshAllData()
    }

    fun commitToUndo(title: String, content: String) {
        if (title != lastSavedPair.first || content != lastSavedPair.second) {
            if (lastSavedPair.first.isNotEmpty() || lastSavedPair.second.isNotEmpty()) {
                undoStack.add(lastSavedPair)
                if (undoStack.size > 50) undoStack.removeAt(0)
            }
            lastSavedPair = Pair(title, content)
            redoStack.clear()
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(Pair(editorTitle, editorContent))
            val prev = undoStack.removeAt(undoStack.size - 1)
            editorTitle = prev.first
            editorContent = prev.second
            lastSavedPair = prev
            val currentId = selectedNoteId ?: return
            val existing = dao.getNoteById(currentId) ?: return
            val updated = existing.copy(title = editorTitle, content = editorContent, updatedAt = System.currentTimeMillis())
            dao.updateNote(updated)
            refreshAllData()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(Pair(editorTitle, editorContent))
            val next = redoStack.removeAt(redoStack.size - 1)
            editorTitle = next.first
            editorContent = next.second
            lastSavedPair = next
            val currentId = selectedNoteId ?: return
            val existing = dao.getNoteById(currentId) ?: return
            val updated = existing.copy(title = editorTitle, content = editorContent, updatedAt = System.currentTimeMillis())
            dao.updateNote(updated)
            refreshAllData()
        }
    }

    fun loadNoteIntoEditor(note: Note) {
        selectedNoteId = note.id
        editorTitle = note.title
        editorContent = if (note.isLocked && !DesktopCryptoManager.isVaultUnlocked) {
            "🔒 Gizli Kasa İçeriği. Düzenlemek için şifreyi giriniz."
        } else if (note.isLocked && DesktopCryptoManager.isVaultUnlocked) {
            DesktopCryptoManager.decryptContent(note.content)
        } else {
            note.content
        }
        editorColor = note.color
        editorCategoryId = note.categoryId
        editorIsPinned = note.isPinned
        editorIsLocked = note.isLocked
        editorReminderTime = note.reminderTime
        editorAttachments = note.attachments
        editorBackgroundImage = note.backgroundImage
        lastSavedPair = Pair(editorTitle, editorContent)
        undoStack.clear()
        redoStack.clear()
        backlinks = dao.getNotesLinkingTo(note.title, note.id)
    }

    fun saveCurrentNote() {
        val currentId = selectedNoteId ?: return
        val existingNote = dao.getNoteById(currentId) ?: return

        val finalContent = if (editorIsLocked && DesktopCryptoManager.isVaultUnlocked) {
            DesktopCryptoManager.encryptContent(editorContent)
        } else if (editorIsLocked && !DesktopCryptoManager.isVaultUnlocked) {
            existingNote.content
        } else {
            editorContent
        }

        val updated = existingNote.copy(
            title = editorTitle,
            content = finalContent,
            color = editorColor,
            categoryId = editorCategoryId,
            isPinned = editorIsPinned,
            isLocked = editorIsLocked,
            reminderTime = editorReminderTime,
            attachments = editorAttachments,
            backgroundImage = editorBackgroundImage,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateNote(updated)
        refreshAllData()
    }

    fun createNewNote() {
        val newNote = Note(
            title = "",
            content = "",
            timestamp = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            color = 0,
            categoryId = selectedCategoryId,
            isPinned = false,
            isArchived = false,
            isDeleted = false,
            isLocked = false
        )
        val newId = dao.insertNote(newNote)
        refreshAllData()
        val created = dao.getNoteById(newId)
        if (created != null) {
            loadNoteIntoEditor(created)
        }
    }

    fun duplicateCurrentNote() {
        val current = dao.getNoteById(selectedNoteId ?: 0) ?: return
        val copyTitle = if (current.title.isNotBlank()) "${current.title} (Kopya)" else "Kopya Not"
        val duplicated = current.copy(
            id = 0,
            title = copyTitle,
            timestamp = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val newId = dao.insertNote(duplicated)
        refreshAllData()
        dao.getNoteById(newId)?.let { loadNoteIntoEditor(it) }
        bannerMessage = "Not kopyalandı."
    }

    // Hashtag listesi
    val allHashtags = remember(activeNotes, archivedNotes) {
        val regex = Regex("#([a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-]+)")
        (activeNotes + archivedNotes).flatMap { note ->
            regex.findAll("${note.title} ${note.content}").map { it.groupValues[1] }
        }.distinct().sorted()
    }

    // Filtrelenmiş ve sıralanmış not listesi
    val currentNoteList = remember(activeNotes, archivedNotes, trashNotes, reminderNotes, selectedNav, selectedCategoryId, selectedTag, searchQuery, sortOrder) {
        val baseList = when (selectedNav) {
            NavFilter.ALL -> activeNotes
            NavFilter.PINNED -> activeNotes.filter { it.isPinned }
            NavFilter.VAULT -> activeNotes.filter { it.isLocked }
            NavFilter.REMINDERS -> (activeNotes + archivedNotes).filter { it.reminderTime != null && it.reminderTime > 0 }
            NavFilter.ARCHIVE -> archivedNotes
            NavFilter.TRASH -> trashNotes
        }

        val filtered = baseList.filter { note ->
            val matchCat = selectedCategoryId == null || note.categoryId == selectedCategoryId
            val matchQuery = searchQuery.isBlank() ||
                    note.title.contains(searchQuery, ignoreCase = true) ||
                    note.content.contains(searchQuery, ignoreCase = true)
            val matchTag = selectedTag == null ||
                    note.content.contains("#$selectedTag", ignoreCase = true) ||
                    note.title.contains("#$selectedTag", ignoreCase = true)
            matchCat && matchQuery && matchTag
        }

        val (pinned, unpinned) = filtered.partition { it.isPinned }

        fun sortList(list: List<Note>): List<Note> = when (sortOrder) {
            NoteSortOrder.MODIFIED_DESC -> list.sortedByDescending { if (it.updatedAt > 0) it.updatedAt else it.timestamp }
            NoteSortOrder.MODIFIED_ASC -> list.sortedBy { if (it.updatedAt > 0) it.updatedAt else it.timestamp }
            NoteSortOrder.CREATED_DESC -> list.sortedByDescending { if (it.createdAt > 0) it.createdAt else it.timestamp }
            NoteSortOrder.CREATED_ASC -> list.sortedBy { if (it.createdAt > 0) it.createdAt else it.timestamp }
            NoteSortOrder.TITLE_AZ -> list.sortedBy { it.title.ifBlank { it.content }.lowercase() }
            NoteSortOrder.TITLE_ZA -> list.sortedByDescending { it.title.ifBlank { it.content }.lowercase() }
        }

        sortList(pinned) + sortList(unpinned)
    }

    LaunchedEffect(currentNoteList) {
        if (selectedNoteId == null && currentNoteList.isNotEmpty()) {
            loadNoteIntoEditor(currentNoteList.first())
        }
    }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(3500)
            bannerMessage = null
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ==========================================
        // 1. SOL GEZİNME MENÜSÜ (SIDEBAR)
        // ==========================================
        Surface(
            modifier = Modifier.width(240.dp).fillMaxHeight(),
            color = Color(0xFF090E17),
            contentColor = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Logo ve Başlık
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        val appLogo = remember { com.example.noteapp.desktop.util.AppIconRenderer.createIconBitmap(96) }
                        androidx.compose.foundation.Image(
                            bitmap = appLogo,
                            contentDescription = "Not Defterim Logosu",
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Not Defterim", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), color = Color(0xFFF8FAFC))
                            Text("Masaüstü Sürümü", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Color(0xFF94A3B8))
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Yeni Not Butonu - Modern Derin Mavi / Indigo
                    Button(
                        onClick = { createNewNote() },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Yeni Not", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Gezinme Menüleri
                    SidebarItem(
                        icon = Icons.AutoMirrored.Filled.StickyNote2,
                        label = "Tüm Notlar",
                        count = activeNotes.size,
                        isSelected = selectedNav == NavFilter.ALL && selectedCategoryId == null,
                        onClick = { selectedNav = NavFilter.ALL; selectedCategoryId = null }
                    )
                    SidebarItem(
                        icon = Icons.Default.PushPin,
                        label = "Sabitlenenler",
                        count = activeNotes.count { it.isPinned },
                        isSelected = selectedNav == NavFilter.PINNED,
                        onClick = { selectedNav = NavFilter.PINNED }
                    )
                    SidebarItem(
                        icon = Icons.Default.Lock,
                        label = "Kilitli Kasa",
                        count = activeNotes.count { it.isLocked },
                        isSelected = selectedNav == NavFilter.VAULT,
                        badgeColor = if (DesktopCryptoManager.isVaultUnlocked) Color(0xFF10B981) else Color(0xFFF59E0B),
                        onClick = {
                            selectedNav = NavFilter.VAULT
                            if (!DesktopCryptoManager.isVaultUnlocked) {
                                showVaultDialog = true
                            }
                        }
                    )
                    SidebarItem(
                        icon = Icons.Default.Notifications,
                        label = "Hatırlatıcılar",
                        count = reminderNotes.size,
                        isSelected = selectedNav == NavFilter.REMINDERS,
                        badgeColor = Color(0xFF38BDF8),
                        onClick = { selectedNav = NavFilter.REMINDERS; selectedCategoryId = null; selectedTag = null }
                    )
                    SidebarItem(
                        icon = Icons.Default.Archive,
                        label = "Arşiv",
                        count = archivedNotes.size,
                        isSelected = selectedNav == NavFilter.ARCHIVE,
                        onClick = { selectedNav = NavFilter.ARCHIVE; selectedTag = null }
                    )
                    SidebarItem(
                        icon = Icons.Default.Delete,
                        label = "Çöp Kutusu",
                        count = trashNotes.size,
                        isSelected = selectedNav == NavFilter.TRASH,
                        onClick = { selectedNav = NavFilter.TRASH; selectedTag = null }
                    )

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Spacer(Modifier.height(10.dp))

                    // Kategoriler Başlığı
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "KATEGORİLER",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = Color(0xFF94A3B8)
                        )
                        IconButton(onClick = { showCategoryDialog = true }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Yönet", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Kategoriler Listesi
                    LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                        items(categories) { cat ->
                            val isCatSelected = selectedCategoryId == cat.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedCategoryId = if (isCatSelected) null else cat.id
                                    },
                                color = if (isCatSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (cat.color != 0) Color(cat.color) else MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        color = if (isCatSelected) Color(0xFFF8FAFC) else Color(0xFFCBD5E1),
                                        modifier = Modifier.weight(1f)
                                    )
                                    val catCount = activeNotes.count { it.categoryId == cat.id }
                                    if (catCount > 0) {
                                        Text(
                                            text = catCount.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Etiketler (Hashtags) Başlığı
                    if (allHashtags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "ETİKETLER",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 100.dp)) {
                            items(allHashtags) { tag ->
                                val isTagSelected = selectedTag == tag
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedTag = if (isTagSelected) null else tag
                                        },
                                    color = if (isTagSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = if (isTagSelected) FontWeight.Bold else FontWeight.Normal),
                                            color = if (isTagSelected) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Alt Kısım: Araçlar & Güvenlik & Wi-Fi Sync
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Spacer(Modifier.height(6.dp))

                    SidebarToolButton(
                        icon = Icons.Default.Wifi,
                        label = "Wi-Fi Senkronizasyon",
                        isActive = syncServerState.isRunning,
                        activeColor = Color(0xFF10B981),
                        onClick = { showSyncDialog = true }
                    )

                    SidebarToolButton(
                        icon = Icons.Default.Backup,
                        label = "Yedek Al / Yükle (.zip)",
                        onClick = {
                            val chooser = JFileChooser()
                            chooser.dialogTitle = "Yedekleme İşlemi Seçin"
                            chooser.fileFilter = FileNameExtensionFilter("Not Defterim Yedekleri (*.zip, *.notevault)", "zip", "notevault")
                            val ret = chooser.showSaveDialog(null)
                            if (ret == JFileChooser.APPROVE_OPTION) {
                                val selectedFile = chooser.selectedFile
                                val fileToSave = if (!selectedFile.name.endsWith(".zip") && !selectedFile.name.endsWith(".notevault")) {
                                    File(selectedFile.parentFile, selectedFile.name + ".zip")
                                } else selectedFile

                                val result = backupManager.exportBackup(fileToSave)
                                bannerMessage = when (result) {
                                    is com.example.noteapp.desktop.data.backup.BackupResult.Success -> "✅ " + result.message
                                    is com.example.noteapp.desktop.data.backup.BackupResult.Error -> "❌ " + result.message
                                }
                            }
                        }
                    )

                    SidebarToolButton(
                        icon = Icons.Default.Shield,
                        label = "Güvenlik & Anti-SS",
                        isActive = DesktopSecurityManager.isScreenshotProtectionActive,
                        activeColor = Color(0xFF10B981),
                        onClick = { showSecurityDialog = true }
                    )

                    SidebarToolButton(
                        icon = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        label = if (isDarkTheme) "Açık Tema" else "Koyu Tema",
                        onClick = onToggleTheme
                    )
                }
            }
        }

        // ==========================================
        // 2. ORTA PANE (NOT LİSTESİ & ARAMA)
        // ==========================================
        Surface(
            modifier = Modifier.width(320.dp).fillMaxHeight(),
            color = Color(0xFF0E1524),
            contentColor = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                // Şık ve Asla Kesilmeyen Arama Kutusu
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFFF8FAFC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(Color(0xFF3B82F6)),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .background(Color(0xFF1E293B), RoundedCornerShape(19.dp))
                                .border(BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.5f)), RoundedCornerShape(19.dp))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Notlarda ara...",
                                        color = Color(0xFF64748B),
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Temizle",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { searchQuery = "" }
                                )
                            }
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

                // Liste Başlığı, Sıralama, Çoklu Seçim & Not Sayısı
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${currentNoteList.size} Not",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                            color = Color(0xFF94A3B8)
                        )

                        // Sıralama Menüsü
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.Sort, contentDescription = "Sırala", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                NoteSortOrder.values().forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.title, fontSize = 12.sp, fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            sortOrder = order
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Çoklu Seçim Butonu
                        IconButton(
                            onClick = {
                                isMultiSelectMode = !isMultiSelectMode
                                if (!isMultiSelectMode) selectedNoteIds = emptySet()
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Default.Checklist,
                                contentDescription = "Çoklu Seçim",
                                tint = if (isMultiSelectMode) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    if (selectedNav == NavFilter.TRASH && currentNoteList.isNotEmpty()) {
                        TextButton(
                            onClick = { showEmptyTrashConfirm = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Çöpü Boşalt", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                        }
                    }
                }

                // Çoklu Seçim Eylem Çubuğu
                if (isMultiSelectMode && selectedNoteIds.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${selectedNoteIds.size} seçildi",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = {
                                    dao.moveNotesToTrash(selectedNoteIds.toList())
                                    selectedNoteIds = emptySet()
                                    refreshAllData()
                                }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Çöpe At", tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                                }
                                IconButton(onClick = {
                                    selectedNoteIds = emptySet()
                                    isMultiSelectMode = false
                                }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "İptal", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Not Kartları Listesi
                if (currentNoteList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(46.dp),
                                tint = Color(0xFF3B82F6).copy(alpha = 0.45f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Henüz not bulunmuyor",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentNoteList) { note ->
                            val cat = categories.find { it.id == note.categoryId }
                            NoteCard(
                                note = note,
                                category = cat,
                                isSelected = note.id == selectedNoteId,
                                isDarkTheme = isDarkTheme,
                                isVaultUnlocked = DesktopCryptoManager.isVaultUnlocked,
                                isMultiSelectMode = isMultiSelectMode,
                                isChecked = selectedNoteIds.contains(note.id),
                                onCheckedChange = { checked ->
                                    selectedNoteIds = if (checked) selectedNoteIds + note.id else selectedNoteIds - note.id
                                },
                                onClick = {
                                    saveCurrentNote()
                                    loadNoteIntoEditor(note)
                                },
                                onTogglePin = {
                                    dao.updateNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
                                    refreshAllData()
                                },
                                onMoveToTrash = {
                                    dao.moveToTrash(note.id)
                                    refreshAllData()
                                    if (selectedNoteId == note.id) {
                                        selectedNoteId = null
                                    }
                                },
                                onRestore = if (selectedNav == NavFilter.TRASH) {
                                    {
                                        dao.restoreFromTrash(note.id)
                                        refreshAllData()
                                    }
                                } else null,
                                onDeletePermanently = if (selectedNav == NavFilter.TRASH) {
                                    {
                                        dao.deletePermanently(note.id)
                                        refreshAllData()
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. SAĞ PANE (NOT EDİTÖRÜ & DETAY)
        // ==========================================
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF0B1120))) {
            val currentSelectedNote = activeNotes.find { it.id == selectedNoteId }
                ?: archivedNotes.find { it.id == selectedNoteId }
                ?: trashNotes.find { it.id == selectedNoteId }

            if (currentSelectedNote == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = Color(0xFF3B82F6).copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Görüntülemek veya düzenlemek için bir not seçin",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                            color = Color(0xFFF1F5F9)
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { createNewNote() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Yeni Not Oluştur", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                val noteColorSpec = getNoteColorSpec(editorColor, isDarkTheme)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (editorColor != 0) noteColorSpec.backgroundColor else MaterialTheme.colorScheme.surface)
                        .padding(18.dp)
                ) {
                    // Üst Eylem Araç Çubuğu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Kategori & Renk Seçici
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var showCatDropdown by remember { mutableStateOf(false) }
                            val currentCat = categories.find { it.id == editorCategoryId }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { showCatDropdown = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (currentCat?.color != null && currentCat.color != 0) Color(currentCat.color) else MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = currentCat?.name ?: "Kategori Yok",
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                        color = Color(0xFFCBD5E1)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF94A3B8))
                                }
                            }

                            DropdownMenu(
                                expanded = showCatDropdown,
                                onDismissRequest = { showCatDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Kategori Yok") },
                                    onClick = {
                                        editorCategoryId = null
                                        showCatDropdown = false
                                        saveCurrentNote()
                                    }
                                )
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (cat.color != 0) Color(cat.color) else MaterialTheme.colorScheme.primary)
                                            )
                                        },
                                        onClick = {
                                            editorCategoryId = cat.id
                                            showCatDropdown = false
                                            saveCurrentNote()
                                        }
                                    )
                                }
                            }

                            // Renk Paleti Butonu
                            var showColorPicker by remember { mutableStateOf(false) }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier.size(30.dp).clip(CircleShape).clickable { showColorPicker = true }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Palette, contentDescription = "Renk Seç", modifier = Modifier.size(16.dp), tint = Color(0xFF94A3B8))
                                }
                            }

                            DropdownMenu(
                                expanded = showColorPicker,
                                onDismissRequest = { showColorPicker = false }
                            ) {
                                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    NotePresets.take(7).forEach { (colorInt, _) ->
                                        val color = if (colorInt != 0) Color(colorInt) else Color(0xFF94A3B8)
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .clickable {
                                                    editorColor = colorInt
                                                    showColorPicker = false
                                                    saveCurrentNote()
                                                }
                                                .border(
                                                    width = if (editorColor == colorInt) 2.dp else 0.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    NotePresets.drop(7).forEach { (colorInt, _) ->
                                        val color = Color(colorInt)
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .clickable {
                                                    editorColor = colorInt
                                                    showColorPicker = false
                                                    saveCurrentNote()
                                                }
                                                .border(
                                                    width = if (editorColor == colorInt) 2.dp else 0.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        // Sağ Eylemler: Hatırlatıcı, Çizim, Kopyala, Markdown, Arka Plan, Pin, Kilit, Arşiv, Sil
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Hatırlatıcı
                            IconButton(onClick = { showReminderDialog = true }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = "Hatırlatıcı Belirle",
                                    tint = if (editorReminderTime != null && editorReminderTime!! > 0) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Çizim Yap
                            IconButton(onClick = { showDrawingDialog = true }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Default.Brush,
                                    contentDescription = "Çizim Ekle",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Kopyasını Oluştur
                            IconButton(onClick = { duplicateCurrentNote() }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Notu Kopyala",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Markdown Dışa Aktar (.md)
                            IconButton(onClick = {
                                val chooser = JFileChooser()
                                chooser.dialogTitle = "Notu Markdown Olarak Kaydet"
                                val safeName = (editorTitle.ifBlank { "Not" }).replace("[^a-zA-Z0-9çğıöşüÇĞİÖŞÜ_ -]".toRegex(), "_")
                                chooser.selectedFile = File("$safeName.md")
                                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    val f = chooser.selectedFile
                                    val target = if (!f.name.endsWith(".md")) File(f.parentFile, "${f.name}.md") else f
                                    target.writeText("# $editorTitle\n\n$editorContent", Charsets.UTF_8)
                                    bannerMessage = "Markdown kaydedildi: ${target.name}"
                                }
                            }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Markdown Olarak Dışa Aktar",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Arka Plan Görseli
                            IconButton(onClick = {
                                if (editorBackgroundImage == null) {
                                    val chooser = JFileChooser()
                                    chooser.dialogTitle = "Arka Plan Görseli Seç"
                                    chooser.fileFilter = FileNameExtensionFilter("Görseller (*.png, *.jpg, *.jpeg, *.webp)", "png", "jpg", "jpeg", "webp")
                                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                        val chosen = chooser.selectedFile
                                        val dest = File(DatabaseHelper.attachmentsDir, "bg_${System.currentTimeMillis()}_${chosen.name}")
                                        chosen.copyTo(dest, overwrite = true)
                                        editorBackgroundImage = dest.absolutePath
                                        saveCurrentNote()
                                    }
                                } else {
                                    editorBackgroundImage = null
                                    saveCurrentNote()
                                }
                            }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = "Arka Plan Görseli",
                                    tint = if (editorBackgroundImage != null) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Sabitle
                            IconButton(onClick = {
                                editorIsPinned = !editorIsPinned
                                saveCurrentNote()
                            }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = "Sabitle",
                                    tint = if (editorIsPinned) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Kasa Kilidi
                            IconButton(onClick = {
                                if (!editorIsLocked) {
                                    if (!DesktopCryptoManager.hasMasterPassword()) {
                                        showVaultDialog = true
                                    } else {
                                        editorIsLocked = true
                                        saveCurrentNote()
                                    }
                                } else {
                                    editorIsLocked = false
                                    saveCurrentNote()
                                }
                            }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Kasa Kilidi",
                                    tint = if (editorIsLocked) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Arşivle
                            IconButton(onClick = {
                                dao.updateNote(currentSelectedNote.copy(isArchived = !currentSelectedNote.isArchived, updatedAt = System.currentTimeMillis()))
                                refreshAllData()
                            }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Default.Archive,
                                    contentDescription = "Arşivle",
                                    tint = if (currentSelectedNote.isArchived) MaterialTheme.colorScheme.secondary else Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Çöpe At
                            IconButton(onClick = {
                                dao.moveToTrash(currentSelectedNote.id)
                                refreshAllData()
                                selectedNoteId = null
                            }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Sil",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Başlık Alanı
                    TextField(
                        value = editorTitle,
                        onValueChange = {
                            commitToUndo(it, editorContent)
                            editorTitle = it
                            saveCurrentNote()
                        },
                        placeholder = {
                            Text(
                                "Başlık",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                                color = Color(0xFF64748B)
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = noteColorSpec.contentColor
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = noteColorSpec.contentColor,
                            unfocusedTextColor = noteColorSpec.contentColor,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp)
                    )

                    Spacer(Modifier.height(6.dp))

                    // Markdown Araç Çubuğu
                    MarkdownToolbar(
                        onInsertText = { prefix, suffix ->
                            commitToUndo(editorTitle, editorContent)
                            editorContent = editorContent + prefix + suffix
                            saveCurrentNote()
                        },
                        viewMode = markdownViewMode,
                        onViewModeChange = { markdownViewMode = it },
                        onUndo = { undo() },
                        onRedo = { redo() },
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty()
                    )

                    Spacer(Modifier.height(10.dp))

                    // İçerik Alanı (Edit / Split / Preview)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (markdownViewMode) {
                            MarkdownViewMode.EDIT_ONLY -> {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color(0xFF131D2E),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    TextField(
                                        value = editorContent,
                                        onValueChange = {
                                            editorContent = it
                                            saveCurrentNote()
                                        },
                                        placeholder = {
                                            Text(
                                                "Notunuzu yazın... (Markdown formatı desteklenir)",
                                                color = noteColorSpec.secondaryColor.copy(alpha = 0.6f)
                                            )
                                        },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = noteColorSpec.contentColor, lineHeight = 24.sp),
                                        colors = TextFieldDefaults.colors(
                                            focusedTextColor = noteColorSpec.contentColor,
                                            unfocusedTextColor = noteColorSpec.contentColor,
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxSize().padding(8.dp)
                                    )
                                }
                            }
                            MarkdownViewMode.PREVIEW_ONLY -> {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color(0xFF131D2E),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        MarkdownViewer(
                                            markdown = editorContent,
                                            onCheckboxToggle = { lineIndex, newChecked ->
                                                val lines = editorContent.lines().toMutableList()
                                                if (lineIndex < lines.size) {
                                                    val line = lines[lineIndex]
                                                    val prefix = if (newChecked) "- [x] " else "- [ ] "
                                                    lines[lineIndex] = prefix + line.substring(6)
                                                    editorContent = lines.joinToString("\n")
                                                    saveCurrentNote()
                                                }
                                            },
                                            onWikilinkClick = { linkedTitle ->
                                                val existing = dao.getNoteByTitle(linkedTitle)
                                                if (existing != null) {
                                                    saveCurrentNote()
                                                    loadNoteIntoEditor(existing)
                                                } else {
                                                    saveCurrentNote()
                                                    val newId = dao.insertNote(Note(title = linkedTitle, content = ""))
                                                    refreshAllData()
                                                    val created = dao.getNoteById(newId)
                                                    if (created != null) loadNoteIntoEditor(created)
                                                }
                                            },
                                            onTagClick = { tag ->
                                                selectedTag = tag
                                            }
                                        )
                                    }
                                }
                            }
                            MarkdownViewMode.SPLIT -> {
                                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Sol: Düzenleyici Paneli
                                    Surface(
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        color = Color(0xFF131D2E),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFF172338))
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Düzenleyici", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Color(0xFFCBD5E1))
                                            }
                                            TextField(
                                                value = editorContent,
                                                onValueChange = {
                                                    editorContent = it
                                                    saveCurrentNote()
                                                },
                                                placeholder = {
                                                    Text(
                                                        "Notunuzu yazın...",
                                                        color = noteColorSpec.secondaryColor.copy(alpha = 0.6f),
                                                        fontSize = 14.sp
                                                    )
                                                },
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = noteColorSpec.contentColor, lineHeight = 22.sp),
                                                colors = TextFieldDefaults.colors(
                                                    focusedTextColor = noteColorSpec.contentColor,
                                                    unfocusedTextColor = noteColorSpec.contentColor,
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent
                                                ),
                                                modifier = Modifier.fillMaxSize().padding(4.dp)
                                            )
                                        }
                                    }

                                    // Sağ: Önizleme Paneli
                                    Surface(
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        color = Color(0xFF131D2E),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFF172338))
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.secondary)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Canlı Önizleme", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Color(0xFFCBD5E1))
                                            }
                                            Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                                MarkdownViewer(
                                                    markdown = editorContent,
                                                    onCheckboxToggle = { lineIndex, newChecked ->
                                                        val lines = editorContent.lines().toMutableList()
                                                        if (lineIndex < lines.size) {
                                                            val line = lines[lineIndex]
                                                            val prefix = if (newChecked) "- [x] " else "- [ ] "
                                                            lines[lineIndex] = prefix + line.substring(6)
                                                            editorContent = lines.joinToString("\n")
                                                            saveCurrentNote()
                                                        }
                                                    },
                                                    onWikilinkClick = { linkedTitle ->
                                                        val existing = dao.getNoteByTitle(linkedTitle)
                                                        if (existing != null) {
                                                            saveCurrentNote()
                                                            loadNoteIntoEditor(existing)
                                                        } else {
                                                            saveCurrentNote()
                                                            val newId = dao.insertNote(Note(title = linkedTitle, content = ""))
                                                            refreshAllData()
                                                            val created = dao.getNoteById(newId)
                                                            if (created != null) loadNoteIntoEditor(created)
                                                        }
                                                    },
                                                    onTagClick = { tag ->
                                                        selectedTag = tag
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Geri Bağlantılar (Backlinks) Paneli
                    if (backlinks.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Bu Nota Bağlantı Verenler (${backlinks.size}):",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(Modifier.width(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                backlinks.forEach { linkedNote ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF1E293B),
                                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            saveCurrentNote()
                                            loadNoteIntoEditor(linkedNote)
                                        }
                                    ) {
                                        Text(
                                            linkedNote.title.ifBlank { "Başlıksız" },
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = Color(0xFF93C5FD),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Ekler ve Durum Çubuğu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Ek Ekle Butonu - Şık Modern Hap Buton
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        val chooser = JFileChooser()
                                        chooser.dialogTitle = "Görsel veya Dosya Eki Seç"
                                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                            val chosen = chooser.selectedFile
                                            val dest = File(DatabaseHelper.attachmentsDir, "${System.currentTimeMillis()}_${chosen.name}")
                                            chosen.copyTo(dest, overwrite = true)
                                            editorAttachments = editorAttachments + dest.absolutePath
                                            saveCurrentNote()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF94A3B8))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Ek Ekle", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = Color(0xFFCBD5E1))
                                }
                            }

                            editorAttachments.forEach { path ->
                                val file = File(path)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    modifier = Modifier.clickable {
                                        try {
                                            if (Desktop.isDesktopSupported() && file.exists()) {
                                                Desktop.getDesktop().open(file)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                        Text(file.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Color(0xFFCBD5E1), maxLines = 1)
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Kaldır",
                                            modifier = Modifier.size(12.dp).clickable {
                                                editorAttachments = editorAttachments.filter { it != path }
                                                saveCurrentNote()
                                            },
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }

                        // Karakter & Kelime Sayısı
                        val wordCount = if (editorContent.isBlank()) 0 else editorContent.trim().split("\\s+".toRegex()).size
                        Text(
                            text = "${editorContent.length} karakter • $wordCount kelime",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // Bildirim Banner'ı
            if (bannerMessage != null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = bannerMessage!!,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }

    // ==========================================
    // DİYALOGLAR
    // ==========================================
    if (showVaultDialog) {
        VaultPasswordDialog(
            onDismiss = { showVaultDialog = false },
            onSuccess = {
                refreshAllData()
                val current = dao.getNoteById(selectedNoteId ?: 0)
                if (current != null) loadNoteIntoEditor(current)
                bannerMessage = "🔓 Kasa kilidi açıldı."
            }
        )
    }

    if (showCategoryDialog) {
        CategoryManageDialog(
            categories = categories,
            onAddCategory = { name, color ->
                dao.insertCategory(Category(name = name, color = color))
                refreshAllData()
            },
            onDeleteCategory = { id ->
                dao.deleteCategory(id)
                refreshAllData()
            },
            onDismiss = { showCategoryDialog = false }
        )
    }

    if (showSyncDialog) {
        SyncDialog(
            serverState = syncServerState,
            onStartServer = {
                syncServer.start()
                syncServerState = SyncServerState(
                    isRunning = syncServer.isRunning(),
                    localIp = DesktopSyncServer.getLocalIpAddress(),
                    port = 8765,
                    pairingPin = syncServer.pairingPin,
                    lastSyncMessage = syncServer.lastMessage
                )
            },
            onStopServer = {
                syncServer.stop()
                syncServerState = SyncServerState(
                    isRunning = false,
                    localIp = DesktopSyncServer.getLocalIpAddress(),
                    port = 8765,
                    pairingPin = syncServer.pairingPin,
                    lastSyncMessage = "Durduruldu"
                )
            },
            onRefreshPin = {
                syncServer.refreshPin()
                syncServerState = syncServerState.copy(pairingPin = syncServer.pairingPin)
            },
            onDismiss = {
                showSyncDialog = false
                refreshAllData()
            }
        )
    }

    if (showSecurityDialog) {
        SecuritySettingsDialog(
            onDismiss = { showSecurityDialog = false }
        )
    }

    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            title = { Text("Çöp Kutusunu Boşalt") },
            text = { Text("Çöp kutusundaki tüm notlar kalıcı olarak silinecek. Bu işlem geri alınamaz!") },
            confirmButton = {
                Button(
                    onClick = {
                        dao.emptyTrash()
                        refreshAllData()
                        showEmptyTrashConfirm = false
                        bannerMessage = "Çöp kutusu boşaltıldı."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Evet, Boşalt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirm = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showReminderDialog) {
        ReminderDialog(
            initialReminderTime = editorReminderTime,
            onSaveReminder = { time ->
                editorReminderTime = time
                saveCurrentNote()
                showReminderDialog = false
                bannerMessage = if (time != null) "⏰ Hatırlatıcı ayarlandı." else "Hatırlatıcı kaldırıldı."
            },
            onDismiss = { showReminderDialog = false }
        )
    }

    if (showDrawingDialog) {
        DesktopDrawingDialog(
            onDismiss = { showDrawingDialog = false },
            onSaveDrawing = { savedPath ->
                editorAttachments = editorAttachments + savedPath
                saveCurrentNote()
                showDrawingDialog = false
                bannerMessage = "🎨 Çizim nota eklendi."
            }
        )
    }
}

@Composable
fun SidebarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    isSelected: Boolean,
    badgeColor: Color? = null,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFF1E293B) else Color.Transparent
    val contentColor = if (isSelected) Color(0xFFF8FAFC) else Color(0xFFCBD5E1)
    val iconTint = if (isSelected) MaterialTheme.colorScheme.primary else (badgeColor ?: Color(0xFF94A3B8))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Seçili göstergesi (İnce mavi dikey çizgi)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp
                ),
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            if (count > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0x333B82F6) else Color(0x1AFFFFFF),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = if (isSelected) Color(0xFF93C5FD) else Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SidebarToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = Color(0xFF10B981),
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) activeColor else Color(0xFF94A3B8),
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                color = Color(0xFFCBD5E1),
                modifier = Modifier.weight(1f)
            )
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(activeColor)
                )
            }
        }
    }
}
