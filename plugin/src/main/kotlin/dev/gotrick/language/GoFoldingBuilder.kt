package dev.gotrick.language

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType

/** Immediate lexical folding remains available before the language server has started. */
class GoFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val folds = mutableListOf<FoldingDescriptor>()
        val stack = ArrayDeque<Pair<IElementType, Int>>()
        val lexer = GoLexer()
        lexer.start(document.charsSequence)
        while (lexer.tokenType != null) {
            ProgressManager.checkCanceled()
            val type = lexer.tokenType!!
            val start = lexer.tokenStart
            val end = lexer.tokenEnd
            when (type) {
                GoTokens.LEFT_BRACE, GoTokens.LEFT_PAREN, GoTokens.LEFT_BRACKET -> stack.addLast(type to start)
                GoTokens.RIGHT_BRACE, GoTokens.RIGHT_PAREN, GoTokens.RIGHT_BRACKET -> {
                    val expected = when (type) {
                        GoTokens.RIGHT_BRACE -> GoTokens.LEFT_BRACE
                        GoTokens.RIGHT_PAREN -> GoTokens.LEFT_PAREN
                        else -> GoTokens.LEFT_BRACKET
                    }
                    // Do not fold across malformed, mismatched delimiters while a user edits.
                    if (stack.lastOrNull()?.first == expected) {
                        val opening = stack.removeLast().second
                        addMultiline(folds, root, document, opening, end)
                    } else stack.clear()
                }
                GoTokens.BLOCK_COMMENT, GoTokens.RAW_STRING -> addMultiline(folds, root, document, start, end)
            }
            lexer.advance()
        }
        return folds.toTypedArray()
    }

    private fun addMultiline(
        folds: MutableList<FoldingDescriptor>, root: PsiElement, document: Document, start: Int, end: Int,
    ) {
        if (end - start <= 2 || document.getLineNumber(start) == document.getLineNumber(end - 1)) return
        val node = root.findElementAt(start)?.node ?: return
        folds.add(FoldingDescriptor(node, TextRange(start, end)))
    }

    override fun getPlaceholderText(node: ASTNode): String = when (node.elementType) {
        GoTokens.LEFT_BRACE -> "{…}"
        GoTokens.LEFT_PAREN -> "(…)"
        GoTokens.LEFT_BRACKET -> "[…]"
        GoTokens.BLOCK_COMMENT -> "/* … */"
        GoTokens.RAW_STRING -> "`…`"
        else -> "…"
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
