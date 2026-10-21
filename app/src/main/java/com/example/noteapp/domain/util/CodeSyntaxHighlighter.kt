package com.example.noteapp.domain.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

/**
 * Kod sözdizimi belirteç (token) tipleri.
 */
enum class CodeTokenType {
    KEYWORD,
    STRING,
    COMMENT,
    NUMBER,
    TYPE,
    PUNCTUATION,
    PLAIN
}

/**
 * Belirteç modeli.
 */
data class CodeToken(
    val type: CodeTokenType,
    val start: Int,
    val end: Int,
    val text: String
)

/**
 * Kod Blokları Sözdizimi Renklendirici Motoru (Code Syntax Highlighter).
 * Kotlin, Java, Python, JavaScript, TypeScript, SQL, JSON, HTML ve Bash
 * dillerinde kod bloklarını analiz ederek anahtar kelimeleri, dizgileri,
 * yorumları ve sayıları belirler ve `AnnotatedString` olarak stillendirir.
 */
object CodeSyntaxHighlighter {

    private val KOTLIN_KEYWORDS = setOf(
        "val", "var", "fun", "class", "object", "interface", "return", "if", "else", "when",
        "for", "while", "do", "package", "import", "override", "private", "public", "protected",
        "internal", "data", "sealed", "enum", "null", "true", "false", "this", "super",
        "companion", "suspend", "inline", "tailrec", "operator", "infix", "typealias", "is", "as"
    )

    private val JAVA_KEYWORDS = setOf(
        "public", "private", "protected", "class", "interface", "enum", "extends", "implements",
        "return", "if", "else", "switch", "case", "for", "while", "do", "package", "import",
        "void", "static", "final", "new", "this", "super", "null", "true", "false", "try", "catch", "finally", "throw", "throws"
    )

    private val PYTHON_KEYWORDS = setOf(
        "def", "class", "import", "from", "return", "if", "elif", "else", "for", "while",
        "in", "is", "not", "and", "or", "True", "False", "None", "with", "as", "try",
        "except", "finally", "raise", "pass", "lambda", "yield", "global", "nonlocal", "assert"
    )

