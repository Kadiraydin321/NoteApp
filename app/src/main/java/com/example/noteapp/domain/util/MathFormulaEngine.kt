package com.example.noteapp.domain.util

/**
 * Matematiksel ifade türü.
 */
enum class MathExpressionType {
    INLINE,   // $...$
    DISPLAY   // $$...$$
}

/**
 * Ayrıştırılmış matematiksel ifade modeli.
 */
data class MathExpression(
    val rawText: String,
    val latex: String,
    val type: MathExpressionType,
    val startIndex: Int,
    val endIndex: Int,
    val unicodeMath: String
)

/**
 * Hazır STEM & Matematik formül şablonu.
 */
data class MathTemplate(
    val title: String,
    val category: String,
    val latex: String,
    val previewUnicode: String,
    val isBlock: Boolean = false
)

/**
 * Not İçi Matematiksel Formül & LaTeX Ayrıştırma ve Gösterim Motoru.
 * Markdown içerisindeki inline ($...$) ve display ($$...$$) formülleri ayrıştırır,
 * yaygın LaTeX ifadelerini yüksek kaliteli okunabilir Unicode matematik sembollerine dönüştürür.
 */
object MathFormulaEngine {

    // DISPLAY FORMULA: $$ ... $$ (tek veya çok satırlı)
    private val DISPLAY_MATH_REGEX = Regex("(?<!\\\\)\\$\\$([\\s\\S]*?)(?<!\\\\)\\$\\$")

    // INLINE FORMULA: $ ... $ (tek satırlı, içi boş olmayan, boşlukla başlamayan/bitmeyen, kaçış karakterli olmayan)
    private val INLINE_MATH_REGEX = Regex("(?<!\\\\)\\$(?!\\s)([^$\\n]+?)(?<![\\s\\\\])\\$")

