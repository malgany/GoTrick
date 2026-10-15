package dev.gotrick.language

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * A lossless editor lexer based on the lexical elements in https://go.dev/ref/spec.
 * Complete comments and literals are single tokens so restarting at a token boundary
 * is context independent. Invalid/incomplete literals remain editable; gopls supplies
 * diagnostics. This lexer intentionally does not synthesize automatic semicolons.
 */
class GoLexer : LexerBase() {
    private var source: CharSequence = ""
    private var limit = 0
    private var start = 0
    private var end = 0
    private var type: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        source = buffer
        limit = endOffset
        start = startOffset
        end = startOffset
        scan()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = type
    override fun getTokenStart(): Int = start
    override fun getTokenEnd(): Int = end
    override fun getBufferSequence(): CharSequence = source
    override fun getBufferEnd(): Int = limit

    override fun advance() {
        start = end
        scan()
    }

    private fun scan() {
        if (start >= limit) {
            type = null
            end = limit
            return
        }
        end = start + 1
        val first = source[start]
        type = when {
            isSpace(first) || (start == 0 && first == '\uFEFF') -> {
                while (end < limit && isSpace(source[end])) end++
                TokenType.WHITE_SPACE
            }
            first == '/' && peek(start + 1) == '/' -> {
                end = start + 2
                while (end < limit && source[end] != '\n' && source[end] != '\r') end++
                GoTokens.LINE_COMMENT
            }
            first == '/' && peek(start + 1) == '*' -> {
                end = start + 2
                while (end < limit) {
                    if (source[end] == '*' && peek(end + 1) == '/') {
                        end += 2
                        break
                    }
                    end++
                }
                GoTokens.BLOCK_COMMENT
            }
            first == '`' -> {
                while (end < limit && source[end] != '`') end++
                if (end < limit) end++
                GoTokens.RAW_STRING
            }
            first == '"' || first == '\'' -> {
                scanQuoted(first)
                if (first == '"') GoTokens.STRING else GoTokens.RUNE
            }
            isIdentifierStart(codePointAt(start)) -> {
                end = start + Character.charCount(codePointAt(start))
                while (end < limit) {
                    val cp = codePointAt(end)
                    if (!isIdentifierStart(cp) && !Character.isDigit(cp)) break
                    end += Character.charCount(cp)
                }
                if (source.subSequence(start, end).toString() in KEYWORDS) GoTokens.KEYWORD
                else GoTokens.IDENTIFIER
            }
            first in '0'..'9' || (first == '.' && peek(start + 1) in '0'..'9') -> {
                scanNumber()
                GoTokens.NUMBER
            }
            else -> scanPunctuation(first)
        }
    }

    private fun scanQuoted(quote: Char) {
        while (end < limit) {
            val current = source[end]
            // A newline ends an unfinished interpreted literal; do not color the next line.
            if (current == '\n' || current == '\r') break
            end++
            if (current == quote) break
            if (current == '\\' && end < limit && source[end] != '\n' && source[end] != '\r') end++
        }
    }

    private fun scanNumber() {
        end = start
        val hexadecimal = peek(start) == '0' && (peek(start + 1) == 'x' || peek(start + 1) == 'X')
        val binary = peek(start) == '0' && (peek(start + 1) == 'b' || peek(start + 1) == 'B')
        val octal = peek(start) == '0' && (peek(start + 1) == 'o' || peek(start + 1) == 'O')
        if (hexadecimal || binary || octal) end += 2
        val radix = when {
            hexadecimal -> 16
            binary -> 2
            octal -> 8
            else -> 10
        }
        scanDigits(radix)
        if (!binary && !octal && peek(end) == '.') {
            end++
            scanDigits(radix)
        }
        val exponent = peek(end)
        if ((hexadecimal && (exponent == 'p' || exponent == 'P')) ||
            (!hexadecimal && !binary && !octal && (exponent == 'e' || exponent == 'E'))) {
            end++
            if (peek(end) == '+' || peek(end) == '-') end++
            scanDigits(10)
        }
        if (peek(end) == 'i') end++
    }

    private fun scanDigits(radix: Int) {
        while (end < limit) {
            val c = source[end]
            val digit = when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                in 'A'..'F' -> c - 'A' + 10
                else -> -1
            }
            if (c != '_' && (digit < 0 || digit >= radix)) break
            end++
        }
    }

    private fun scanPunctuation(c: Char): IElementType {
        val punctuation = when (c) {
            '{' -> GoTokens.LEFT_BRACE
            '}' -> GoTokens.RIGHT_BRACE
            '(' -> GoTokens.LEFT_PAREN
            ')' -> GoTokens.RIGHT_PAREN
            '[' -> GoTokens.LEFT_BRACKET
            ']' -> GoTokens.RIGHT_BRACKET
            ',' -> GoTokens.COMMA
            ';' -> GoTokens.SEMICOLON
            else -> null
        }
        if (punctuation != null) return punctuation
        for (operator in MULTI_OPERATORS) {
            if (matches(operator)) {
                end = start + operator.length
                return GoTokens.OPERATOR
            }
        }
        if (c == '.') return GoTokens.DOT
        if (c in "+-*/%&|^<>=!~:") return GoTokens.OPERATOR
        // Consume one Unicode code point, never half a valid surrogate pair.
        end = start + Character.charCount(codePointAt(start))
        return TokenType.BAD_CHARACTER
    }

    private fun matches(value: String): Boolean {
        if (start + value.length > limit) return false
        return value.indices.all { source[start + it] == value[it] }
    }

    private fun peek(index: Int): Char = if (index < limit) source[index] else '\u0000'

    private fun codePointAt(index: Int): Int {
        val c = source[index]
        return if (Character.isHighSurrogate(c) && index + 1 < limit && Character.isLowSurrogate(source[index + 1])) {
            Character.toCodePoint(c, source[index + 1])
        } else c.code
    }

    private fun isSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\r' || c == '\n'
    private fun isIdentifierStart(cp: Int): Boolean = cp == '_'.code || Character.isLetter(cp)

    companion object {
        private val KEYWORDS = setOf(
            "break", "default", "func", "interface", "select", "case", "defer", "go", "map", "struct",
            "chan", "else", "goto", "package", "switch", "const", "fallthrough", "if", "range", "type",
            "continue", "for", "import", "return", "var",
        )
        private val MULTI_OPERATORS = listOf(
            "<<=", ">>=", "&^=", "...", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
            "&&", "||", "<-", "++", "--", "==", "!=", "<=", ">=", ":=", "<<", ">>", "&^",
        )
    }
}
