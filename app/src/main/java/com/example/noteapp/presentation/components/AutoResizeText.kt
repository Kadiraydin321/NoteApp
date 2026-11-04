package com.example.noteapp.presentation.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Taşma durumunda alta geçip tasarımı bozmak yerine, metin boyutunu asgari
 * okunabilir sınıra (minFontSize) kadar kademeli olarak küçülten duyarlı Text bileşeni.
 *
 * Sığmama durumunda [overflow] (varsayılan [TextOverflow.Ellipsis]) ile kesilir.
 */
@Composable
fun AutoResizeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
    maxLines: Int = 1,
    minFontSize: TextUnit = 9.sp,
    targetFontSize: TextUnit = TextUnit.Unspecified,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    AutoResizeText(
        annotatedText = AnnotatedString(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minFontSize = minFontSize,
        targetFontSize = targetFontSize,
        onTextLayout = onTextLayout,
        style = style
    )
}

/**
 * [AnnotatedString] destekleyen aşırı yüklenmiş AutoResizeText bileşeni.
 */
@Composable
fun AutoResizeText(
    annotatedText: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
    maxLines: Int = 1,
    minFontSize: TextUnit = 9.sp,
    targetFontSize: TextUnit = TextUnit.Unspecified,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    val mergedStyle = style.copy(
        color = if (color != Color.Unspecified) color else style.color,
        fontSize = if (fontSize.isSpecified) fontSize else style.fontSize,
        fontWeight = fontWeight ?: style.fontWeight,
        textAlign = textAlign ?: style.textAlign,
        lineHeight = if (lineHeight.isSpecified) lineHeight else style.lineHeight,
        fontFamily = fontFamily ?: style.fontFamily,
        textDecoration = textDecoration ?: style.textDecoration,
        fontStyle = fontStyle ?: style.fontStyle,
        letterSpacing = if (letterSpacing.isSpecified) letterSpacing else style.letterSpacing
    )

    val actualTargetFontSize = when {
        targetFontSize.isSpecified -> targetFontSize
        fontSize.isSpecified -> fontSize
        mergedStyle.fontSize.isSpecified -> mergedStyle.fontSize
        else -> 14.sp
    }

    val actualMinFontSize = if (minFontSize.isSpecified) minFontSize else 9.sp

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val maxConstraintWidth = constraints.maxWidth
        val maxConstraintHeight = constraints.maxHeight

        val optimalFontSize = remember(
            annotatedText,
            maxConstraintWidth,
            maxConstraintHeight,
            actualTargetFontSize,
            actualMinFontSize,
            maxLines,
            mergedStyle,
            density
        ) {
            if (maxConstraintWidth == Constraints.Infinity || maxConstraintWidth <= 0 ||
                actualTargetFontSize.value <= actualMinFontSize.value
            ) {
                actualTargetFontSize
            } else {
                var low = actualMinFontSize.value
                var high = actualTargetFontSize.value
                var best = actualMinFontSize.value

                // 0.5sp hassasiyetle binary search ölçümü
                for (iter in 0 until 8) {
                    if (high - low < 0.4f) break
                    val mid = (low + high) / 2f
                    val testStyle = mergedStyle.copy(
                        fontSize = mid.sp,
                        lineHeight = if (mergedStyle.lineHeight.isSpecified) (mid * 1.3f).sp else TextUnit.Unspecified
                    )

                    val measureResult = textMeasurer.measure(
                        text = annotatedText,
                        style = testStyle,
                        constraints = Constraints(maxWidth = maxConstraintWidth),
                        maxLines = maxLines
                    )

                    val fits = !measureResult.hasVisualOverflow &&
                            measureResult.lineCount <= maxLines &&
                            (maxConstraintHeight == Constraints.Infinity || measureResult.size.height <= maxConstraintHeight)

                    if (fits) {
                        best = mid
                        low = mid
                    } else {
                        high = mid
                    }
                }
                best.sp
            }
        }

        Text(
            text = annotatedText,
            modifier = Modifier,
            style = mergedStyle.copy(
                fontSize = optimalFontSize,
                lineHeight = if (mergedStyle.lineHeight.isSpecified) (optimalFontSize.value * 1.3f).sp else TextUnit.Unspecified
            ),
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            onTextLayout = onTextLayout ?: {}
        )
    }
}
