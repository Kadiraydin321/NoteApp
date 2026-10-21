package com.example.noteapp.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Not düzenleme ekranında '#' yazıldığında klavye üstünde beliren akıllı etiket tamamlama çubuğu.
 */
@Composable
fun TagAutocompleteBar(
    query: String,
    suggestions: List<String>,
    onSelectTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Label,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (query.isNotBlank()) "Etiket Tamamla: #$query" else "Kullanılan Etiketler",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Eğer sorgu varsa ve listede tam eşleşen yoksa "Yeni Etiket Olarak Tamamla" çipi
                if (query.isNotBlank() && suggestions.none { it.equals(query, ignoreCase = true) }) {
                    item(key = "create_new_$query") {
                        SuggestionChip(
                            onClick = { onSelectTag(query) },
                            label = { Text("+#$query", fontWeight = FontWeight.Bold) },
                            icon = {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                items(suggestions, key = { it }) { tag ->
                    SuggestionChip(
                        onClick = { onSelectTag(tag) },
                        label = { Text("#$tag", fontWeight = FontWeight.SemiBold) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}
