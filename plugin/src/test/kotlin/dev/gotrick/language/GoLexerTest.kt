package dev.gotrick.language

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GoLexerTest {
    private data class Token(val type: IElementType, val text: String, val start: Int, val end: Int)

    private fun tokens(source: String, start: Int = 0, limit: Int = source.length): List<Token> {
        val lexer = GoLexer()
        lexer.start(source, start, limit, 0)
        val result = mutableListOf<Token>()
        while (lexer.tokenType != null) {
            assertTrue("Lexer must always advance at ${lexer.tokenStart}", lexer.tokenEnd > lexer.tokenStart)
            assertTrue("Lexer exceeded the supplied end offset", lexer.tokenEnd <= limit)
            result.add(Token(lexer.tokenType!!, source.substring(lexer.tokenStart, lexer.tokenEnd), lexer.tokenStart, lexer.tokenEnd))
            lexer.advance()
        }
        return result
    }

    private fun nonSpace(source: String) = tokens(source).filter { it.type != TokenType.WHITE_SPACE }

    @Test fun recognizesKeywordsWithoutTreatingPredeclaredNamesAsReserved() {
        val lexed = nonSpace("package main func if range type true nil any comparable println")
        assertEquals(listOf(GoTokens.KEYWORD, GoTokens.IDENTIFIER, GoTokens.KEYWORD, GoTokens.KEYWORD,
            GoTokens.KEYWORD, GoTokens.KEYWORD, GoTokens.IDENTIFIER, GoTokens.IDENTIFIER,
            GoTokens.IDENTIFIER, GoTokens.IDENTIFIER, GoTokens.IDENTIFIER), lexed.map { it.type })
    }

    @Test fun acceptsUnicodeLettersAndDigitsIncludingSupplementaryLetters() {
        val names = listOf("π", "世界", "𐐀name", "a١", "_private", "é")
        val lexed = nonSpace(names.joinToString(" "))
        assertEquals(names, lexed.map { it.text })
        assertTrue(lexed.all { it.type == GoTokens.IDENTIFIER })
    }

    @Test fun doesNotAcceptCombiningMarksOrDigitsAsIdentifierStart() {
        val lexed = nonSpace("e\u0301 ١name")
        assertEquals(listOf(GoTokens.IDENTIFIER, TokenType.BAD_CHARACTER, TokenType.BAD_CHARACTER,
            GoTokens.IDENTIFIER), lexed.map { it.type })
    }

    @Test fun stringsAndRunesHideCommentMarkersAndDelimiters() {
        val literals = listOf("\"// { /* */ \\\"text\\\"\"", "'\\''", "'界'", "`first\n// { }\nlast`")
        val lexed = nonSpace(literals.joinToString(" "))
        assertEquals(literals, lexed.map { it.text })
        assertEquals(listOf(GoTokens.STRING, GoTokens.RUNE, GoTokens.RUNE, GoTokens.RAW_STRING), lexed.map { it.type })
    }

    @Test fun unfinishedInterpretedStringStopsBeforeTheNextLine() {
        val lexed = nonSpace("\"unfinished\\\nvar value = 1")
        assertEquals("\"unfinished\\", lexed.first().text)
        assertEquals(GoTokens.KEYWORD, lexed[1].type)
        assertEquals("var", lexed[1].text)
    }

    @Test fun unfinishedRawStringAndBlockCommentConsumeToEndOfFile() {
        for ((source, expected) in listOf("`unfinished\ntext" to GoTokens.RAW_STRING, "/* unfinished\ncomment" to GoTokens.BLOCK_COMMENT)) {
            val lexed = tokens(source)
            assertEquals(1, lexed.size)
            assertEquals(expected, lexed.single().type)
            assertEquals(source, lexed.single().text)
        }
    }

    @Test fun blockCommentsStopAtTheFirstClosingDelimiter() {
        val lexed = nonSpace("/* one /* nested-looking */ var n = 1 // end\r\nfunc")
        assertEquals("/* one /* nested-looking */", lexed.first().text)
        assertEquals(GoTokens.KEYWORD, lexed[1].type)
        assertEquals(GoTokens.LINE_COMMENT, lexed[5].type)
        assertEquals("func", lexed.last().text)
    }

    @Test fun recognizesGoNumberFormsAsSingleTokens() {
        val numbers = listOf("42", "0_600", "0b_1010", "0o755", "0x_FF", "1_000.25", "1e-3", "1E+6",
            "0x1.fp+2", "0X.8P-1", "2i", "3.2i", ".5i", "0x1p-1i", "1.")
        val lexed = nonSpace(numbers.joinToString(" "))
        assertEquals(numbers, lexed.map { it.text })
        assertTrue(lexed.all { it.type == GoTokens.NUMBER })
    }

    @Test fun numericExponentsDoNotConsumeFollowingArithmetic() {
        val lexed = nonSpace("0x1e+2 1e-3-4 7+8")
        assertEquals(listOf("0x1e", "+", "2", "1e-3", "-", "4", "7", "+", "8"), lexed.map { it.text })
    }

    @Test fun choosesLongestOperators() {
        val operators = listOf("<<=", ">>=", "&^=", "...", ":=", "<-", "&&", "||", "++", "--", "==", "!=", "<=", ">=", "~")
        val lexed = nonSpace(operators.joinToString(" "))
        assertEquals(operators, lexed.map { it.text })
        assertTrue(lexed.all { it.type == GoTokens.OPERATOR })
    }

    @Test fun distinguishesDelimitersAndDot() {
        assertEquals(listOf(GoTokens.LEFT_BRACE, GoTokens.RIGHT_BRACE, GoTokens.LEFT_PAREN,
            GoTokens.RIGHT_PAREN, GoTokens.LEFT_BRACKET, GoTokens.RIGHT_BRACKET, GoTokens.COMMA,
            GoTokens.SEMICOLON, GoTokens.DOT), nonSpace("{}()[],;.").map { it.type })
    }

    @Test fun honorsSubrangeAndSurrogateBoundary() {
        val source = "prefix π = 𐐀name suffix"
        val start = source.indexOf('π')
        val limit = source.indexOf(" suffix")
        val lexed = tokens(source, start, limit)
        assertEquals("π = 𐐀name", lexed.joinToString("") { it.text })
        assertEquals(start, lexed.first().start)
        assertEquals(limit, lexed.last().end)
        val clipped = tokens("𐐀", 0, 1)
        assertEquals(1, clipped.single().end)
    }

    @Test fun incrementalRestartAtEveryTokenBoundaryMatchesFullLexing() {
        val source = "package π\n/* block\ncomment */ var s = `raw\nstring`; var r = '\\''; n := 0x1.fp+2\n"
        val original = tokens(source)
        for (index in original.indices) {
            assertEquals("Restart at ${original[index].start}", original.drop(index), tokens(source, original[index].start))
        }
    }

    @Test fun lexerPreservesEveryInputCharacterAndTerminatesOnMalformedInput() {
        val alphabet = "abcπ世界01239_\n\r\t{}[]()/*`'\"\\.+-:$\u0000\uD800\uDC00"
        val random = Random(147)
        repeat(100) {
            val source = buildString { repeat(150) { append(alphabet[random.nextInt(alphabet.length)]) } }
            val lexed = tokens(source)
            assertEquals(source, lexed.joinToString("") { it.text })
            lexed.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
        }
    }

    @Test fun acceptsInitialBomButDoesNotTreatMidFileBomAsWhitespace() {
        val lexed = tokens("\uFEFFpackage\uFEFF")
        assertEquals(TokenType.WHITE_SPACE, lexed.first().type)
        assertEquals(TokenType.BAD_CHARACTER, lexed.last().type)
    }
}
