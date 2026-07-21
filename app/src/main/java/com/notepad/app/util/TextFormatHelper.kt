package com.notepad.app.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Formattazione markdown leggera.
 * I marker restano nel testo salvato ma vengono nascosti in visualizzazione.
 */
object TextFormatHelper {

    private val boldRegex = Regex("\\*\\*(.+?)\\*\\*", RegexOption.DOT_MATCHES_ALL)
    private val italicRegex = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", RegexOption.DOT_MATCHES_ALL)
    private val strikeRegex = Regex("~~(.+?)~~", RegexOption.DOT_MATCHES_ALL)
    private val underlineRegex = Regex("__(.+?)__", RegexOption.DOT_MATCHES_ALL)
    private val codeRegex = Regex("`(.+?)`")

    fun parseMarkdownLite(text: String): AnnotatedString = buildHiddenDisplay(text).text

    fun buildHiddenDisplay(source: String): MarkdownDisplay {
        val builder = HiddenMarkdownBuilder(source)
        builder.build()
        return MarkdownDisplay(builder.toAnnotatedString(), builder.toOffsetMapping())
    }

    fun stripMarkdown(text: String): String {
        return text
            .replace(boldRegex) { it.groupValues[1] }
            .replace(strikeRegex) { it.groupValues[1] }
            .replace(underlineRegex) { it.groupValues[1] }
            .replace(codeRegex) { it.groupValues[1] }
            .replace(italicRegex) { it.groupValues[1] }
    }

    /**
     * Avvolge il testo selezionato con [marker], oppure lo rimuove se già presente.
     * Se non c'è selezione, inserisce i marker e posiziona il cursore nel mezzo.
     */
    fun wrapSelection(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        marker: String
    ): Pair<String, TextRange> {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(start, text.length)
        val markerLen = marker.length

        if (start == end) {
            val inserted = "$marker$marker"
            val newText = text.substring(0, start) + inserted + text.substring(start)
            // Cursore tra i marker: il testo successivo sarà formattato
            return newText to TextRange(start + markerLen)
        }

        // Toggle: se già avvolto, rimuovi i marker
        if (start >= markerLen &&
            end + markerLen <= text.length &&
            text.substring(start - markerLen, start) == marker &&
            text.substring(end, end + markerLen) == marker
        ) {
            val newText = text.substring(0, start - markerLen) +
                text.substring(start, end) +
                text.substring(end + markerLen)
            return newText to TextRange(start - markerLen, end - markerLen)
        }

        // Anche se la selezione include già i marker
        if (end - start > markerLen * 2 &&
            text.substring(start, start + markerLen) == marker &&
            text.substring(end - markerLen, end) == marker
        ) {
            val innerStart = start + markerLen
            val innerEnd = end - markerLen
            val newText = text.substring(0, start) +
                text.substring(innerStart, innerEnd) +
                text.substring(end)
            return newText to TextRange(start, start + (innerEnd - innerStart))
        }

        val selected = text.substring(start, end)
        val wrapped = "$marker$selected$marker"
        val newText = text.substring(0, start) + wrapped + text.substring(end)
        // Seleziona solo il contenuto (senza marker) per un mapping cursore corretto
        return newText to TextRange(start + markerLen, start + markerLen + selected.length)
    }

    fun toggleLinePrefix(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        prefix: String
    ): Pair<String, TextRange> {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(start, text.length)

        val lineStart = text.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }
        val lineEnd = if (end < text.length && text[end] == '\n') end
        else text.indexOf('\n', end).let { if (it == -1) text.length else it }

        val block = text.substring(lineStart, lineEnd)
        val lines = block.split('\n')
        val allHavePrefix = lines.isNotEmpty() && lines.all { it.isEmpty() || it.startsWith(prefix) }
        val newLines = lines.map { line ->
            when {
                line.isEmpty() -> line
                allHavePrefix && line.startsWith(prefix) -> line.removePrefix(prefix)
                !allHavePrefix && !line.startsWith(prefix) -> prefix + line
                else -> line
            }
        }
        val newBlock = newLines.joinToString("\n")
        val newText = text.substring(0, lineStart) + newBlock + text.substring(lineEnd)
        val newEnd = lineStart + newBlock.length
        return newText to TextRange(lineStart, newEnd)
    }
}

data class MarkdownDisplay(
    val text: AnnotatedString,
    val offsetMapping: OffsetMapping
)

class MarkdownVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val display = TextFormatHelper.buildHiddenDisplay(text.text)
        return TransformedText(display.text, display.offsetMapping)
    }
}

private class HiddenMarkdownBuilder(private val source: String) {
    private val stringBuilder = AnnotatedString.Builder()
    private val originalToTransformed = IntArray(source.length + 1) { -1 }
    private val transformedToOriginal = mutableListOf<Int>()

