package com.example.noteapp.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteapp.data.settings.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignStudioScreen(
    settings: AppSettings,
    onBackClick: () -> Unit,
    onApplyPreset: (DesignPreset) -> Unit,
    onCardCornerStyleChange: (CardCornerStyle) -> Unit,
    onCardElevationStyleChange: (CardElevationStyle) -> Unit,
    onQuickActionLocationChange: (QuickActionLocation) -> Unit,
    onSearchBarShapeChange: (SearchBarShape) -> Unit,
    onColorPaletteChange: (AppColorPalette) -> Unit,
    onLayoutModeChange: (NotesLayoutMode) -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tasarım Stüdyosu", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "PRO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ==========================================
            // CANLI MİNİ ÖNİZLEME PENCERESİ (MOCKUP)
            // ==========================================
            LiveScreenMockupCard(settings = settings)

            // ==========================================
            // HAZIR ŞABLONLAR BÖLÜMÜ (TEK DOKUNUŞLA UYGULA)
            // ==========================================
            Text(
                text = "✨ Hazır Tasarım Şablonları",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Tüm arayüzü tek dokunuşla uyumlu ve profesyonel bir tarza dönüştürün:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DesignPreset.values().filter { it != DesignPreset.CUSTOM }.forEach { preset ->
                    val isSelected = settings.designPreset == preset
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onApplyPreset(preset) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = preset.iconEmoji,
                                fontSize = 28.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = preset.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Seçili",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp).padding(2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ==========================================
            // BUTON KONUMU (GÖRSEL MİNİ ÇİZİMLERLE)
            // ==========================================
            Text(
                text = "📍 Buton Konumları (Ayarlar & Hızlı Senkronizasyon)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Ayarlar çarkı ve Wi-Fi senkronizasyon simgelerinin nerede duracağını seçin:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Seçenek 1: Sol Menü Başlığı
                LocationOptionCard(
                    title = "Sol Menü Başlığı",
                    subtitle = "Not Defterim yanında",
                    isSelected = settings.quickActionLocation == QuickActionLocation.DRAWER_HEADER,
                    onClick = { onQuickActionLocationChange(QuickActionLocation.DRAWER_HEADER) },
                    modifier = Modifier.weight(1f)
                ) {
                    // Mini Çizim: Sol Menü Çekmecesi ve Başlığı
                    MiniDrawerIllustration(hasActionsInHeader = true)
                }

                // Seçenek 2: Ana Ekran Üst Çubuğu
                LocationOptionCard(
                    title = "Ana Ekran Üstü",
                    subtitle = "Sağ üst çubukta",
                    isSelected = settings.quickActionLocation == QuickActionLocation.TOP_APP_BAR,
                    onClick = { onQuickActionLocationChange(QuickActionLocation.TOP_APP_BAR) },
                    modifier = Modifier.weight(1f)
                ) {
                    // Mini Çizim: Ana Ekran TopAppBar
                    MiniTopBarIllustration(hasActionsInTopBar = true)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ==========================================
            // KART KÖŞE STİLİ (GÖRSEL ÖNİZLEMELİ)
            // ==========================================
            Text(
                text = "📐 Not Kartı Köşe Stili",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardCornerStyle.values().forEach { corner ->
                    val isSelected = settings.cardCornerStyle == corner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCardCornerStyleChange(corner) }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Mini Kart Çizimi
                            Box(
                                modifier = Modifier
                                    .size(width = 46.dp, height = 34.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 0.85f else 0.4f),
                                        RoundedCornerShape(corner.radiusDp.dp / 2)
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        RoundedCornerShape(corner.radiusDp.dp / 2)
                                    )
                            )
                            Text(
                                text = corner.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = "${corner.radiusDp} dp",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ==========================================
            // KART DERİNLİĞİ & ÇERÇEVE STİLİ
            // ==========================================
            Text(
                text = "🎨 Kart Derinlik & Efekt Stili",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardElevationStyle.values().forEach { elevation ->
                    val isSelected = settings.cardElevationStyle == elevation
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCardElevationStyleChange(elevation) }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Mini Efekt Çizimi
                            when (elevation) {
                                CardElevationStyle.BORDERED -> {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 46.dp, height = 34.dp)
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    )
                                }
                                CardElevationStyle.ELEVATED -> {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 46.dp, height = 34.dp)
                                            .shadow(4.dp, RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                                    )
                                }
                                CardElevationStyle.GLASS -> {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 46.dp, height = 34.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                            Text(
                                text = elevation.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ==========================================
            // ARAMA ÇUBUĞU ŞEKLİ
            // ==========================================
            Text(
                text = "🔍 Arama Çubuğu Şekli",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SearchBarShape.values().forEach { shape ->
                    val isSelected = settings.searchBarShape == shape
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSearchBarShapeChange(shape) }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(shape.cornerDp.dp / 2))
                                    .border(1.2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(shape.cornerDp.dp / 2)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(40.dp)
                                            .height(4.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                            Text(
                                text = shape.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ==========================================
            // RENK & ATMOSFER PALETİ
            // ==========================================
            Text(
                text = "🎨 Renk & Atmosfer Paleti",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppColorPalette.values().forEach { palette ->
                    val isSelected = settings.colorPalette == palette
                    val swatchColors = when (palette) {
                        AppColorPalette.WARM_AMBER -> listOf(Color(0xFFFFB74D), Color(0xFFFFAB91), Color(0xFF2E241E))
                        AppColorPalette.MIDNIGHT_ROSE -> listOf(Color(0xFFFFB0CD), Color(0xFFE4BDC8), Color(0xFF2E2026))
                        AppColorPalette.FOREST_EMERALD -> listOf(Color(0xFF81D89D), Color(0xFFA2CED9), Color(0xFF202A22))
                        AppColorPalette.DEEP_OCEAN -> listOf(Color(0xFF97CCF8), Color(0xFFB9C8DA), Color(0xFF202731))
                        AppColorPalette.SLATE_MINIMAL -> listOf(Color(0xFFD0D3D9), Color(0xFFC4C7CC), Color(0xFF26292E))
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onColorPaletteChange(palette) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                swatchColors.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(color, CircleShape)
                                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = palette.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Canlı Ekran Mockup Kartı: Kullanıcı bir ayar değiştirdiğinde gerçek zamanlı olarak
 * telefon ekranında nasıl göründüğünü minyatür ölçekte animasyonlu olarak simüle eder.
 */
@Composable
private fun LiveScreenMockupCard(settings: AppSettings) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "CANLI ARAYÜZ SİMÜLASYONU",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = settings.designPreset.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Minyatür Telefon Ekranı
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Minyatür TopAppBar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Notlarım", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (settings.quickActionLocation == QuickActionLocation.TOP_APP_BAR) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(13.dp))
                            }
                        }

                        // 2. Minyatür Arama Çubuğu
                        val searchRadius = settings.searchBarShape.cornerDp.dp / 2
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(searchRadius))
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .width(70.dp)
                                        .height(3.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                                )
                            }
                        }

                        // 3. Minyatür Kartlar (Grid Şeklinde)
                        val cardRadius = settings.cardCornerStyle.radiusDp.dp / 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Kart 1
                            MiniNoteCardPreview(
                                title = "Haftalık Plan",
                                body = "Alışveriş, toplantı...",
                                cornerRadius = cardRadius,
                                elevationStyle = settings.cardElevationStyle,
                                modifier = Modifier.weight(1f)
                            )
                            // Kart 2
                            MiniNoteCardPreview(
                                title = "Yeni Fikirler 💡",
                                body = "Mobil mimari, UI...",
                                cornerRadius = cardRadius,
                                elevationStyle = settings.cardElevationStyle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 4. Minyatür FAB
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniNoteCardPreview(
    title: String,
    body: String,
    cornerRadius: androidx.compose.ui.unit.Dp,
    elevationStyle: CardElevationStyle,
    modifier: Modifier = Modifier
) {
    val cardBorder = when (elevationStyle) {
        CardElevationStyle.BORDERED -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        CardElevationStyle.GLASS -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        CardElevationStyle.ELEVATED -> null
    }
    val cardBg = when (elevationStyle) {
        CardElevationStyle.GLASS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        CardElevationStyle.ELEVATED -> MaterialTheme.colorScheme.surfaceContainerHigh
        CardElevationStyle.BORDERED -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .height(82.dp)
            .then(
                if (elevationStyle == CardElevationStyle.ELEVATED) Modifier.shadow(3.dp, RoundedCornerShape(cornerRadius))
                else Modifier
            )
            .background(cardBg, RoundedCornerShape(cornerRadius))
            .then(if (cardBorder != null) Modifier.border(cardBorder, RoundedCornerShape(cornerRadius)) else Modifier)
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
            )
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            ) {
                Text("#not", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
            }
        }
    }
}

@Composable
private fun LocationOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            illustration()
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MiniDrawerIllustration(hasActionsInHeader: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Not Defterim", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                if (hasActionsInHeader) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, modifier = Modifier.size(9.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tüm Notlar", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun MiniTopBarIllustration(hasActionsInTopBar: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Notlarım", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                if (hasActionsInTopBar) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            )
        }
    }
}
