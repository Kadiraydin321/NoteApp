package com.example.noteapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownPreview(
    content: String,
    onContentChange: (String) -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val lines = content.lines()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEachIndexed { index, line ->
            val trimmed = line.trimStart()

            when {
                // 1. Checkbox: - [ ] veya - [x]
                trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") -> {
                    val isChecked = trimmed.startsWith("- [x] ")
                    val itemText = if (isChecked) trimmed.removePrefix("- [x] ") else trimmed.removePrefix("- [ ] ")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                val prefix = if (checked) "- [x] " else "- [ ] "
                                val updatedLines = lines.toMutableList()
                                updatedLines[index] = prefix + itemText
                                onContentChange(updatedLines.joinToString("\n"))
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = parseInlineMarkdown(itemText),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (isChecked) contentColor.copy(alpha = 0.5f) else contentColor
                            )
                        )
                    }
                }

                // 2. Başlık 1: # Başlık
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("# ")),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        ),
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }

                // 3. Başlık 2: ## Başlık
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("## ")),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }

                // 4. Başlık 3: ### Başlık
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("### ")),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        ),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }

                // 5. Alıntı: > Alıntı metni
                trimmed.startsWith("> ") -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(trimmed.removePrefix("> ")),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontStyle = FontStyle.Italic,
                                color = contentColor.copy(alpha = 0.85f)
                            )
                        )
                    }
                }

                // 6. Madde İşareti: • veya -
                trimmed.startsWith("• ") || trimmed.startsWith("- ") -> {
                    val bulletText = if (trimmed.startsWith("• ")) trimmed.removePrefix("• ") else trimmed.removePrefix("- ")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("• ", style = MaterialTheme.typography.bodyLarge.copy(color = contentColor, fontWeight = FontWeight.Bold))
                        Text(
                            text = parseInlineMarkdown(bulletText),
                            style = MaterialTheme.typography.bodyLarge.copy(color = contentColor)
                        )
                    }
                }

                // 7. Normal Paragraf
                else -> {
                    if (trimmed.isEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    } else {
                        Text(
                            text = parseInlineMarkdown(trimmed),
                            style = MaterialTheme.typography.bodyLarge.copy(color = contentColor)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Satır içi Markdown formatlarını (**kalın**, *italik*, ~~üstü çizili~~, ==vurgu==, `kod`) AnnotatedString'e dönüştürür.
 */
fun parseInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        append(text)

        // **Kalın**
        Regex("\\*\\*(.*?)\\*\\*").findAll(text).forEach { match ->
            val inner = match.groupValues[1]
            val start = match.range.first
            val end = match.range.last + 1
            // Sadece iç metni göster veya stil uygula
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
        }

        // *İtalik*
        Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)").findAll(text).forEach { match ->
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
        }

        // ~~Üstü Çizili~~
        Regex("~~(.*?)~~").findAll(text).forEach { match ->
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), match.range.first, match.range.last + 1)
        }

        // ==Vurgu==
        Regex("==(.*?)==").findAll(text).forEach { match ->
            addStyle(SpanStyle(background = Color(0xFFFFF59D), color = Color(0xFF212121)), match.range.first, match.range.last + 1)
        }

        // `Kod`
        Regex("`([^`]+)`").findAll(text).forEach { match ->
            addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x1F000000)), match.range.first, match.range.last + 1)
        }
    }
}
