package com.example.noteapp.domain.util

/**
 * Not Versiyon Karşılaştırma & Görsel Diff Motoru.
 * İki metin (veya başlık) arasındaki satır bazlı farkları LCS (Longest Common Subsequence)
 * algoritması ve ortak önek/sonek budama optimizasyonu ile hesaplar.
 */
object NoteDiffEngine {

    enum class DiffType {
        ADDED,      // Yeni versiyonda eklenen satır (+)
        DELETED,    // Eski versiyonda olup silinen satır (-)
        UNCHANGED   // Değişmeyen satır ( )
    }

    data class DiffLine(
        val type: DiffType,
        val text: String,
        val oldLineNumber: Int?,
        val newLineNumber: Int?
    )

    data class DiffResult(
        val lines: List<DiffLine>,
        val additionsCount: Int,
        val deletionsCount: Int,
        val unchangedCount: Int,
        val titleChanged: Boolean,
        val oldTitle: String,
        val newTitle: String
    ) {
        val totalChanges: Int get() = additionsCount + deletionsCount
        val hasChanges: Boolean get() = totalChanges > 0 || titleChanged
    }

    /**
     * Eski metin ile yeni metin arasındaki satır bazlı diff'i hesaplar.
     */
    fun computeDiff(
        oldTitle: String,
        oldContent: String,
        newTitle: String,
        newContent: String
    ): DiffResult {
        val oldLines = if (oldContent.isEmpty()) emptyList() else oldContent.lines()
        val newLines = if (newContent.isEmpty()) emptyList() else newContent.lines()

        val diffLines = computeLineDiff(oldLines, newLines)

        var additions = 0
        var deletions = 0
        var unchanged = 0

        for (line in diffLines) {
            when (line.type) {
                DiffType.ADDED -> additions++
                DiffType.DELETED -> deletions++
                DiffType.UNCHANGED -> unchanged++
            }
        }

        return DiffResult(
            lines = diffLines,
            additionsCount = additions,
            deletionsCount = deletions,
            unchangedCount = unchanged,
            titleChanged = oldTitle != newTitle,
            oldTitle = oldTitle,
            newTitle = newTitle
        )
    }

    /**
     * Satır listeleri arasındaki farkları ortak önek/sonek budama ve LCS algoritması ile hesaplar.
     */
    fun computeLineDiff(oldLines: List<String>, newLines: List<String>): List<DiffLine> {
        val n = oldLines.size
        val m = newLines.size

        if (n == 0 && m == 0) return emptyList()

        if (n == 0) {
            return newLines.mapIndexed { idx, text ->
                DiffLine(DiffType.ADDED, text, oldLineNumber = null, newLineNumber = idx + 1)
            }
        }

        if (m == 0) {
            return oldLines.mapIndexed { idx, text ->
                DiffLine(DiffType.DELETED, text, oldLineNumber = idx + 1, newLineNumber = null)
            }
        }

        // 1. Ortak Önek Budama (Common Prefix Optimization)
        var prefixLen = 0
        while (prefixLen < n && prefixLen < m && oldLines[prefixLen] == newLines[prefixLen]) {
            prefixLen++
        }

        // 2. Ortak Sonek Budama (Common Suffix Optimization)
        var suffixLen = 0
        while (suffixLen < (n - prefixLen) && suffixLen < (m - prefixLen) &&
            oldLines[n - 1 - suffixLen] == newLines[m - 1 - suffixLen]
        ) {
            suffixLen++
        }

        val result = mutableListOf<DiffLine>()

        // Önek satırlarını ekle
        for (i in 0 until prefixLen) {
            result.add(
                DiffLine(
                    type = DiffType.UNCHANGED,
                    text = oldLines[i],
                    oldLineNumber = i + 1,
                    newLineNumber = i + 1
                )
            )
        }

        // Ortadaki değişen dilimi hesapla
        val midOld = oldLines.subList(prefixLen, n - suffixLen)
        val midNew = newLines.subList(prefixLen, m - suffixLen)

        val midDiff = computeLcsDiff(midOld, midNew, prefixLen)
        result.addAll(midDiff)

        // Sonek satırlarını ekle
        for (i in 0 until suffixLen) {
            val oldIdx = n - suffixLen + i
            val newIdx = m - suffixLen + i
            result.add(
                DiffLine(
                    type = DiffType.UNCHANGED,
                    text = oldLines[oldIdx],
                    oldLineNumber = oldIdx + 1,
                    newLineNumber = newIdx + 1
                )
            )
        }

        return result
    }

    /**
     * İki liste arasındaki LCS (Longest Common Subsequence) DP tablosunu kurarak Diff satırlarını üretir.
     */
    private fun computeLcsDiff(
        oldList: List<String>,
        newList: List<String>,
        lineOffset: Int
    ): List<DiffLine> {
        val n = oldList.size
        val m = newList.size

        if (n == 0) {
            return newList.mapIndexed { idx, text ->
                DiffLine(DiffType.ADDED, text, oldLineNumber = null, newLineNumber = lineOffset + idx + 1)
            }
        }
        if (m == 0) {
            return oldList.mapIndexed { idx, text ->
                DiffLine(DiffType.DELETED, text, oldLineNumber = lineOffset + idx + 1, newLineNumber = null)
            }
        }

        // DP tablosu
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 1..n) {
            for (j in 1..m) {
                if (oldList[i - 1] == newList[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        // Geriye doğru izleme (Backtracking)
        val temp = mutableListOf<DiffLine>()
        var i = n
        var j = m

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldList[i - 1] == newList[j - 1]) {
                temp.add(
                    DiffLine(
                        type = DiffType.UNCHANGED,
                        text = oldList[i - 1],
                        oldLineNumber = lineOffset + i,
                        newLineNumber = lineOffset + j
                    )
                )
                i--
                j--
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                temp.add(
                    DiffLine(
                        type = DiffType.ADDED,
                        text = newList[j - 1],
                        oldLineNumber = null,
                        newLineNumber = lineOffset + j
                    )
                )
                j--
            } else if (i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j])) {
                temp.add(
                    DiffLine(
                        type = DiffType.DELETED,
                        text = oldList[i - 1],
                        oldLineNumber = lineOffset + i,
                        newLineNumber = null
                    )
                )
                i--
            }
        }

        return temp.reversed()
    }
}