    // Üst simge harita tablosu
    private val SUPERSCRIPT_MAP = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ', 'a' to 'ᵃ',
        'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ', 'k' to 'ᵏ',
        'm' to 'ᵐ', 'p' to 'ᵖ', 't' to 'ᵗ'
    )

    // Alt simge harita tablosu
    private val SUBSCRIPT_MAP = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ'
    )

    // Yaygın Yunan Harfleri
    private val GREEK_LETTERS = listOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
        "\\theta" to "θ", "\\vartheta" to "ϑ", "\\iota" to "ι", "\\kappa" to "κ",
        "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ",
        "\\pi" to "π", "\\varpi" to "ϖ", "\\rho" to "ρ", "\\varrho" to "ϱ",
        "\\sigma" to "σ", "\\varsigma" to "ς", "\\tau" to "τ", "\\upsilon" to "υ",
        "\\phi" to "φ", "\\varphi" to "ϕ", "\\chi" to "χ", "\\psi" to "ψ",
        "\\omega" to "ω",
        "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ",
        "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Upsilon" to "Υ",
        "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω"
    )

    // Yaygın Matematik Operatörleri ve Sembolleri
    private val MATH_OPERATORS = listOf(
        "\\pm" to "±", "\\mp" to "∓", "\\times" to "×", "\\div" to "÷",
        "\\cdot" to "·", "\\ast" to "∗", "\\star" to "⋆", "\\circ" to "∘",
        "\\bullet" to "•", "\\infty" to "∞", "\\propto" to "∝",
        "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥",
        "\\neq" to "≠", "\\ne" to "≠", "\\approx" to "≈", "\\equiv" to "≡",
        "\\sim" to "∼", "\\simeq" to "≃", "\\cong" to "≅",
        "\\in" to "∈", "\\notin" to "∉", "\\subset" to "⊂", "\\subseteq" to "⊆",
        "\\supset" to "⊃", "\\supseteq" to "⊇", "\\cup" to "∪", "\\cap" to "∩",
        "\\setminus" to "∖", "\\emptyset" to "∅", "\\forall" to "∀", "\\exists" to "∃",
        "\\nexists" to "∄", "\\neg" to "¬", "\\land" to "∧", "\\lor" to "∨",
        "\\nabla" to "∇", "\\partial" to "∂", "\\sum" to "∑", "\\prod" to "∏",
        "\\int" to "∫", "\\iint" to "∬", "\\iiint" to "∭", "\\oint" to "∮",
        "\\sqrt" to "√", "\\perp" to "⊥", "\\angle" to "∠", "\\degree" to "°"
    )

    // Ok ve İlişki Sembolleri
    private val ARROWS = listOf(
        "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←", "\\gets" to "←",
        "\\Rightarrow" to "⇒", "\\Leftarrow" to "⇐", "\\leftrightarrow" to "↔",
        "\\Leftrightarrow" to "⇔", "\\iff" to "⇔", "\\implies" to "⟹",
        "\\uparrow" to "↑", "\\downarrow" to "↓", "\\mapsto" to "↦"
    )

    // Standart Fonksiyon Adları
    private val MATH_FUNCTIONS = listOf(
        "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan",
        "\\cot" to "cot", "\\sec" to "sec", "\\csc" to "csc",
        "\\arcsin" to "arcsin", "\\arccos" to "arccos", "\\arctan" to "arctan",
        "\\sinh" to "sinh", "\\cosh" to "cosh", "\\tanh" to "tanh",
        "\\ln" to "ln", "\\log" to "log", "\\exp" to "exp",
        "\\lim" to "lim", "\\max" to "max", "\\min" to "min",
        "\\sup" to "sup", "\\inf" to "inf", "\\det" to "det",
        "\\gcd" to "gcd", "\\deg" to "deg"
    )

    /**
     * Metin içinde matematiksel formül olup olmadığını hızlıca kontrol eder.
     */
    fun hasMathFormulas(text: String): Boolean {
        if (text.isBlank() || !text.contains("$")) return false
        return DISPLAY_MATH_REGEX.containsMatchIn(text) || INLINE_MATH_REGEX.containsMatchIn(text)
    }

    /**
     * Metin içerisindeki tüm inline ve display matematik formüllerini ayıklar.
     */
    fun extractMathExpressions(rawText: String): List<MathExpression> {
        if (rawText.isBlank() || !rawText.contains("$")) return emptyList()

        val expressions = mutableListOf<MathExpression>()
        val consumedRanges = mutableListOf<IntRange>()

        // 1. Önce Block / Display ($$...$$) formülleri ayıkla
        for (match in DISPLAY_MATH_REGEX.findAll(rawText)) {
            val range = match.range
            val innerLatex = match.groupValues[1].trim()
            if (innerLatex.isNotEmpty()) {
                val expr = MathExpression(
                    rawText = match.value,
                    latex = innerLatex,
                    type = MathExpressionType.DISPLAY,
                    startIndex = range.first,
                    endIndex = range.last + 1,
                    unicodeMath = convertToUnicodeMath(innerLatex)
                )
                expressions.add(expr)
                consumedRanges.add(range)
            }
        }

        // 2. Ardından satır içi ($...$) formülleri ayıkla (Display bloklarının içinde olmayanlar)
        for (match in INLINE_MATH_REGEX.findAll(rawText)) {
            val range = match.range
            // Eğer display formulun parçası ise veya para birimi deseni ise atla
            val overlaps = consumedRanges.any { it.contains(range.first) || it.contains(range.last) }
            if (!overlaps) {
                val innerLatex = match.groupValues[1].trim()
                // Para birimi false-positive kontrolü (Örn: $100 ve $200 tek satırda geçiyorsa ve harf içermiyorsa)
                if (innerLatex.isNotEmpty() && !isCurrencyFalsePositive(innerLatex)) {
                    val expr = MathExpression(
                        rawText = match.value,
                        latex = innerLatex,
                        type = MathExpressionType.INLINE,
                        startIndex = range.first,
                        endIndex = range.last + 1,
                        unicodeMath = convertToUnicodeMath(innerLatex)
                    )
                    expressions.add(expr)
                }
            }
        }

        return expressions.sortedBy { it.startIndex }
    }

    /**
     * Tek başına sayı ve para birimi olup olmadığını kontrol eder (Örn: "100", "50.99").
     */
    private fun isCurrencyFalsePositive(text: String): Boolean {
        return text.matches(Regex("^\\d+([.,]\\d+)?$"))
    }

    /**
     * LaTeX matematiksel ifadesini okunabilir Unicode sembollerine ve biçimlendirmesine dönüştürür.
     */
    fun convertToUnicodeMath(latex: String): String {
        if (latex.isBlank()) return ""

        var result = latex.trim()

        // 1. Boşluk ve parantez temizlikleri
        result = result
            .replace("\\left(", "(")
            .replace("\\right)", ")")
            .replace("\\left[", "[")
            .replace("\\right]", "]")
            .replace("\\left\\{", "{")
            .replace("\\right\\}", "}")
            .replace("\\left|", "|")
            .replace("\\right|", "|")
            .replace("\\,", " ")
            .replace("\\;", " ")
            .replace("\\quad", "  ")
            .replace("\\qquad", "    ")
            .replace("\\!", "")

        // 2. Metin komutları (\text{...}, \mathrm{...})
        result = Regex("\\\\text\\{([^}]+)\\}").replace(result) { it.groupValues[1] }
        result = Regex("\\\\mathrm\\{([^}]+)\\}").replace(result) { it.groupValues[1] }
        result = Regex("\\\\mathbf\\{([^}]+)\\}").replace(result) { it.groupValues[1] }
        result = Regex("\\\\mathit\\{([^}]+)\\}").replace(result) { it.groupValues[1] }

        // 3. Kesirler (\frac{a}{b})
        result = Regex("\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}").replace(result) { match ->
            val num = match.groupValues[1].trim()
            val den = match.groupValues[2].trim()
            when {
                num == "1" && den == "2" -> "½"
                num == "1" && den == "3" -> "⅓"
                num == "2" && den == "3" -> "⅔"
                num == "1" && den == "4" -> "¼"
                num == "3" && den == "4" -> "¾"
                num.length == 1 && den.length == 1 -> "$num/$den"
                else -> "($num / $den)"
            }
        }

        // 4. Karekök (\sqrt{x} ve \sqrt[n]{x})
        result = Regex("\\\\sqrt\\[([^]]+)\\]\\{([^}]+)\\}").replace(result) { match ->
            val root = match.groupValues[1].trim()
            val content = match.groupValues[2].trim()
            "${toSuperscript(root)}√($content)"
        }
        result = Regex("\\\\sqrt\\{([^}]+)\\}").replace(result) { match ->
            val content = match.groupValues[1].trim()
            "√($content)"
        }

        // 5. Yunan Harfleri
        result = replaceLatexCommands(result, GREEK_LETTERS)

        // 6. Matematik Operatörleri
        result = replaceLatexCommands(result, MATH_OPERATORS)

        // 7. Oklar
        result = replaceLatexCommands(result, ARROWS)

        // 8. Standart Fonksiyonlar
        result = replaceLatexCommands(result, MATH_FUNCTIONS)

        // 9. Üst simgeler (Exponents / Powers): ^{...} ve tek karakter ^x
        result = Regex("\\^\\{([^}]+)\\}").replace(result) { match ->
            toSuperscript(match.groupValues[1])
        }
        result = Regex("\\^([0-9a-zA-Z+\\-])").replace(result) { match ->
            toSuperscript(match.groupValues[1])
        }

        // 10. Alt simgeler (Subscripts): _{...} ve tek karakter _x
        result = Regex("_\\{([^}]+)\\}").replace(result) { match ->
            toSubscript(match.groupValues[1])
        }
        result = Regex("_([0-9a-zA-Z+\\-])").replace(result) { match ->
            toSubscript(match.groupValues[1])
        }

        // Kalan gereksiz süslü parantezleri temizle
        result = result.replace("{", "").replace("}", "")

        return result.trim()
    }

    /**
     * Dizeyi üst simgelere dönüştürür. Haritada olmayan karakterleri olduğu gibi bırakır.
     */
    fun toSuperscript(text: String): String {
        return text.map { SUPERSCRIPT_MAP[it] ?: it }.joinToString("")
    }

    /**
     * Dizeyi alt simgelere dönüştürür. Haritada olmayan karakterleri olduğu gibi bırakır.
     */
    fun toSubscript(text: String): String {
        return text.map { SUBSCRIPT_MAP[it] ?: it }.joinToString("")
    }

    /**
     * LaTeX komutlarını uzunluklarına göre azalan sırada ve kelime sınırına dikkat ederek dönüştürür.
     */
    private fun replaceLatexCommands(input: String, commands: List<Pair<String, String>>): String {
        var text = input
        val sorted = commands.sortedByDescending { it.first.length }
        for ((cmd, replacement) in sorted) {
            val pattern = Regex(Regex.escape(cmd) + "(?![a-zA-Z])")
            text = text.replace(pattern, replacement)
        }
        return text
    }

    /**
     * Formülü LaTeX formatı ile sarar.
     */
    fun wrapInLatex(formula: String, isBlock: Boolean): String {
        val clean = formula.trim()
        return if (isBlock) {
            "$$\n$clean\n$$"
        } else {
            "$$clean$"
        }
    }

    /**
     * Hazır STEM & Matematik Formül Şablonları Kütüphanesi.
     */
    val TEMPLATE_LIBRARY: List<MathTemplate> = listOf(
        // Cebir
        MathTemplate(
            title = "Pisagor Teoremi",
            category = "Cebir & Geometri",
            latex = "a^2 + b^2 = c^2",
            previewUnicode = "a² + b² = c²",
            isBlock = false
        ),
        MathTemplate(
            title = "İkinci Dereceden Denklem Kökleri",
            category = "Cebir",
            latex = "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}",
            previewUnicode = "x = (-b ± √(b² - 4ac) / 2a)",
            isBlock = true
        ),
        MathTemplate(
            title = "Tam Kare Açılımı",
            category = "Cebir",
            latex = "(a + b)^2 = a^2 + 2ab + b^2",
            previewUnicode = "(a + b)² = a² + 2ab + b²",
            isBlock = false
        ),
        MathTemplate(
            title = "İki Kare Farkı",
            category = "Cebir",
            latex = "a^2 - b^2 = (a - b)(a + b)",
            previewUnicode = "a² - b² = (a - b)(a + b)",
            isBlock = false
        ),

        // Fizik
        MathTemplate(
            title = "Kütle-Enerji Eşdeğerliği (Einstein)",
            category = "Fizik",
            latex = "E = mc^2",
            previewUnicode = "E = mc²",
            isBlock = false
        ),
        MathTemplate(
            title = "Newton'un 2. Hareket Yasası",
            category = "Fizik",
            latex = "F = m \\cdot a",
            previewUnicode = "F = m · a",
            isBlock = false
        ),
        MathTemplate(
            title = "Evrensel Kütleçekim Yasası",
            category = "Fizik",
            latex = "F = G \\frac{m_1 m_2}{r^2}",
            previewUnicode = "F = G (m₁ m₂ / r²)",
            isBlock = true
        ),
        MathTemplate(
            title = "Kinetik Enerji",
            category = "Fizik",
            latex = "E_k = \\frac{1}{2} m v^2",
            previewUnicode = "Eₖ = ½ m v²",
            isBlock = false
        ),

        // Kalkülüs
        MathTemplate(
            title = "Euler Özdeşliği",
            category = "Kalkülüs & Analiz",
            latex = "e^{i\\pi} + 1 = 0",
            previewUnicode = "eⁱᵖⁱ + 1 = 0",
            isBlock = false
        ),
        MathTemplate(
            title = "Türevin Tanımı (Limit)",
            category = "Kalkülüs & Analiz",
            latex = "f'(x) = \\lim_{h \\to 0} \\frac{f(x+h) - f(x)}{h}",
            previewUnicode = "f'(x) = limₕ ₜₒ ₀ (f(x+h) - f(x) / h)",
            isBlock = true
        ),
        MathTemplate(
            title = "Gauss İntegrali",
            category = "Kalkülüs & Analiz",
            latex = "\\int_{-\\infty}^{\\infty} e^{-x^2} dx = \\sqrt{\\pi}",
            previewUnicode = "∫₋∞^∞ e⁻ˣ² dx = √π",
            isBlock = true
        ),
        MathTemplate(
            title = "Aritmetik Seri Toplamı (Gauss)",
            category = "Kalkülüs & Analiz",
            latex = "\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}",
            previewUnicode = "∑ᵢ₌₁ⁿ i = (n(n+1) / 2)",
            isBlock = true
        ),

        // Geometri & Trigonometri
        MathTemplate(
            title = "Dairenin Alanı",
            category = "Geometri & Trigonometri",
            latex = "A = \\pi r^2",
            previewUnicode = "A = π r²",
            isBlock = false
        ),
        MathTemplate(
            title = "Trigonometrik Temel Özdeşlik",
            category = "Geometri & Trigonometri",
            latex = "\\sin^2(\\theta) + \\cos^2(\\theta) = 1",
            previewUnicode = "sin²(θ) + cos²(θ) = 1",
            isBlock = false
        ),

        // İstatistik & Olasılık
        MathTemplate(
            title = "Normal Dağılım Fonksiyonu (Gauss)",
            category = "İstatistik & Olasılık",
            latex = "f(x) = \\frac{1}{\\sigma \\sqrt{2\\pi}} e^{-\\frac{1}{2}\\left(\\frac{x-\\mu}{\\sigma}\\right)^2}",
            previewUnicode = "f(x) = (1 / σ √(2π)) e^(-½((x-μ)/σ)²)",
            isBlock = true
        ),
        MathTemplate(
            title = "Bayes Teoremi",
            category = "İstatistik & Olasılık",
            latex = "P(A|B) = \\frac{P(B|A) \\cdot P(A)}{P(B)}",
            previewUnicode = "P(A|B) = (P(B|A) · P(A) / P(B))",
            isBlock = true
        )
    )
}
