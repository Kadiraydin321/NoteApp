package com.example.noteapp.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.noteapp.data.settings.AppSettings
import com.example.noteapp.data.settings.WidgetFilterMode
import com.example.noteapp.presentation.detail.NoteColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onWidgetFilterChange: (WidgetFilterMode) -> Unit,
    onWidgetShowLockedChange: (Boolean) -> Unit,
    onDefaultColorChange: (Int) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onRefreshWidget: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // --- BÖLÜM 1: ANA EKRAN WİDGET'I ---
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Ana Ekran Widget Ayarları",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    // Filtreleme Seçimi
                    Text("Widget'ta Görüntülenecek Notlar:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = settings.widgetFilterMode == WidgetFilterMode.ALL,
                            onClick = { onWidgetFilterChange(WidgetFilterMode.ALL) },
                            label = { Text("Tüm Notlar") },
                            leadingIcon = if (settings.widgetFilterMode == WidgetFilterMode.ALL) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = settings.widgetFilterMode == WidgetFilterMode.FAVORITES,
                            onClick = { onWidgetFilterChange(WidgetFilterMode.FAVORITES) },
                            label = { Text("Sadece Favoriler ★") },
                            leadingIcon = if (settings.widgetFilterMode == WidgetFilterMode.FAVORITES) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Kilitli Notlar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kilitli Notları Göster", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "Kapalıyken şifrelenmiş notlar widget listesinde hiç görünmez",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.widgetShowLockedNotes,
                            onCheckedChange = onWidgetShowLockedChange
                        )
                    }

                    // Widget'ı Şimdi Güncelle Butonu
                    FilledTonalButton(
                        onClick = onRefreshWidget,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Widget Verilerini Şimdi Senkronize Et")
                    }
                }
            }

            // --- BÖLÜM 2: GÖRÜNÜM & TEMA ---
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Görünüm ve Tema",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dinamik Renkler (Material You)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "Android 12+ sistem duvar kağıdı renklerine göre arayüzü şekillendirir",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }

                    // Varsayılan Not Rengi
                    Text("Yeni Notlar İçin Varsayılan Renk:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(NoteColors) { color ->
                            val argb = color.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (color == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant else color)
                                    .border(
                                        width = if (settings.defaultNoteColor == argb) 3.dp else 1.dp,
                                        color = if (settings.defaultNoteColor == argb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { onDefaultColorChange(argb) }
                            )
                        }
                    }
                }
            }

            // --- BÖLÜM 3: GÜVENLİK BİLGİSİ ---
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Güvenlik & Gizlilik",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    Text(
                        "Notlarınızı biyometrik olarak (parmak izi / cihaz PIN'i) kilitlemek için, herhangi bir notun düzenleme ekranında üst kısımdaki 'Kilit' simgesine dokunmanız yeterlidir. Kilitli notlar kimlik doğrulanmadan açılamaz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- BÖLÜM 4: HAKKINDA ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Modern Note App v1.0.0",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tamamen Çevrimdışı ve Güvenli Yerel Depolama",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
