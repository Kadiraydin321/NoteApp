package com.example.noteapp.domain.util

data class SplitColumns(
    val leftColumn: String,
    val rightColumn: String
)

/**
 * Not içeriğini gazete / çift sütun okuma modunda (Dual-Column Reading Layout)
 * anlamsal blokları bölmeden dengeli iki sütuna ayıran motor.
 */
object ReadingColumnSplitter {

    /**
     * Verilen Markdown metnini kod blokları, başlıklar ve paragrafları koruyarak
     * kelime/karakter ağırlığına göre iki sütuna böler.
     */
    fun splitIntoBalancedColumns(content: String): SplitColumns {
        if (content.isBlank()) return SplitColumns("", "")

        val blocks = splitIntoSemanticBlocks(content)
        if (blocks.size <= 1) {
            return SplitColumns(leftColumn = content, rightColumn = "")
        }

        val blockWeights = blocks.map { it.length.coerceAtLeast(1) }
        val totalWeight = blockWeights.sum()
        val targetWeight = totalWeight / 2

        var accumulated = 0
        var bestIndex = 1
        var minDiff = Int.MAX_VALUE

        for (i in 0 until blocks.size - 1) {
            accumulated += blockWeights[i]
            val diff = kotlin.math.abs(accumulated - targetWeight)
            if (diff < minDiff) {
                minDiff = diff
                bestIndex = i + 1
            }
        }

        bestIndex = bestIndex.coerceIn(1, blocks.size - 1)

        val leftBlocks = blocks.subList(0, bestIndex)
        val rightBlocks = blocks.subList(bestIndex, blocks.size)

        return SplitColumns(
            leftColumn = leftBlocks.joinToString("\n\n"),
            rightColumn = rightBlocks.joinToString("\n\n")
        )
    }

    /**
     * Metni kod blokları (``` ... ```), başlıklar (#, ##, ###) ve boş satırlarla ayrılmış
     * anlamlı paragraflara böler. Kod bloklarının içi asla bölünmez.
     */
    fun splitIntoSemanticBlocks(content: String): List<String> {
        val lines = content.lines()
        val blocks = mutableListOf<String>()
        val currentBlock = StringBuilder()
        var insideCodeBlock = false

        for (line in lines) {
            val trimmed = line.trimStart()

            if (trimmed.startsWith("```")) {
                insideCodeBlock = !insideCodeBlock
                currentBlock.append(line).append("\n")
                if (!insideCodeBlock) {
                    blocks.add(currentBlock.toString().trimEnd())
                    currentBlock.clear()
                }
                continue
            }

            if (insideCodeBlock) {
                currentBlock.append(line).append("\n")
                continue
            }

            // Yeni bir Markdown başlığı geldiğinde önceki paragrafı kapat
            if (trimmed.startsWith("# ") || trimmed.startsWith("## ") || trimmed.startsWith("### ")) {
                if (currentBlock.isNotBlank()) {
                    blocks.add(currentBlock.toString().trimEnd())
                    currentBlock.clear()
                }
                currentBlock.append(line).append("\n")
                continue
            }

            // Boş satır paragraf sonudur
            if (trimmed.isEmpty()) {
                if (currentBlock.isNotBlank()) {
                    blocks.add(currentBlock.toString().trimEnd())
                    currentBlock.clear()
                }
                continue
            }

            currentBlock.append(line).append("\n")
        }

        if (currentBlock.isNotBlank()) {
            blocks.add(currentBlock.toString().trimEnd())
        }

        return blocks.filter { it.isNotBlank() }
    }
}
