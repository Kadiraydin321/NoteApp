package com.example.noteapp

import com.example.noteapp.domain.util.MathExpressionType
import com.example.noteapp.domain.util.MathFormulaEngine
import org.junit.Assert.*
import org.junit.Test

class MathFormulaEngineTest {

    @Test
    fun hasMathFormulas_identifiesInlineAndDisplayFormulas() {
        assertFalse(MathFormulaEngine.hasMathFormulas(""))
        assertFalse(MathFormulaEngine.hasMathFormulas("Düz bir metin ve formül yok"))
        
        assertTrue(MathFormulaEngine.hasMathFormulas("Burada \$E = mc^2\$ var."))
        assertTrue(MathFormulaEngine.hasMathFormulas("\$\$\\sum_{i=1}^n i\$\$"))
    }

    @Test
    fun extractMathExpressions_extractsInlineAndDisplayFormulasAccurately() {
        val markdown = """
            # Fizik Notları
            Temel denklem: ${'$'}E = mc^2${'$'} olarak bilinir.
            
            Ardından Gauss toplamı:
            ${'$'}${'$'}
            \sum_{i=1}^{n} i = \frac{n(n+1)}{2}
            ${'$'}${'$'}
            
            Ve Pisagor: ${'$'}a^2 + b^2 = c^2${'$'}.
        """.trimIndent()

        val expressions = MathFormulaEngine.extractMathExpressions(markdown)
        assertEquals(3, expressions.size)

        // 1. Inline E = mc^2
        assertEquals(MathExpressionType.INLINE, expressions[0].type)
        assertEquals("E = mc^2", expressions[0].latex)
        assertTrue(expressions[0].unicodeMath.contains("mc²"))

        // 2. Display Gauss sum
        assertEquals(MathExpressionType.DISPLAY, expressions[1].type)
        assertTrue(expressions[1].latex.contains("\\sum"))
        assertTrue(expressions[1].unicodeMath.contains("∑"))

        // 3. Inline a^2 + b^2 = c^2
        assertEquals(MathExpressionType.INLINE, expressions[2].type)
        assertEquals("a^2 + b^2 = c^2", expressions[2].latex)
        assertEquals("a² + b² = c²", expressions[2].unicodeMath)
    }

    @Test
    fun convertToUnicodeMath_translatesGreekLettersCorrectly() {
        val latex = "\\alpha + \\beta = \\gamma \\cdot \\theta + \\pi + \\Omega"
        val unicode = MathFormulaEngine.convertToUnicodeMath(latex)

        assertEquals("α + β = γ · θ + π + Ω", unicode)
    }

    @Test
    fun convertToUnicodeMath_translatesFractionsAndRoots() {
        // Standart kesir
        val frac = "\\frac{a}{b}"
        assertEquals("a/b", MathFormulaEngine.convertToUnicodeMath(frac))

        // Özel 1/2 kesri
        val half = "\\frac{1}{2}"
        assertEquals("½", MathFormulaEngine.convertToUnicodeMath(half))

        // Karekök
        val sqrt = "\\sqrt{x^2 + y^2}"
        assertEquals("√(x² + y²)", MathFormulaEngine.convertToUnicodeMath(sqrt))

        // Küpkök
        val cuberoot = "\\sqrt[3]{8}"
        assertEquals("³√(8)", MathFormulaEngine.convertToUnicodeMath(cuberoot))
    }

    @Test
    fun convertToUnicodeMath_translatesSuperscriptsAndSubscripts() {
        val expr = "x_1^2 + x_2^2 = r^2"
        val unicode = MathFormulaEngine.convertToUnicodeMath(expr)

        assertEquals("x₁² + x₂² = r²", unicode)
    }

    @Test
    fun convertToUnicodeMath_translatesOperatorsAndCalculusSymbols() {
        val calc = "\\int_0^1 f(x) dx \\leq \\infty \\neq 0"
        val unicode = MathFormulaEngine.convertToUnicodeMath(calc)

        assertTrue(unicode.contains("∫"))
        assertTrue(unicode.contains("≤"))
        assertTrue(unicode.contains("∞"))
        assertTrue(unicode.contains("≠"))
    }

    @Test
    fun wrapInLatex_formatsInlineAndBlockProperly() {
        val formula = "x^2 + y^2 = z^2"
        val inline = MathFormulaEngine.wrapInLatex(formula, isBlock = false)
        val block = MathFormulaEngine.wrapInLatex(formula, isBlock = true)

        assertEquals("\$$formula\$", inline)
        assertTrue(block.startsWith("\$\$\n"))
        assertTrue(block.endsWith("\n\$\$"))
    }

    @Test
    fun templateLibrary_containsValidSTEMTemplates() {
        val templates = MathFormulaEngine.TEMPLATE_LIBRARY
        assertTrue(templates.size >= 10)

        val categories = templates.map { it.category }.distinct()
        assertTrue(categories.contains("Cebir"))
        assertTrue(categories.contains("Fizik"))
        assertTrue(categories.contains("Kalkülüs & Analiz"))

        for (tmpl in templates) {
            assertTrue(tmpl.title.isNotBlank())
            assertTrue(tmpl.latex.isNotBlank())
            assertTrue(tmpl.previewUnicode.isNotBlank())
        }
    }

    @Test
    fun extractMathExpressions_ignoresEscapedDollarsAndCurrency() {
        val text = "Bu ürünün fiyatı \$100 ve diğeri de \$200 tutuyor. Kaçışlı: \\\$50."
        val expressions = MathFormulaEngine.extractMathExpressions(text)
        assertEquals(0, expressions.size)
    }

    @Test
    fun convertToUnicodeMath_handlesWavelengthAndComplexFormulas() {
        val lambdaFormula = "\\lambda = \\frac{c}{f}"
        val unicode = MathFormulaEngine.convertToUnicodeMath(lambdaFormula)
        assertEquals("λ = c/f", unicode)

        val unbracedPowers = "x^2 + y^3 - z^n = 0"
        assertEquals("x² + y³ - zⁿ = 0", MathFormulaEngine.convertToUnicodeMath(unbracedPowers))
    }
}