    private val JS_KEYWORDS = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while", "import",
        "from", "export", "default", "class", "async", "await", "new", "this", "null",
        "undefined", "true", "false", "try", "catch", "finally", "throw", "typeof", "instanceof", "interface", "type"
    )

    private val SQL_KEYWORDS = setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "UPDATE", "DELETE", "CREATE", "TABLE",
        "DROP", "ALTER", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ORDER", "BY", "GROUP",
        "HAVING", "LIMIT", "AND", "OR", "NOT", "NULL", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
        "VALUES", "SET", "DISTINCT", "AS", "IN", "IS", "LIKE", "BETWEEN", "UNION", "ALL"
    )

    private val BASH_KEYWORDS = setOf(
        "echo", "cd", "ls", "mkdir", "rm", "cp", "mv", "git", "chmod", "sudo", "export",
        "source", "if", "then", "else", "elif", "fi", "for", "do", "done", "while", "until",
        "case", "esac", "function", "return", "exit", "grep", "cat", "curl", "wget"
    )

    /**
     * Dil adını kullanıcı dostu formata ve iç standart isme dönüştürür.
     */
    fun normalizeLanguageName(rawLang: String): String {
        return when (rawLang.trim().lowercase(Locale.ROOT)) {
            "kt", "kotlin" -> "Kotlin"
            "java" -> "Java"
            "py", "python" -> "Python"
            "js", "javascript" -> "JavaScript"
            "ts", "typescript" -> "TypeScript"
            "sql" -> "SQL"
            "json" -> "JSON"
            "html", "xml" -> "HTML/XML"
            "sh", "bash", "shell", "zsh" -> "Bash"
            "css" -> "CSS"
            "md", "markdown" -> "Markdown"
            "" -> "Kod"
            else -> rawLang.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    /**
     * Verilen kod metnini dile göre belirteçlere (tokens) ayırır.
     */
    fun tokenize(code: String, rawLang: String): List<CodeToken> {
        if (code.isEmpty()) return emptyList()

        val normLang = normalizeLanguageName(rawLang).lowercase(Locale.ROOT)
        val tokens = mutableListOf<CodeToken>()
        var i = 0
        val len = code.length

        while (i < len) {
            val c = code[i]

            // 1. Yorum Satırları
            if ((normLang == "python" || normLang == "bash") && c == '#') {
                val start = i
                while (i < len && code[i] != '\n') i++
                tokens.add(CodeToken(CodeTokenType.COMMENT, start, i, code.substring(start, i)))
                continue
            }

            if (c == '/' && i + 1 < len && code[i + 1] == '/') {
                val start = i
                while (i < len && code[i] != '\n') i++
                tokens.add(CodeToken(CodeTokenType.COMMENT, start, i, code.substring(start, i)))
                continue
            }

            if (c == '/' && i + 1 < len && code[i + 1] == '*') {
                val start = i
                i += 2
                while (i + 1 < len && !(code[i] == '*' && code[i + 1] == '/')) i++
                i = (i + 2).coerceAtMost(len)
                tokens.add(CodeToken(CodeTokenType.COMMENT, start, i, code.substring(start, i)))
                continue
            }

            // SQL yorumu (-- ...)
            if (normLang == "sql" && c == '-' && i + 1 < len && code[i + 1] == '-') {
                val start = i
                while (i < len && code[i] != '\n') i++
                tokens.add(CodeToken(CodeTokenType.COMMENT, start, i, code.substring(start, i)))
                continue
            }

            // 2. String Dizgileri ("..." veya '...')
            if (c == '"' || c == '\'') {
                val quote = c
                val start = i
                i++
                while (i < len && code[i] != quote && code[i] != '\n') {
                    if (code[i] == '\\' && i + 1 < len) i++ // escape karakterini atla
                    i++
                }
                if (i < len && code[i] == quote) i++
                tokens.add(CodeToken(CodeTokenType.STRING, start, i, code.substring(start, i)))
                continue
            }

            // 3. Sayılar
            if (c.isDigit()) {
                val start = i
                while (i < len && (code[i].isDigit() || code[i] == '.' || code[i] == 'f' || code[i] == 'L' || code[i] == 'x')) {
                    i++
                }
                tokens.add(CodeToken(CodeTokenType.NUMBER, start, i, code.substring(start, i)))
                continue
            }

            // 4. Tanımlayıcılar (Kelimeler & Anahtar Kelimeler)
            if (c.isLetter() || c == '_' || c == '@' || c == '$') {
                val start = i
                while (i < len && (code[i].isLetterOrDigit() || code[i] == '_')) {
                    i++
                }
                val word = code.substring(start, i)
                val type = when {
                    word.startsWith("@") -> CodeTokenType.TYPE
                    isKeyword(word, normLang) -> CodeTokenType.KEYWORD
                    word.firstOrNull()?.isUpperCase() == true && normLang != "sql" -> CodeTokenType.TYPE
                    else -> CodeTokenType.PLAIN
                }
                tokens.add(CodeToken(type, start, i, word))
                continue
            }

            // 5. Diğer Semboller ve Boşluklar
            val start = i
            i++
            val type = if ("{}[](),;:.".contains(c)) CodeTokenType.PUNCTUATION else CodeTokenType.PLAIN
            tokens.add(CodeToken(type, start, i, code.substring(start, i)))
        }

        return tokens
    }

    private fun isKeyword(word: String, normLang: String): Boolean {
        return when (normLang) {
            "kotlin" -> KOTLIN_KEYWORDS.contains(word)
            "java" -> JAVA_KEYWORDS.contains(word)
            "python" -> PYTHON_KEYWORDS.contains(word)
            "javascript", "typescript" -> JS_KEYWORDS.contains(word)
            "sql" -> SQL_KEYWORDS.contains(word.uppercase(Locale.ROOT))
            "bash" -> BASH_KEYWORDS.contains(word)
            else -> KOTLIN_KEYWORDS.contains(word) || PYTHON_KEYWORDS.contains(word) || JS_KEYWORDS.contains(word)
        }
    }

    /**
     * Kod belirteçlerini renkli `AnnotatedString` nesnesine dönüştürür.
     */
    fun highlightToAnnotatedString(
        code: String,
        rawLang: String,
        isDarkTheme: Boolean = false,
        defaultContentColor: Color = if (isDarkTheme) Color(0xFFE0E0E0) else Color(0xFF212121)
    ): AnnotatedString {
        val tokens = tokenize(code, rawLang)
        return buildAnnotatedString {
            // Renk paleti
            val keywordColor = if (isDarkTheme) Color(0xFFBB86FC) else Color(0xFF6200EE)
            val stringColor = if (isDarkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
            val commentColor = if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF757575)
            val numberColor = if (isDarkTheme) Color(0xFFFFB74D) else Color(0xFFE65100)
            val typeColor = if (isDarkTheme) Color(0xFF4DD0E1) else Color(0xFF00838F)

            tokens.forEach { token ->
                val style = when (token.type) {
                    CodeTokenType.KEYWORD -> SpanStyle(
                        color = keywordColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    CodeTokenType.STRING -> SpanStyle(
                        color = stringColor,
                        fontFamily = FontFamily.Monospace
                    )
                    CodeTokenType.COMMENT -> SpanStyle(
                        color = commentColor,
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Monospace
                    )
                    CodeTokenType.NUMBER -> SpanStyle(
                        color = numberColor,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    CodeTokenType.TYPE -> SpanStyle(
                        color = typeColor,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    CodeTokenType.PUNCTUATION -> SpanStyle(
                        color = defaultContentColor.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace
                    )
                    CodeTokenType.PLAIN -> SpanStyle(
                        color = defaultContentColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                append(AnnotatedString(token.text, style))
            }
        }
    }
}