    private val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
    private val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
    private val strikeStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
    private val underlineStyle = SpanStyle(textDecoration = TextDecoration.Underline)
    private val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
    private val headingStyle = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)

    fun build() {
        var index = 0
        while (index < source.length) {
            val lineStart = index
            val lineEnd = source.indexOf('\n', index).let { if (it == -1) source.length else it }
            when {
                source.startsWith("## ", lineStart) && lineStart + 3 <= lineEnd -> {
                    mapSkipped(lineStart, lineStart + 3)
                    appendInlineRange(lineStart + 3, lineEnd, headingStyle)
                }
                source.startsWith("- ", lineStart) && lineStart + 2 <= lineEnd -> {
                    mapSkipped(lineStart, lineStart + 2)
                    // Bullet visibile al posto di "- "
                    appendReplacement(lineStart, '•')
                    appendReplacement(lineStart + 1, ' ')
                    appendInlineRange(lineStart + 2, lineEnd)
                }
                else -> appendInlineRange(lineStart, lineEnd)
            }
            if (lineEnd < source.length) {
                appendMappedChar(lineEnd, '\n')
                index = lineEnd + 1
            } else {
                index = lineEnd
            }
        }
        // Fine testo
        originalToTransformed[source.length] = stringBuilder.length
        // Riempie eventuali buchi (marker saltati)
        fillGaps()
    }

    fun toAnnotatedString(): AnnotatedString = stringBuilder.toAnnotatedString()

    fun toOffsetMapping(): OffsetMapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val safe = offset.coerceIn(0, source.length)
            val mapped = originalToTransformed[safe]
            return if (mapped >= 0) mapped else stringBuilder.length
        }

        override fun transformedToOriginal(offset: Int): Int {
            if (transformedToOriginal.isEmpty()) return 0
            val safe = offset.coerceIn(0, transformedToOriginal.size)
            return if (safe >= transformedToOriginal.size) source.length
            else transformedToOriginal[safe]
        }
    }

    private fun fillGaps() {
        var last = 0
        for (i in originalToTransformed.indices) {
            if (originalToTransformed[i] >= 0) {
                last = originalToTransformed[i]
            } else {
                originalToTransformed[i] = last
            }
        }
    }

    private fun mapSkipped(from: Int, until: Int) {
        val position = stringBuilder.length
        for (i in from until until.coerceAtMost(source.length)) {
            originalToTransformed[i] = position
        }
    }

    private fun appendReplacement(originalIndex: Int, char: Char) {
        originalToTransformed[originalIndex] = stringBuilder.length
        transformedToOriginal.add(originalIndex)
        stringBuilder.append(char)
    }

    private fun appendMappedChar(originalIndex: Int, char: Char, style: SpanStyle? = null) {
        originalToTransformed[originalIndex] = stringBuilder.length
        transformedToOriginal.add(originalIndex)
        if (style != null) {
            stringBuilder.withStyle(style) { append(char) }
        } else {
            stringBuilder.append(char)
        }
    }

    private fun appendMappedString(originalStart: Int, text: String, style: SpanStyle? = null) {
        if (text.isEmpty()) return
        if (style != null) {
            stringBuilder.pushStyle(style)
            text.forEachIndexed { offset, char ->
                originalToTransformed[originalStart + offset] = stringBuilder.length
                transformedToOriginal.add(originalStart + offset)
                stringBuilder.append(char)
            }
            stringBuilder.pop()
        } else {
            text.forEachIndexed { offset, char ->
                appendMappedChar(originalStart + offset, char)
            }
        }
    }

    private fun appendInlineRange(from: Int, until: Int, baseStyle: SpanStyle? = null) {
        if (baseStyle != null) stringBuilder.pushStyle(baseStyle)
        var index = from
        while (index < until) {
            when {
                matchPair(index, until, "**") -> {
                    val end = source.indexOf("**", index + 2)
                    mapSkipped(index, index + 2)
                    appendMappedString(index + 2, source.substring(index + 2, end), boldStyle)
                    mapSkipped(end, end + 2)
                    index = end + 2
                }
                matchPair(index, until, "~~") -> {
                    val end = source.indexOf("~~", index + 2)
                    mapSkipped(index, index + 2)
                    appendMappedString(index + 2, source.substring(index + 2, end), strikeStyle)
                    mapSkipped(end, end + 2)
                    index = end + 2
                }
                matchPair(index, until, "__") -> {
                    val end = source.indexOf("__", index + 2)
                    mapSkipped(index, index + 2)
                    appendMappedString(index + 2, source.substring(index + 2, end), underlineStyle)
                    mapSkipped(end, end + 2)
                    index = end + 2
                }
                source[index] == '`' -> {
                    val end = source.indexOf('`', index + 1)
                    if (end != -1 && end < until) {
                        mapSkipped(index, index + 1)
                        appendMappedString(index + 1, source.substring(index + 1, end), codeStyle)
                        mapSkipped(end, end + 1)
                        index = end + 1
                    } else {
                        appendMappedChar(index, source[index])
                        index++
                    }
                }
                source[index] == '*' && !source.startsWith("**", index) -> {
                    val end = source.indexOf('*', index + 1)
                    if (end != -1 && end < until && (end + 1 >= source.length || source[end + 1] != '*')) {
                        mapSkipped(index, index + 1)
                        appendMappedString(index + 1, source.substring(index + 1, end), italicStyle)
                        mapSkipped(end, end + 1)
                        index = end + 1
                    } else {
                        appendMappedChar(index, source[index])
                        index++
                    }
                }
                else -> {
                    appendMappedChar(index, source[index])
                    index++
                }
            }
        }
        if (baseStyle != null) stringBuilder.pop()
    }

    private fun matchPair(index: Int, until: Int, marker: String): Boolean {
        if (!source.startsWith(marker, index) || index + marker.length > until) return false
        val end = source.indexOf(marker, index + marker.length)
        return end != -1 && end + marker.length <= until
    }
}
