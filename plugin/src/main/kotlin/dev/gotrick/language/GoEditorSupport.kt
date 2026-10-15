package dev.gotrick.language

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.lang.BracePair
import com.intellij.lang.Commenter
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class GoQuoteHandler : SimpleTokenSetQuoteHandler(GoTokens.STRINGS)

class GoCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "//"
    override fun getBlockCommentPrefix(): String = "/*"
    override fun getBlockCommentSuffix(): String = "*/"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}

class GoBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = arrayOf(
        BracePair(GoTokens.LEFT_BRACE, GoTokens.RIGHT_BRACE, true),
        BracePair(GoTokens.LEFT_PAREN, GoTokens.RIGHT_PAREN, false),
        BracePair(GoTokens.LEFT_BRACKET, GoTokens.RIGHT_BRACKET, false),
    )

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean =
        contextType == null || contextType == TokenType.WHITE_SPACE || GoTokens.COMMENTS.contains(contextType) ||
            contextType in setOf(GoTokens.RIGHT_BRACE, GoTokens.RIGHT_BRACKET, GoTokens.RIGHT_PAREN,
                GoTokens.COMMA, GoTokens.SEMICOLON)

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = openingBraceOffset
}
