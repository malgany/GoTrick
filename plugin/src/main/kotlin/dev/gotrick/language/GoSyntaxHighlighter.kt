package dev.gotrick.language

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import javax.swing.Icon

open class GoSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = GoLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = pack(when (tokenType) {
        GoTokens.KEYWORD -> KEYWORD
        GoTokens.NUMBER -> NUMBER
        GoTokens.STRING, GoTokens.RAW_STRING, GoTokens.RUNE -> STRING
        GoTokens.LINE_COMMENT -> LINE_COMMENT
        GoTokens.BLOCK_COMMENT -> BLOCK_COMMENT
        GoTokens.OPERATOR -> OPERATOR
        GoTokens.LEFT_BRACE, GoTokens.RIGHT_BRACE -> BRACES
        GoTokens.LEFT_PAREN, GoTokens.RIGHT_PAREN -> PARENTHESES
        GoTokens.LEFT_BRACKET, GoTokens.RIGHT_BRACKET -> BRACKETS
        GoTokens.COMMA -> COMMA
        GoTokens.SEMICOLON -> SEMICOLON
        GoTokens.DOT -> DOT
        TokenType.BAD_CHARACTER -> BAD_CHARACTER
        else -> null
    })

    companion object {
        private fun key(name: String, fallback: TextAttributesKey) =
            TextAttributesKey.createTextAttributesKey("GOTRICK_GO_$name", fallback)

        val KEYWORD = key("KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val NUMBER = key("NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val STRING = key("STRING", DefaultLanguageHighlighterColors.STRING)
        val LINE_COMMENT = key("LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val BLOCK_COMMENT = key("BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
        val OPERATOR = key("OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val BRACES = key("BRACES", DefaultLanguageHighlighterColors.BRACES)
        val PARENTHESES = key("PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
        val BRACKETS = key("BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
        val COMMA = key("COMMA", DefaultLanguageHighlighterColors.COMMA)
        val SEMICOLON = key("SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)
        val DOT = key("DOT", DefaultLanguageHighlighterColors.DOT)
        val BAD_CHARACTER = key("BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
    }
}

class GoSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = GoSyntaxHighlighter()
}

class GoColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName(): String = "GoTrick Go"
    override fun getIcon(): Icon = GoFileType.icon
    override fun getHighlighter(): SyntaxHighlighter = GoSyntaxHighlighter()
    override fun getDemoText(): String = """
        package main

        import "fmt"

        // Print a message and count the runes.
        func main() {
            greeting := "Hello, GoTrick!"
            letters := []rune(greeting)
            for index, letter := range letters {
                fmt.Printf("%d: %c\n", index, letter)
            }
            /* Hexadecimal floating-point constant */
            scale := 0x1.fp+2
            fmt.Println(scale, '界')
        }
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = arrayOf(
        AttributesDescriptor("Keywords", GoSyntaxHighlighter.KEYWORD),
        AttributesDescriptor("Numbers", GoSyntaxHighlighter.NUMBER),
        AttributesDescriptor("Strings and runes", GoSyntaxHighlighter.STRING),
        AttributesDescriptor("Line comments", GoSyntaxHighlighter.LINE_COMMENT),
        AttributesDescriptor("Block comments", GoSyntaxHighlighter.BLOCK_COMMENT),
        AttributesDescriptor("Operators", GoSyntaxHighlighter.OPERATOR),
        AttributesDescriptor("Braces", GoSyntaxHighlighter.BRACES),
        AttributesDescriptor("Parentheses", GoSyntaxHighlighter.PARENTHESES),
        AttributesDescriptor("Brackets", GoSyntaxHighlighter.BRACKETS),
        AttributesDescriptor("Commas", GoSyntaxHighlighter.COMMA),
        AttributesDescriptor("Semicolons", GoSyntaxHighlighter.SEMICOLON),
        AttributesDescriptor("Dots", GoSyntaxHighlighter.DOT),
        AttributesDescriptor("Invalid characters", GoSyntaxHighlighter.BAD_CHARACTER),
    )
}
