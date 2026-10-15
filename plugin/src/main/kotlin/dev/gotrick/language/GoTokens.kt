package dev.gotrick.language

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/** Lexical token types only. Semantic analysis and syntax diagnostics belong to gopls. */
object GoTokens {
    private fun token(name: String) = IElementType(name, GoTrickLanguage)

    val FILE = IFileElementType(GoTrickLanguage)
    val IDENTIFIER = token("IDENTIFIER")
    val KEYWORD = token("KEYWORD")
    val NUMBER = token("NUMBER")
    val STRING = token("STRING")
    val RAW_STRING = token("RAW_STRING")
    val RUNE = token("RUNE")
    val LINE_COMMENT = token("LINE_COMMENT")
    val BLOCK_COMMENT = token("BLOCK_COMMENT")
    val OPERATOR = token("OPERATOR")
    val LEFT_BRACE = token("LEFT_BRACE")
    val RIGHT_BRACE = token("RIGHT_BRACE")
    val LEFT_PAREN = token("LEFT_PAREN")
    val RIGHT_PAREN = token("RIGHT_PAREN")
    val LEFT_BRACKET = token("LEFT_BRACKET")
    val RIGHT_BRACKET = token("RIGHT_BRACKET")
    val COMMA = token("COMMA")
    val SEMICOLON = token("SEMICOLON")
    val DOT = token("DOT")

    val COMMENTS: TokenSet = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT)
    val STRINGS: TokenSet = TokenSet.create(STRING, RAW_STRING, RUNE)
}
