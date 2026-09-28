package com.example.noteapp.presentation.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    state: NotesState,
    onSearchChange: (String) -> Unit,
    onNoteClick: (Note) -> Unit,
    onAddNoteClick: () -> Unit,
    onPinNote: (Note) -> Unit,
    onArchiveNote: (Note) -> Unit,
    onMoveToTrash: (Note) -> Unit,
    onRestoreNote: (Note) -> Unit,
    onDeletePermanently: (Note) -> Unit,
    onEmptyTrash: () -> Unit,
    onCategorySelect: (Category?) -> Unit,
    onViewModeChange: (NotesViewMode) -> Unit,
    onAddCategory: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Yeni Kategori Oluştur") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { Text("Kategori adı") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddCategory(newCategoryName)
                    newCategoryName = ""
                    showAddCategoryDialog = false
                }) {
                    Text("Ekle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Modern Note",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
                HorizontalDivider()

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Notes, contentDescription = null) },
                    label = { Text("Tüm Notlar") },
                    selected = state.viewMode == NotesViewMode.ALL,
                    onClick = {
                        onViewModeChange(NotesViewMode.ALL)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Archive, contentDescription = null) },
                    label = { Text("Arşiv") },
                    selected = state.viewMode == NotesViewMode.ARCHIVE,
                    onClick = {
                        onViewModeChange(NotesViewMode.ARCHIVE)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    label = { Text("Çöp Kutusu") },
                    selected = state.viewMode == NotesViewMode.TRASH,
                    onClick = {
                        onViewModeChange(NotesViewMode.TRASH)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kategoriler", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Kategori Ekle")
                    }
                }

                state.categories.forEach { category ->
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                        label = { Text(category.name) },
                        selected = state.selectedCategory?.id == category.id,
                        onClick = {
                            onCategorySelect(category)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Ayarlar") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSettingsClick()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (state.viewMode) {
                                NotesViewMode.ALL -> "Notlarım"
                                NotesViewMode.ARCHIVE -> "Arşiv"
                                NotesViewMode.TRASH -> "Çöp Kutusu"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menü")
                        }
                    },
                    actions = {
                        if (state.viewMode == NotesViewMode.TRASH) {
                            TextButton(onClick = onEmptyTrash) {
                                Text("Çöpü Boşalt", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                        }
                    }
                )
            },
            floatingActionButton = {
                if (state.viewMode == NotesViewMode.ALL) {
                    FloatingActionButton(
                        onClick = onAddNoteClick,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Yeni Not")
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Arama Çubuğu
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Notlarda ara...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                // Kategoriler Çipi
                if (state.viewMode == NotesViewMode.ALL && state.categories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = state.selectedCategory == null,
                                onClick = { onCategorySelect(null) },
                                label = { Text("Tümü") }
                            )
                        }
                        items(state.categories) { category ->
                            FilterChip(
                                selected = state.selectedCategory?.id == category.id,
                                onClick = { onCategorySelect(category) },
                                label = { Text(category.name) }
                            )
                        }
                    }
                }

                val filteredNotes = state.notes.filter {
                    it.title.contains(state.searchQuery, ignoreCase = true) ||
                            it.content.contains(state.searchQuery, ignoreCase = true)
                }

                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (state.searchQuery.isEmpty()) "Gösterilecek not bulunamadı" else "Sonuç bulunamadı",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 12.dp
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                viewMode = state.viewMode,
                                onClick = { onNoteClick(note) },
                                onPinClick = { onPinNote(note) },
                                onArchiveClick = { onArchiveNote(note) },
                                onDeleteClick = { onMoveToTrash(note) },
                                onRestoreClick = { onRestoreNote(note) },
                                onDeletePermanentlyClick = { onDeletePermanently(note) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCard(
    note: Note,
    viewMode: NotesViewMode,
    onClick: () -> Unit,
    onPinClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDeletePermanentlyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (note.color != 0) Color(note.color) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // İlk Görsel Eki Varsa Kartta Göster
            val firstImage = note.attachments.firstOrNull { !it.endsWith(".mp4") && !it.endsWith(".m4a") }
            firstImage?.let { imgPath ->
                AsyncImage(
                    model = File(imgPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (note.title.isNotEmpty()) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (viewMode == NotesViewMode.ALL) {
                    IconButton(
                        onClick = onPinClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Default.OutlinedFlag,
                            contentDescription = "Sabitle",
                            tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (note.isLocked) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Kilitli",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Kilitli Not",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (note.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Alt Butonlar / Göstergeler
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (note.reminderTime != null) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = "Hatırlatıcı",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (note.attachments.any { it.endsWith(".mp4") || it.endsWith(".m4a") }) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Ses Kaydı",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Row {
                    when (viewMode) {
                        NotesViewMode.ALL -> {
                            IconButton(onClick = onArchiveClick, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Archive, contentDescription = "Arşivle", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil", modifier = Modifier.size(18.dp))
                            }
                        }
                        NotesViewMode.ARCHIVE -> {
                            IconButton(onClick = onArchiveClick, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Unarchive, contentDescription = "Arşivden Çıkar", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil", modifier = Modifier.size(18.dp))
                            }
                        }
                        NotesViewMode.TRASH -> {
                            IconButton(onClick = onRestoreClick, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.RestoreFromTrash, contentDescription = "Geri Yükle", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onDeletePermanentlyClick, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Kalıcı Olarak Sil", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
