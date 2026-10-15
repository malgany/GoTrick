package dev.gotrick.language

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/** Token-level PSI hosts editor and LSP integrations; it does not claim a Go semantic tree. */
class GoParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = GoLexer()
    override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
        val file = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        file.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType(): IFileElementType = GoTokens.FILE
    override fun getWhitespaceTokens(): TokenSet = TokenSet.WHITE_SPACE
    override fun getCommentTokens(): TokenSet = GoTokens.COMMENTS
    override fun getStringLiteralElements(): TokenSet = GoTokens.STRINGS
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = GoPsiFile(viewProvider)
}

class GoPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, GoTrickLanguage) {
    override fun getFileType(): FileType = GoFileType
    override fun toString(): String = "Go source file"
}
