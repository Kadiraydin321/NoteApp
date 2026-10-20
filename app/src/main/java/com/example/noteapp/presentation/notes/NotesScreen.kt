@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.noteapp.presentation.notes

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import com.example.noteapp.R
import com.example.noteapp.data.settings.CardCornerStyle
import com.example.noteapp.data.settings.CardElevationStyle
import com.example.noteapp.data.settings.DrawerCollapseMode
import com.example.noteapp.data.settings.QuickActionLocation
import com.example.noteapp.data.settings.SearchBarShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import coil.compose.AsyncImage
import com.example.noteapp.data.settings.NotesLayoutMode
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.presentation.components.parseMarkdownCardPreview
import com.example.noteapp.presentation.theme.getNoteColorSpec
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    state: NotesState,
    onSearchChange: (String) -> Unit,
    onNoteClick: (Note) -> Unit,
    onAddNoteClick: (autoAction: String?) -> Unit,
    onPinNote: (Note) -> Unit,
    onArchiveNote: (Note) -> Unit,
    onMoveToTrash: (Note) -> Unit,
    onRestoreNote: (Note) -> Unit,
    onDeletePermanently: (Note) -> Unit,
    onEmptyTrash: () -> Unit,
    onCategorySelect: (Category?) -> Unit,
    onViewModeChange: (NotesViewMode) -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (Category) -> Unit = {},
    onRenameCategory: (Category, String) -> Unit = { _, _ -> },
    onLayoutModeChange: (NotesLayoutMode) -> Unit = {},
    onToggleNoteSelection: (Long) -> Unit = {},
    onSelectAllNotes: (List<Note>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDeleteSelectedNotes: () -> Unit = {},
    onUpdateCategoryForSelected: (Long?) -> Unit = {},
    onUpdateColorForSelected: (Int) -> Unit = {},
    onTogglePinForSelected: () -> Unit = {},
    onDuplicateNote: (Note) -> Unit = {},
    onFilterTypeChange: (NoteTypeFilter) -> Unit = {},
    onSortOrderChange: (NoteSortOrder) -> Unit = {},
    onSelectTag: (String?) -> Unit = {},
    quickSyncState: QuickSyncState = QuickSyncState(),
    syncHost: String = "",
    onQuickSyncSend: () -> Unit = {},
    onQuickSyncPull: () -> Unit = {},
    onDismissQuickSyncMessage: () -> Unit = {},
    drawerCollapseMode: DrawerCollapseMode = DrawerCollapseMode.AUTO_ACCORDION,
    cardCornerStyle: CardCornerStyle = CardCornerStyle.ROUNDED,
    cardElevationStyle: CardElevationStyle = CardElevationStyle.BORDERED,
    quickActionLocation: QuickActionLocation = QuickActionLocation.DRAWER_HEADER,
    searchBarShape: SearchBarShape = SearchBarShape.PILL,
    onNavigateToStatistics: () -> Unit = {},
    onOpenTemplates: () -> Unit = {},
    onSettingsClick: () -> Unit
) {
    val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val screenDensity = LocalDensity.current
    val homeGlowColor = MaterialTheme.colorScheme.primary

    // Ekran açıldığında veya nota girip geri dönüldüğünde menünün donup gri ekran yapmasını önle
    LaunchedEffect(Unit) {
        if (drawerState.isOpen || drawerState.targetValue != DrawerValue.Closed) {
            drawerState.snapTo(DrawerValue.Closed)
        }
    }

    val handleOpenNote: (Note) -> Unit = remember(onNoteClick, drawerState, scope) {
        { note ->
            if (drawerState.isOpen) {
                scope.launch { drawerState.snapTo(DrawerValue.Closed) }
            }
            onNoteClick(note)
        }
    }

    val handleAddNote: (String?) -> Unit = remember(onAddNoteClick, drawerState, scope) {
        { action ->
            if (drawerState.isOpen) {
                scope.launch { drawerState.snapTo(DrawerValue.Closed) }
            }
            onAddNoteClick(action)
        }
    }

    val onShareNote: (Note) -> Unit = remember(context) {
        { note ->
            val fullText = buildString {
                if (note.title.isNotBlank()) appendLine("# ${note.title}\n")
                append(note.content)
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Not" })
                putExtra(Intent.EXTRA_TEXT, fullText)
            }
            context.startActivity(Intent.createChooser(intent, "Markdown Olarak Paylaş"))
        }
    }

    val handleDeleteNote: (Note) -> Unit = remember(scope, snackbarHostState) {
        { note ->
            onMoveToTrash(note)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "'${note.title.ifBlank { "Not" }}' çöp kutusuna taşındı",
                    actionLabel = "Geri Al",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onRestoreNote(note)
                }
            }
        }
    }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var showBatchCategoryDialog by remember { mutableStateOf(false) }
    var showBatchColorDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showSelectionActionsMenu by remember { mutableStateOf(false) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }
    var noteToDeletePermanently by remember { mutableStateOf<Note?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }
    var showQuickSyncDialog by remember { mutableStateOf(false) }

    var isCategoriesAccordionExpanded by rememberSaveable(drawerCollapseMode) {
        mutableStateOf(
            when (drawerCollapseMode) {
                DrawerCollapseMode.EXPANDED_ALL, DrawerCollapseMode.INLINE_EXPAND -> true
                DrawerCollapseMode.ALWAYS_ACCORDION -> state.selectedCategory != null
                DrawerCollapseMode.AUTO_ACCORDION -> state.selectedCategory != null || state.categories.size <= 5
            }
        )
    }
    var isCategoriesInlineExpanded by rememberSaveable(drawerCollapseMode) {
        mutableStateOf(
            state.selectedCategory != null && state.categories.indexOfFirst { it.id == state.selectedCategory?.id } >= 5
        )
    }

    var isTagsAccordionExpanded by rememberSaveable(drawerCollapseMode) {
        mutableStateOf(
            when (drawerCollapseMode) {
                DrawerCollapseMode.EXPANDED_ALL, DrawerCollapseMode.INLINE_EXPAND -> true
                DrawerCollapseMode.ALWAYS_ACCORDION -> state.selectedTag != null
                DrawerCollapseMode.AUTO_ACCORDION -> state.selectedTag != null || state.allHashtags.size <= 5
            }
        )
    }
    var isTagsInlineExpanded by rememberSaveable(drawerCollapseMode) {
        mutableStateOf(
            state.selectedTag != null && state.allHashtags.indexOf(state.selectedTag) >= 5
        )
    }

    LaunchedEffect(state.selectedCategory) {
        if (state.selectedCategory != null) {
            isCategoriesAccordionExpanded = true
            isCategoriesInlineExpanded = true
        }
    }

    LaunchedEffect(state.selectedTag) {
        if (state.selectedTag != null) {
            isTagsAccordionExpanded = true
            isTagsInlineExpanded = true
        }
    }

    LaunchedEffect(quickSyncState.message) {
        quickSyncState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onDismissQuickSyncMessage()
        }
    }

    val isSelectionMode = state.selectedNoteIds.isNotEmpty()

    BackHandler(enabled = drawerState.isOpen || isSelectionMode || isFabExpanded) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (isSelectionMode) {
            onClearSelection()
        } else if (isFabExpanded) {
            isFabExpanded = false
        }
    }

    val reserveFabSpace = !isSelectionMode &&
            (state.viewMode == NotesViewMode.ALL || state.viewMode == NotesViewMode.REMINDERS)

    val gridState = rememberLazyStaggeredGridState()
    val listState = rememberLazyListState()
    val compactListState = rememberLazyListState()

    LaunchedEffect(state.sortOrder) {
        gridState.scrollToItem(0)
        listState.scrollToItem(0)
        compactListState.scrollToItem(0)
    }

    val allHashtags = state.allHashtags
    val filteredNotes = state.filteredNotes
    val selectedNotes = filteredNotes.filter { it.id in state.selectedNoteIds }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isSelectionMode,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 320.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Not Defterim",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (quickActionLocation == QuickActionLocation.DRAWER_HEADER) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                scope.launch { drawerState.close() }
                                showQuickSyncDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Masaüstü ile Eşitle",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = {
                                scope.launch { drawerState.close() }
                                onSettingsClick()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Ayarlar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                    label = { Text("Tüm Notlar") },
                    selected = state.viewMode == NotesViewMode.ALL &&
                            state.filterType == NoteTypeFilter.ALL &&
                            state.selectedCategory == null && state.selectedTag == null,
                    onClick = {
                        onViewModeChange(NotesViewMode.ALL)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    label = { Text("Hatırlatıcılar") },
                    selected = state.viewMode == NotesViewMode.REMINDERS,
                    onClick = {
                        onViewModeChange(NotesViewMode.REMINDERS)
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
                
                // AKILLI KLASÖRLER (SMART FOLDERS)
                Text(
                    "Akıllı Klasörler",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    label = { Text("Kilitli Notlar") },
                    selected = state.viewMode == NotesViewMode.ALL && state.filterType == NoteTypeFilter.LOCKED,
                    onClick = {
                        onViewModeChange(NotesViewMode.ALL)
                        onFilterTypeChange(NoteTypeFilter.LOCKED)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Image, contentDescription = null) },
                    label = { Text("Görselli Notlar") },
                    selected = state.viewMode == NotesViewMode.ALL && state.filterType == NoteTypeFilter.MEDIA,
                    onClick = {
                        onViewModeChange(NotesViewMode.ALL)
                        onFilterTypeChange(NoteTypeFilter.MEDIA)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    label = { Text("Sesli Notlar") },
                    selected = state.viewMode == NotesViewMode.ALL && state.filterType == NoteTypeFilter.AUDIO,
                    onClick = {
                        onViewModeChange(NotesViewMode.ALL)
                        onFilterTypeChange(NoteTypeFilter.AUDIO)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // ARAÇLAR & ANALİTİK
                Text(
                    "Araçlar & Verimlilik",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text("İstatistikler & Analitik") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToStatistics()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    label = { Text("Not Şablonları") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenTemplates()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // ==========================================
                // KATEGORİLER BÖLÜMÜ
                // ==========================================
                val isCatAccordion = when (drawerCollapseMode) {
                    DrawerCollapseMode.ALWAYS_ACCORDION -> true
                    DrawerCollapseMode.AUTO_ACCORDION -> state.categories.size > 5
                    DrawerCollapseMode.INLINE_EXPAND, DrawerCollapseMode.EXPANDED_ALL -> false
                }
                val isCatInline = drawerCollapseMode == DrawerCollapseMode.INLINE_EXPAND && state.categories.size > 5

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = isCatAccordion) {
                            isCategoriesAccordionExpanded = !isCategoriesAccordionExpanded
                        }
                        .padding(start = 8.dp, end = 0.dp, top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Kategoriler", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (isCatAccordion && state.categories.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "${state.categories.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showAddCategoryDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Kategori Ekle")
                        }
                        IconButton(onClick = { showManageCategoriesDialog = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Kategorileri Düzenle")
                        }
                        if (isCatAccordion) {
                            val catRotation by animateFloatAsState(
                                targetValue = if (isCategoriesAccordionExpanded) 180f else 0f,
                                label = "cat_arrow_rotation"
                            )
                            IconButton(onClick = { isCategoriesAccordionExpanded = !isCategoriesAccordionExpanded }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isCategoriesAccordionExpanded) "Daralt" else "Genişlet",
                                    modifier = Modifier.rotate(catRotation)
                                )
                            }
                        }
                    }
                }

                val categoriesToShow = when {
                    isCatAccordion -> if (isCategoriesAccordionExpanded) state.categories else emptyList()
                    isCatInline -> if (isCategoriesInlineExpanded) state.categories else state.categories.take(5)
                    else -> state.categories
                }

                AnimatedVisibility(
                    visible = !isCatAccordion || isCategoriesAccordionExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        categoriesToShow.forEach { category ->
                            NavigationDrawerItem(
                                icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                                label = { Text(category.name) },
                                selected = state.viewMode == NotesViewMode.ALL &&
                                        state.filterType == NoteTypeFilter.ALL &&
                                        state.selectedTag == null && state.selectedCategory?.id == category.id,
                                onClick = {
                                    onCategorySelect(category)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }

                        if (isCatInline) {
                            TextButton(
                                onClick = { isCategoriesInlineExpanded = !isCategoriesInlineExpanded },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCategoriesInlineExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isCategoriesInlineExpanded) "Daha Az Göster" else "Daha Fazla Göster (+${state.categories.size - 5})",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // METİN ETİKETLERİ BÖLÜMÜ
                // ==========================================
                if (allHashtags.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val isTagAccordion = when (drawerCollapseMode) {
                        DrawerCollapseMode.ALWAYS_ACCORDION -> true
                        DrawerCollapseMode.AUTO_ACCORDION -> allHashtags.size > 5
                        DrawerCollapseMode.INLINE_EXPAND, DrawerCollapseMode.EXPANDED_ALL -> false
                    }
                    val isTagInline = drawerCollapseMode == DrawerCollapseMode.INLINE_EXPAND && allHashtags.size > 5

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = isTagAccordion) {
                                isTagsAccordionExpanded = !isTagsAccordionExpanded
                            }
                            .padding(start = 8.dp, end = 0.dp, top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Metin Etiketleri",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            if (isTagAccordion) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "${allHashtags.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        if (isTagAccordion) {
                            val tagRotation by animateFloatAsState(
                                targetValue = if (isTagsAccordionExpanded) 180f else 0f,
                                label = "tag_arrow_rotation"
                            )
                            IconButton(onClick = { isTagsAccordionExpanded = !isTagsAccordionExpanded }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isTagsAccordionExpanded) "Daralt" else "Genişlet",
                                    modifier = Modifier.rotate(tagRotation)
                                )
                            }
                        }
                    }

                    val tagsToShow = when {
                        isTagAccordion -> if (isTagsAccordionExpanded) allHashtags else emptyList()
                        isTagInline -> if (isTagsInlineExpanded) allHashtags else allHashtags.take(5)
                        else -> allHashtags
                    }

                    AnimatedVisibility(
                        visible = !isTagAccordion || isTagsAccordionExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            tagsToShow.forEach { tag ->
                                NavigationDrawerItem(
                                    icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                                    label = { Text("#$tag") },
                                    selected = state.viewMode == NotesViewMode.ALL &&
                                            state.filterType == NoteTypeFilter.ALL &&
                                            state.selectedCategory == null && state.selectedTag == tag,
                                    onClick = {
                                        onSelectTag(if (state.selectedTag == tag) null else tag)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                )
                            }

                            if (isTagInline) {
                                TextButton(
                                    onClick = { isTagsInlineExpanded = !isTagsInlineExpanded },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTagsInlineExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isTagsInlineExpanded) "Daha Az Göster" else "Daha Fazla Göster (+${allHashtags.size - 5})",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
                    Spacer(Modifier.navigationBarsPadding())
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isSelectionMode) {
                    // ==========================================
                    // ÇOKLU SEÇİM EYLEM ÇUBUĞU (ACTION BAR)
                    // ==========================================
                    TopAppBar(
                        title = {
                            Text(
                                text = "${state.selectedNoteIds.size} not seçildi",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onClearSelection) {
                                Icon(Icons.Default.Close, contentDescription = "Seçimi Kapat")
                            }
                        },
                        actions = {
                            // Tümünü Seç / Kaldır
                            IconButton(onClick = { onSelectAllNotes(filteredNotes) }) {
                                Icon(
                                    imageVector = if (state.selectedNoteIds.size == filteredNotes.size && filteredNotes.isNotEmpty())
                                        Icons.Default.Deselect else Icons.Default.SelectAll,
                                    contentDescription = "Tümünü Seç"
                                )
                            }
                            if (state.viewMode == NotesViewMode.ALL || state.viewMode == NotesViewMode.REMINDERS) {
                                // Sabitle / Kaldır
                                IconButton(onClick = onTogglePinForSelected) {
                                    Icon(Icons.Default.PushPin, contentDescription = "Sabitle")
                                }
                                // Rengi Değiştir (Toplu)
                                IconButton(onClick = { showBatchColorDialog = true }) {
                                    Icon(Icons.Default.Palette, contentDescription = "Renk Değiştir")
                                }
                            }
                            Box {
                                IconButton(onClick = { showSelectionActionsMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Seçili not işlemleri")
                                }
                                DropdownMenu(
                                    expanded = showSelectionActionsMenu,
                                    onDismissRequest = { showSelectionActionsMenu = false }
                                ) {
                                    if (state.viewMode == NotesViewMode.ALL && state.selectedNoteIds.size == 1) {
                                        DropdownMenuItem(
                                            text = { Text("Paylaş") },
                                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                            onClick = {
                                                selectedNotes.singleOrNull()?.let(onShareNote)
                                                showSelectionActionsMenu = false
                                                onClearSelection()
                                            }
                                        )
                                    }
                                    if (state.selectedNoteIds.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Seçili Notları Tek PDF Olarak Paylaş") },
                                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = {
                                                showSelectionActionsMenu = false
                                                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                                val tagRegex = Regex("#([a-zA-Z0-9_çğıöşüÇĞİÖŞÜ/-]+)")
                                                val exportItems = selectedNotes.map { n ->
                                                    val catName = state.categories.find { it.id == n.categoryId }?.name
                                                    val dateStr = sdf.format(Date(if (n.updatedAt > 0) n.updatedAt else n.timestamp))
                                                    val noteTags = tagRegex.findAll("${n.title} ${n.content}").map { it.groupValues[1] }.distinct().toList()
                                                    com.example.noteapp.util.BatchNoteExportItem(
                                                        title = n.title,
                                                        content = n.content,
                                                        categoryName = catName,
                                                        dateFormatted = dateStr,
                                                        tags = noteTags
                                                    )
                                                }
                                                try {
                                                    val batchPdf = com.example.noteapp.util.NoteExporter.exportMultipleToPdf(
                                                        context = context,
                                                        notes = exportItems,
                                                        documentTitle = "Toplu_Notlar_${selectedNotes.size}_Adet"
                                                    )
                                                    com.example.noteapp.util.NoteExporter.shareFile(context, batchPdf, "application/pdf", "Seçili Notları Paylaş")
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "PDF oluşturulamadı: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                onClearSelection()
                                            }
                                        )
                                    }
                                    if (state.viewMode == NotesViewMode.ALL) {
                                        DropdownMenuItem(
                                            text = { Text("Etiketle / kategori ata") },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                                            onClick = {
                                                showSelectionActionsMenu = false
                                                showBatchCategoryDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Arşivle") },
                                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                            onClick = {
                                                selectedNotes.forEach(onArchiveNote)
                                                showSelectionActionsMenu = false
                                                onClearSelection()
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(if (state.viewMode == NotesViewMode.TRASH) "Kalıcı sil" else "Sil") },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showSelectionActionsMenu = false
                                            showBatchDeleteConfirm = true
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                } else {
                    // ==========================================
                    // STANDART ÜST ÇUBUK
                    // ==========================================
                    TopAppBar(
                        title = {
                            Text(
                                text = when (state.viewMode) {
                                    NotesViewMode.ALL -> state.selectedCategory?.let { "#${it.name}" } ?: "Notlarım"
                                    NotesViewMode.REMINDERS -> "Hatırlatıcılar"
                                    NotesViewMode.ARCHIVE -> "Arşiv"
                                    NotesViewMode.TRASH -> "Çöp Kutusu"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menü")
                            }
                        },
                        actions = {
                            if (state.viewMode == NotesViewMode.TRASH) {
                                if (state.notes.isNotEmpty()) {
                                    TextButton(onClick = { showEmptyTrashConfirm = true }) {
                                        Text("Çöpü Boşalt", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            } else {
                                // Sıralama Butonu
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = "Sırala"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        NoteSortOrder.entries.forEach { order ->
                                            DropdownMenuItem(
                                                text = { Text(order.title) },
                                                onClick = {
                                                    onSortOrderChange(order)
                                                    showSortMenu = false
                                                },
                                                trailingIcon = if (state.sortOrder == order) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                                } else null
                                            )
                                        }
                                    }
                                }

                                // Not Listeleme Düzen Değiştirici Butonu
                                IconButton(onClick = {
                                    val nextMode = when (state.layoutMode) {
                                        NotesLayoutMode.STAGGERED_GRID -> NotesLayoutMode.LIST
                                        NotesLayoutMode.LIST -> NotesLayoutMode.COMPACT_LIST
                                        NotesLayoutMode.COMPACT_LIST -> NotesLayoutMode.STAGGERED_GRID
                                    }
                                    onLayoutModeChange(nextMode)
                                }) {
                                    Icon(
                                        imageVector = when (state.layoutMode) {
                                            NotesLayoutMode.STAGGERED_GRID -> Icons.Default.GridView
                                            NotesLayoutMode.LIST -> Icons.Default.ViewAgenda
                                            NotesLayoutMode.COMPACT_LIST -> Icons.Default.ViewHeadline
                                        },
                                        contentDescription = "Görünüm Düzenini Değiştir"
                                    )
                                }
                            }
                            if (quickActionLocation == QuickActionLocation.TOP_APP_BAR) {
                                IconButton(onClick = { showQuickSyncDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Masaüstü ile Eşitle",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = onSettingsClick) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Ayarlar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if ((state.viewMode == NotesViewMode.ALL || state.viewMode == NotesViewMode.REMINDERS) && !isSelectionMode) {
                    Column(
                        modifier = Modifier.navigationBarsPadding(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnimatedVisibility(
                            visible = isFabExpanded,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                        ) {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .heightIn(max = 380.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                QuickFabOption(
                                    label = "Resim Notu",
                                    icon = Icons.Default.Image,
                                    onClick = {
                                        isFabExpanded = false
                                        handleAddNote("image")
                                    }
                                )
                                QuickFabOption(
                                    label = "Çizim Notu",
                                    icon = Icons.Default.Draw,
                                    onClick = {
                                        isFabExpanded = false
                                        handleAddNote("draw")
                                    }
                                )
                                QuickFabOption(
                                    label = "Sesli Not",
                                    icon = Icons.Default.Mic,
                                    onClick = {
                                        isFabExpanded = false
                                        handleAddNote("voice")
                                    }
                                )
                                QuickFabOption(
                                    label = "Yapılacaklar Listesi",
                                    icon = Icons.Default.CheckBox,
                                    onClick = {
                                        isFabExpanded = false
                                        handleAddNote("checklist")
                                    }
                                )
                                QuickFabOption(
                                    label = "Şablon ile Not",
                                    icon = Icons.Default.AutoAwesome,
                                    onClick = {
                                        isFabExpanded = false
                                        onOpenTemplates()
                                    }
                                )
                                QuickFabOption(
                                    label = "Metin Notu",
                                    icon = Icons.Default.EditNote,
                                    onClick = {
                                        isFabExpanded = false
                                        handleAddNote(null)
                                    }
                                )
                            }
                        }

                        val rotation by animateFloatAsState(
                            targetValue = if (isFabExpanded) 135f else 0f,
                            animationSpec = tween(220),
                            label = "fab_rotation"
                        )
                        FloatingActionButton(
                            onClick = { isFabExpanded = !isFabExpanded },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = if (isFabExpanded) "Kapat" else "Yeni Not Ekle",
                                modifier = Modifier.rotate(rotation)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                // Arama Çubuğu
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Notlarda ara...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (state.searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Temizle")
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(searchBarShape.cornerDp.dp),
                    singleLine = true
                )

                if (state.viewMode == NotesViewMode.ALL) {
                    // Genel görünüm filtreleri & Dinamik Etiket Çipleri
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Seçili Kategori aktif filtresi varsa iptal butonuyla göster
                        if (state.selectedCategory != null) {
                            item(key = "active_category") {
                                FilterChip(
                                    selected = true,
                                    onClick = { onCategorySelect(null) },
                                    label = { Text(state.selectedCategory.name) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Label,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Kategoriyi temizle",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                        }

                        // Seçili Metin Etiketi aktif filtresi varsa iptal butonuyla göster
                        if (state.selectedTag != null) {
                            item(key = "active_tag") {
                                FilterChip(
                                    selected = true,
                                    onClick = { onSelectTag(null) },
                                    label = { Text("#${state.selectedTag}") },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Etiketi temizle",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                        }

                        // Tür Filtreleri
                        item(key = "type_all") {
                            FilterChip(
                                selected = state.filterType == NoteTypeFilter.ALL && state.selectedCategory == null && state.selectedTag == null,
                                onClick = {
                                    onFilterTypeChange(NoteTypeFilter.ALL)
                                    if (state.selectedCategory != null) onCategorySelect(null)
                                    if (state.selectedTag != null) onSelectTag(null)
                                },
                                label = { Text("Tümü") }
                            )
                        }
                        item(key = "type_pinned") {
                            FilterChip(
                                selected = state.filterType == NoteTypeFilter.PINNED,
                                onClick = { onFilterTypeChange(NoteTypeFilter.PINNED) },
                                label = { Text("Sabitlenenler") },
                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(15.dp)) }
                            )
                        }
                        item(key = "type_locked") {
                            FilterChip(
                                selected = state.filterType == NoteTypeFilter.LOCKED,
                                onClick = { onFilterTypeChange(NoteTypeFilter.LOCKED) },
                                label = { Text("Kilitliler") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp)) }
                            )
                        }
                        item(key = "type_media") {
                            FilterChip(
                                selected = state.filterType == NoteTypeFilter.MEDIA,
                                onClick = { onFilterTypeChange(NoteTypeFilter.MEDIA) },
                                label = { Text("Görseller") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp)) }
                            )
                        }
                        item(key = "type_audio") {
                            FilterChip(
                                selected = state.filterType == NoteTypeFilter.AUDIO,
                                onClick = { onFilterTypeChange(NoteTypeFilter.AUDIO) },
                                label = { Text("Sesler") },
                                leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(15.dp)) }
                            )
                        }
                        item(key = "type_reminders") {
                            FilterChip(
                                selected = state.filterType == NoteTypeFilter.REMINDERS,
                                onClick = { onFilterTypeChange(NoteTypeFilter.REMINDERS) },
                                label = { Text("Hatırlatıcılar") },
                                leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(15.dp)) }
                            )
                        }

                        // Popüler Hashtag'ler
                        allHashtags.filter { it != state.selectedTag }.take(15).forEach { tag ->
                            item(key = "tag_$tag") {
                                FilterChip(
                                    selected = false,
                                    onClick = { onSelectTag(tag) },
                                    label = { Text("#$tag") }
                                )
                            }
                        }
                    }
                }

                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = if (state.searchQuery.isNotEmpty()) Icons.Default.SearchOff else when (state.viewMode) {
                                    NotesViewMode.ALL -> Icons.AutoMirrored.Filled.Notes
                                    NotesViewMode.REMINDERS -> Icons.Default.NotificationsNone
                                    NotesViewMode.ARCHIVE -> Icons.Default.Archive
                                    NotesViewMode.TRASH -> Icons.Default.DeleteOutline
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                modifier = Modifier.size(38.dp)
                            )
                            Text(
                                text = if (state.searchQuery.isNotEmpty()) "Sonuç bulunamadı" else when (state.viewMode) {
                                    NotesViewMode.ALL -> "Burada henüz not yok"
                                    NotesViewMode.REMINDERS -> "Hatırlatıcı yok"
                                    NotesViewMode.ARCHIVE -> "Arşiv boş"
                                    NotesViewMode.TRASH -> "Çöp kutusu boş"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (state.searchQuery.isNotEmpty()) {
                                Text(
                                    "Başka bir arama sözcüğü deneyin.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (state.viewMode == NotesViewMode.ALL) {
                                Text(
                                    "Aklındaki fikri kaydetmeye başlayabilirsin.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilledTonalButton(onClick = { onAddNoteClick(null) }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("İlk notunu oluştur")
                                }
                            } else if (state.viewMode == NotesViewMode.ARCHIVE) {
                                Text(
                                    "Arşivlediğin notlar burada tutulur.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (state.viewMode == NotesViewMode.TRASH) {
                                Text(
                                    "Sildiğin notlar burada 7 gün boyunca kalır.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (state.viewMode == NotesViewMode.REMINDERS) {
                                Text(
                                    "Hatırlatıcı eklediğin notlar burada görünür.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // Seçilen Listeleme Düzenine Göre Görüntüleme
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        when (state.layoutMode) {
                            NotesLayoutMode.STAGGERED_GRID -> {
                                LazyVerticalStaggeredGrid(
                                    columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
                                    state = gridState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (reserveFabSpace) 88.dp else 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalItemSpacing = 12.dp
                                ) {
                                items(
                                        items = filteredNotes,
                                        key = { it.id },
                                        contentType = { "note_card" }
                                    ) { note ->
                                        val isSelected = state.selectedNoteIds.contains(note.id)
                                        NoteCard(
                                            modifier = Modifier.animateItem().then(noteEntranceModifier(note.id)),
                                            note = note,
                                            viewMode = state.viewMode,
                                            isSelected = isSelected,
                                            isSelectionMode = isSelectionMode,
                                            cornerStyle = cardCornerStyle,
                                            elevationStyle = cardElevationStyle,
                                            onClick = {
                                                if (isSelectionMode) onToggleNoteSelection(note.id)
                                                else handleOpenNote(note)
                                            },
                                            onLongClick = { onToggleNoteSelection(note.id) },
                                            onPinClick = { onPinNote(note) },
                                            onShareClick = { onShareNote(note) },
                                            onArchiveClick = { onArchiveNote(note) },
                                            onDeleteClick = { handleDeleteNote(note) },
                                            onRestoreClick = { onRestoreNote(note) },
                                            onDeletePermanentlyClick = { noteToDeletePermanently = note }
                                        )
                                    }
                                }
                            }

                            NotesLayoutMode.LIST -> {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (reserveFabSpace) 88.dp else 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        items = filteredNotes,
                                        key = { it.id },
                                        contentType = { "note_card" }
                                    ) { note ->
                                        val isSelected = state.selectedNoteIds.contains(note.id)
                                        NoteCard(
                                            modifier = Modifier.animateItem().then(noteEntranceModifier(note.id)),
                                            note = note,
                                            viewMode = state.viewMode,
                                            isSelected = isSelected,
                                            isSelectionMode = isSelectionMode,
                                            cornerStyle = cardCornerStyle,
                                            elevationStyle = cardElevationStyle,
                                            onClick = {
                                                if (isSelectionMode) onToggleNoteSelection(note.id)
                                                else handleOpenNote(note)
                                            },
                                            onLongClick = { onToggleNoteSelection(note.id) },
                                            onPinClick = { onPinNote(note) },
                                            onShareClick = { onShareNote(note) },
                                            onArchiveClick = { onArchiveNote(note) },
                                            onDeleteClick = { handleDeleteNote(note) },
                                            onRestoreClick = { onRestoreNote(note) },
                                            onDeletePermanentlyClick = { noteToDeletePermanently = note }
                                        )
                                    }
                                }
                            }

                            NotesLayoutMode.COMPACT_LIST -> {
                                LazyColumn(
                                    state = compactListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (reserveFabSpace) 88.dp else 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(
                                        items = filteredNotes,
                                        key = { it.id },
                                        contentType = { "compact_note" }
                                    ) { note ->
                                        val isSelected = state.selectedNoteIds.contains(note.id)
                                        val category = state.categories.find { it.id == note.categoryId }
                                        CompactNoteCard(
                                            modifier = Modifier.animateItem().then(noteEntranceModifier(note.id)),
                                            note = note,
                                            categoryName = category?.name,
                                            viewMode = state.viewMode,
                                            isSelected = isSelected,
                                            isSelectionMode = isSelectionMode,
                                            cornerStyle = cardCornerStyle,
                                            elevationStyle = cardElevationStyle,
                                            onClick = {
                                                if (isSelectionMode) onToggleNoteSelection(note.id)
                                                else handleOpenNote(note)
                                            },
                                            onLongClick = { onToggleNoteSelection(note.id) },
                                            onPinClick = { onPinNote(note) },
                                            onShareClick = { onShareNote(note) },
                                            onArchiveClick = { onArchiveNote(note) },
                                            onDeleteClick = { handleDeleteNote(note) },
                                            onRestoreClick = { onRestoreNote(note) },
                                            onDeletePermanentlyClick = { noteToDeletePermanently = note }
                                        )
                                    }
                                }
                            }
                        }

                        if (reserveFabSpace) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(104.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.00f to Color.Transparent,
                                                0.34f to MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
                                                0.72f to MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                                1.00f to MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(116.dp)
                                    .align(Alignment.BottomCenter)
                                    .drawBehind {
                                        val glowRadius = with(screenDensity) { 142.dp.toPx() }
                                        val glowCenter = androidx.compose.ui.geometry.Offset(
                                            x = size.width - with(screenDensity) { 30.dp.toPx() },
                                            y = size.height - with(screenDensity) { 42.dp.toPx() }
                                        )
                                        drawRect(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    homeGlowColor.copy(alpha = 0.13f),
                                                    homeGlowColor.copy(alpha = 0.045f),
                                                    Color.Transparent
                                                ),
                                                center = glowCenter,
                                                radius = glowRadius
                                            )
                                        )
                                    }
                            )
                        }

                    }
                }
            }

            if (isFabExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isFabExpanded = false }
                )
            }
        }
    }
}

    // ==========================================
    // DİYALOGLAR (Kategori Ekleme, Düzenleme, Toplu İşlemler)
    // ==========================================

    // 1. Yeni Kategori Ekleme Dialogu
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Yeni Kategori Oluştur") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { Text("Kategori adı (örn: İş, Kişisel)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        onAddCategory(newCategoryName)
                        newCategoryName = ""
                        showAddCategoryDialog = false
                    }
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

    // 2. Kategorileri Yönet (Düzenleme & Silme) Penceresi
    if (showManageCategoriesDialog) {
        AlertDialog(
            onDismissRequest = { showManageCategoriesDialog = false },
            title = { Text("Kategorileri Yönet") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    if (state.categories.isEmpty()) {
                        Text(
                            "Henüz eklenmiş bir kategori bulunmuyor.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.categories) { category ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Label,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(category.name, fontWeight = FontWeight.Medium)
                                        }

                                        IconButton(onClick = { categoryToDelete = category }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Sil",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageCategoriesDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    // 3. Kategori Silme Onay Dialogu
    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Kategori Silinsin mi?") },
            text = {
                Text("'${cat.name}' kategorisi silinecektir. Bu kategorideki notlarınız silinmez; otomatik olarak 'Kategorisiz' şeklinde korunur.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCategory(cat)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("İptal")
                }
            }
        )
    }

    // 4. Toplu Kategori Atama Dialogu
    if (showBatchCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showBatchCategoryDialog = false },
            title = { Text("Seçili Notlara Kategori Ata") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    onUpdateCategoryForSelected(null)
                                    showBatchCategoryDialog = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.LabelOff, contentDescription = null)
                                    Text("Kategorisiz")
                                }
                            }
                        }
                        items(state.categories) { cat ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    onUpdateCategoryForSelected(cat.id)
                                    showBatchCategoryDialog = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(cat.name)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchCategoryDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // 5. Toplu Renk Değiştirme Dialogu
    if (showBatchColorDialog) {
        AlertDialog(
            onDismissRequest = { showBatchColorDialog = false },
            icon = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Seçili Notların Rengini Değiştir") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "${state.selectedNoteIds.size} adet notun kart rengi güncellenecektir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(com.example.noteapp.presentation.detail.NoteColors) { color ->
                            val argb = color.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (color == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant else color)
                                    .border(
                                        width = 1.2.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onUpdateColorForSelected(argb)
                                        showBatchColorDialog = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (color == Color.Transparent) {
                                    Icon(
                                        imageVector = Icons.Default.FormatColorReset,
                                        contentDescription = "Sıfırla",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchColorDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // 6. Toplu Silme Onay Dialogu
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Seçili Notları Sil") },
            text = {
                Text(
                    if (state.viewMode == NotesViewMode.TRASH)
                        "${state.selectedNoteIds.size} adet not kalıcı olarak silinecek. Bu işlem geri alınamaz!"
                    else
                        "${state.selectedNoteIds.size} adet not çöp kutusuna taşınacaktır."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSelectedNotes()
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // 6. Çöp Kutusunu Boşaltma Onay Dialogu
    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Çöp Kutusunu Boşalt") },
            text = {
                val count = state.notes.size
                val countText = if (count > 0) " ($count not)" else ""
                Text("Çöp kutusundaki tüm notlar$countText kalıcı olarak silinecektir. Bu işlem geri alınamaz.\n\nÇöpü boşaltmak istediğinize emin misiniz?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEmptyTrash()
                        showEmptyTrashConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Çöpü Boşalt", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirm = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // 7. Tekil Notu Kalıcı Olarak Silme Onay Dialogu
    if (noteToDeletePermanently != null) {
        val note = noteToDeletePermanently!!
        AlertDialog(
            onDismissRequest = { noteToDeletePermanently = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Notu Kalıcı Olarak Sil") },
            text = {
                Text("'${note.title.ifBlank { "Not" }}' kalıcı olarak silinecektir. Bu işlem geri alınamaz.\n\nSilmek istediğinize emin misiniz?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePermanently(note)
                        noteToDeletePermanently = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Kalıcı Olarak Sil", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDeletePermanently = null }) {
                    Text("İptal")
                }
            }
        )
    }

    // 8. Hızlı Wi-Fi Senkronizasyonu Dialogu
    if (showQuickSyncDialog) {
        if (syncHost.isBlank()) {
            AlertDialog(
                onDismissRequest = { showQuickSyncDialog = false },
                icon = { Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Masaüstü ile Eşitle") },
                text = {
                    Text("Henüz bir masaüstü bilgisayar eşleştirilmedi. Ayarlar ekranına gidip tek tıkla ağdaki bilgisayarınızı bulabilir veya IP adresi girebilirsiniz.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showQuickSyncDialog = false
                        onSettingsClick()
                    }) {
                        Text("Ayarlara Git")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQuickSyncDialog = false }) {
                        Text("Kapat")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = {
                    if (!quickSyncState.isOperating) showQuickSyncDialog = false
                },
                icon = { Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Hızlı Wi-Fi Senkronizasyonu") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Hedef Bilgisayar: $syncHost",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Notlarınızı bilgisayarınızdaki 'Not Defterim Masaüstü' ile kablosuz eşitleyin:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (quickSyncState.isOperating) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Eşitleniyor...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onQuickSyncSend() },
                            enabled = !quickSyncState.isOperating
                        ) {
                            Text("Gönder 📤")
                        }
                        FilledTonalButton(
                            onClick = { onQuickSyncPull() },
                            enabled = !quickSyncState.isOperating
                        ) {
                            Text("Çek 📥")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showQuickSyncDialog = false },
                        enabled = !quickSyncState.isOperating
                    ) {
                        Text("Kapat")
                    }
                }
            )
        }
    }
}

@Composable
private fun NoteActionIcon(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(19.dp), tint = tint)
    }
}

private fun noteActionErrorColor(cardColor: Color): Color =
    if (cardColor.luminance() < 0.48f) Color(0xFFFF8A80) else Color(0xFFB3261E)

@Composable
private fun noteEntranceModifier(noteId: Long): Modifier {
    var hasEntered by rememberSaveable(noteId) { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "note_entry_$noteId"
    )
    val density = LocalDensity.current
    val entranceDistance = remember(density) { with(density) { 16.dp.toPx() } }
    LaunchedEffect(noteId) { hasEntered = true }
    return if (progress >= 0.99f) Modifier else Modifier.graphicsLayer {
        alpha = progress
        translationY = entranceDistance * (1f - progress)
    }
}

/**
 * Kart Görünümü (Staggered Grid ve Detaylı Liste için)
 * getNoteColorSpec ile karanlık/aydınlık temada zıt (negatif) ve yüksek kontrastlı renkleri kullanır.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    viewMode: NotesViewMode,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    cornerStyle: CardCornerStyle = CardCornerStyle.ROUNDED,
    elevationStyle: CardElevationStyle = CardElevationStyle.BORDERED,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPinClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDeletePermanentlyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultSurface = MaterialTheme.colorScheme.surfaceVariant
    val colorSpec = remember(note.color, defaultSurface) {
        getNoteColorSpec(note.color, defaultSurface)
    }

    val firstImage = remember(note.attachments) {
        note.attachments.firstOrNull { !it.endsWith(".mp4") && !it.endsWith(".m4a") }
    }

    val displayContent = remember(note.content) {
        parseMarkdownCardPreview(note.content)
    }

    val checklistStats = remember(note.content) {
        com.example.noteapp.domain.util.ChecklistParser.parse(note.content)
    }

    val cardShape = remember(cornerStyle) { RoundedCornerShape(cornerStyle.radiusDp.dp) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val cardBorder = remember(isSelected, elevationStyle, primaryColor, outlineVariantColor) {
        when {
            isSelected -> BorderStroke(2.5.dp, primaryColor)
            elevationStyle == CardElevationStyle.BORDERED -> BorderStroke(1.2.dp, outlineVariantColor.copy(alpha = 0.55f))
            elevationStyle == CardElevationStyle.GLASS -> BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
            else -> null
        }
    }
    val cardElevation = when (elevationStyle) {
        CardElevationStyle.ELEVATED -> CardDefaults.cardElevation(defaultElevation = 3.dp)
        else -> CardDefaults.cardElevation(defaultElevation = 0.dp)
    }
    val cardBg = remember(elevationStyle, colorSpec.backgroundColor) {
        if (elevationStyle == CardElevationStyle.GLASS) colorSpec.backgroundColor.copy(alpha = 0.65f)
        else colorSpec.backgroundColor
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (cardBorder != null) Modifier.border(cardBorder, cardShape)
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = cardShape,
        elevation = cardElevation,
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (!note.backgroundImage.isNullOrEmpty()) {
                val context = LocalContext.current
                val bgRequest = remember(note.backgroundImage) {
                    coil.request.ImageRequest.Builder(context)
                        .data(File(note.backgroundImage))
                        .size(coil.size.Size(360, 360))
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = bgRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.35f)
                )
            }
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                // Görsel eki (Varsa) - Downsampled thumbnail
                firstImage?.let { imgPath ->
                    val context = LocalContext.current
                    val imgRequest = remember(imgPath) {
                        coil.request.ImageRequest.Builder(context)
                            .data(File(imgPath))
                            .size(coil.size.Size(360, 240))
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = imgRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Başlık ve Sabitleme / Seçim
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
                            color = colorSpec.contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // Başlık olmayınca sabitleme düğmesi satırın başına düşmesin.
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (viewMode == NotesViewMode.ALL || viewMode == NotesViewMode.REMINDERS) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    colorSpec.contentColor.copy(
                                        alpha = if (note.isPinned) 0.14f else 0.05f
                                    )
                                )
                                .clickable(onClick = onPinClick),
                            contentAlignment = Alignment.Center
                        ) {
                            if (note.isPinned) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Sabitlemeyi kaldır",
                                    tint = colorSpec.contentColor.copy(alpha = 0.95f),
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_push_pin_off),
                                    contentDescription = "Sabitle",
                                    tint = colorSpec.contentColor.copy(alpha = 0.45f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Kilitli not veya içerik
                if (note.isLocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Kilitli",
                            tint = colorSpec.contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Kilitli Not",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorSpec.contentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (displayContent.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = displayContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorSpec.contentColor,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (checklistStats != null && !note.isLocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorSpec.contentColor.copy(alpha = 0.07f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (checklistStats.isAllCompleted) Icons.Default.CheckCircle else Icons.Default.Checklist,
                                    contentDescription = null,
                                    tint = if (checklistStats.isAllCompleted) MaterialTheme.colorScheme.primary else colorSpec.iconTint,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${checklistStats.completedCount}/${checklistStats.totalCount} yapıldı",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorSpec.contentColor.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "%${checklistStats.percent}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (checklistStats.isAllCompleted) MaterialTheme.colorScheme.primary else colorSpec.contentColor.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { checklistStats.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (checklistStats.isAllCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            trackColor = colorSpec.contentColor.copy(alpha = 0.12f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Alt Çubuk: Göstergeler (sol) ve Hızlı İşlemler (sağ)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (note.reminderTime != null && note.reminderTime > 0) {
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = "Hatırlatıcı",
                                modifier = Modifier.size(14.dp),
                                tint = colorSpec.iconTint
                            )
                        }
                        if (note.attachments.any { it.endsWith(".mp4") || it.endsWith(".m4a") }) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Ses Kaydı",
                                modifier = Modifier.size(14.dp),
                                tint = colorSpec.iconTint
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (viewMode) {
                            NotesViewMode.ALL, NotesViewMode.REMINDERS -> Unit
                            NotesViewMode.ARCHIVE -> {
                                NoteActionIcon(Icons.Default.Unarchive, "Arşivden çıkar", colorSpec.iconTint, onArchiveClick)
                                NoteActionIcon(Icons.Default.Delete, "Çöp kutusuna taşı", noteActionErrorColor(colorSpec.backgroundColor), onDeleteClick)
                            }
                            NotesViewMode.TRASH -> {
                                NoteActionIcon(Icons.Default.RestoreFromTrash, "Notu geri yükle", MaterialTheme.colorScheme.primary, onRestoreClick)
                                NoteActionIcon(Icons.Default.DeleteForever, "Kalıcı olarak sil", noteActionErrorColor(colorSpec.backgroundColor), onDeletePermanentlyClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kompakt Liste Kartı (Hızlı ve yoğun listeleme modu için)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactNoteCard(
    note: Note,
    categoryName: String? = null,
    viewMode: NotesViewMode,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    cornerStyle: CardCornerStyle = CardCornerStyle.ROUNDED,
    elevationStyle: CardElevationStyle = CardElevationStyle.BORDERED,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPinClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDeletePermanentlyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultSurface = MaterialTheme.colorScheme.surfaceVariant
    val colorSpec = remember(note.color, defaultSurface) {
        getNoteColorSpec(note.color, defaultSurface)
    }
    val dateStr = remember(note.timestamp) {
        SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(note.timestamp))
    }
    val checklistStats = remember(note.content) {
        com.example.noteapp.domain.util.ChecklistParser.parse(note.content)
    }

    val compactShape = remember(cornerStyle) {
        val compactRadius = (cornerStyle.radiusDp * 0.75f).coerceAtLeast(4f).dp
        RoundedCornerShape(compactRadius)
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val cardBorder = remember(isSelected, elevationStyle, primaryColor, outlineVariantColor) {
        when {
            isSelected -> BorderStroke(2.dp, primaryColor)
            elevationStyle == CardElevationStyle.BORDERED -> BorderStroke(1.dp, outlineVariantColor.copy(alpha = 0.5f))
            elevationStyle == CardElevationStyle.GLASS -> BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
            else -> null
        }
    }

    Surface(
        shape = compactShape,
        color = if (elevationStyle == CardElevationStyle.GLASS) colorSpec.backgroundColor.copy(alpha = 0.7f) else colorSpec.backgroundColor,
        shadowElevation = if (elevationStyle == CardElevationStyle.ELEVATED) 2.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (cardBorder != null) Modifier.border(cardBorder, compactShape)
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // 1. SATIR: Başlık ve Kısa Özeti (+ Renk/Kilit/Seçim ve Tarih)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                } else if (note.color != 0) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(note.color))
                    )
                    Spacer(Modifier.width(8.dp))
                }

                if (note.isLocked) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colorSpec.contentColor
                    )
                    Spacer(Modifier.width(6.dp))
                }

                // Başlık ve Kısa Özeti
                val titleSnippet = remember(note.title, note.content, note.isLocked) {
                    val preview = if (!note.isLocked && note.content.isNotBlank()) {
                        parseMarkdownCardPreview(note.content, lineLimit = 1)
                    } else androidx.compose.ui.text.AnnotatedString("")
                    Pair(note.title.ifBlank { "Başlıksız Not" }, preview)
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colorSpec.contentColor)) {
                            append(titleSnippet.first)
                        }
                        if (titleSnippet.second.text.isNotEmpty()) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = colorSpec.secondaryColor)) {
                                append("  —  ")
                                append(titleSnippet.second)
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorSpec.secondaryColor
                )
            }

            Spacer(Modifier.height(4.dp))

            // 2. SATIR: Göstergeler ve Hızlı Aksiyonlar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (!categoryName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorSpec.contentColor.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = "#$categoryName",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorSpec.contentColor.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (checklistStats != null && !note.isLocked) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (checklistStats.isAllCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else colorSpec.contentColor.copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = if (checklistStats.isAllCompleted) Icons.Default.CheckCircle else Icons.Default.Checklist,
                                    contentDescription = null,
                                    tint = if (checklistStats.isAllCompleted) MaterialTheme.colorScheme.primary else colorSpec.iconTint,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "${checklistStats.completedCount}/${checklistStats.totalCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorSpec.contentColor.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    if (note.reminderTime != null && note.reminderTime > 0) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Hatırlatıcı",
                            modifier = Modifier.size(13.dp),
                            tint = colorSpec.secondaryColor
                        )
                    }
                    if (note.attachments.any { !it.endsWith(".mp4") && !it.endsWith(".m4a") }) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "Görsel",
                            modifier = Modifier.size(13.dp),
                            tint = colorSpec.secondaryColor
                        )
                    }
                    if (note.attachments.any { it.endsWith(".mp4") || it.endsWith(".m4a") }) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Ses",
                            modifier = Modifier.size(13.dp),
                            tint = colorSpec.secondaryColor
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Sağ taraf aksiyon butonları (Lightweight Box + Icon)
                if (!isSelectionMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (viewMode) {
                            NotesViewMode.ALL, NotesViewMode.REMINDERS -> {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(
                                            colorSpec.contentColor.copy(
                                                alpha = if (note.isPinned) 0.14f else 0.05f
                                            )
                                        )
                                        .clickable(onClick = onPinClick),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (note.isPinned) {
                                        Icon(
                                            imageVector = Icons.Filled.PushPin,
                                            contentDescription = "Sabitlemeyi kaldır",
                                            tint = colorSpec.contentColor.copy(alpha = 0.95f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_push_pin_off),
                                            contentDescription = "Sabitle",
                                            tint = colorSpec.contentColor.copy(alpha = 0.45f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                            NotesViewMode.ARCHIVE -> {
                                NoteActionIcon(Icons.Default.Unarchive, "Arşivden çıkar", colorSpec.iconTint, onArchiveClick)
                                NoteActionIcon(Icons.Default.Delete, "Çöp kutusuna taşı", MaterialTheme.colorScheme.error, onDeleteClick)
                            }
                            NotesViewMode.TRASH -> {
                                NoteActionIcon(Icons.Default.RestoreFromTrash, "Notu geri yükle", MaterialTheme.colorScheme.primary, onRestoreClick)
                                NoteActionIcon(Icons.Default.DeleteForever, "Kalıcı olarak sil", MaterialTheme.colorScheme.error, onDeletePermanentlyClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickFabOption(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = CircleShape
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        }
    }
}
