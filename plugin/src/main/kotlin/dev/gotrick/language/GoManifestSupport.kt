package dev.gotrick.language

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.Commenter
import com.intellij.lang.Language
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import javax.swing.Icon

object GoModLanguage : Language("GoTrickGoMod")
object GoWorkLanguage : Language("GoTrickGoWork")

object GoModFileType : LanguageFileType(GoModLanguage) {
    override fun getName(): String = "GoTrick Go Module"
    override fun getDescription(): String = "Go module definition"
    override fun getDefaultExtension(): String = "mod"
    override fun getIcon(): Icon = GoFileType.icon
}

object GoWorkFileType : LanguageFileType(GoWorkLanguage) {
    override fun getName(): String = "GoTrick Go Workspace"
    override fun getDescription(): String = "Go workspace definition"
    override fun getDefaultExtension(): String = "work"
    override fun getIcon(): Icon = GoFileType.icon
}

/** go.mod and go.work have line-oriented, whitespace-delimited syntax, not Go expressions. */
class GoManifestLexer : LexerBase() {
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
    override fun advance() { start = end; scan() }

    private fun scan() {
        if (start >= limit) { type = null; end = limit; return }
        end = start + 1
        val c = source[start]
        type = when {
            c.isWhitespace() -> {
                while (end < limit && source[end].isWhitespace()) end++
                TokenType.WHITE_SPACE
            }
            isCommentStart(start) -> {
                while (end < limit && source[end] != '\n' && source[end] != '\r') end++
                GoTokens.LINE_COMMENT
            }
            c == '"' || c == '`' -> {
                while (end < limit) {
                    val next = source[end]
                    if (next == '\n' || next == '\r') break
                    end++
                    if (next == c) break
                    if (c == '"' && next == '\\' && end < limit && source[end] != '\n' && source[end] != '\r') end++
                }
                GoTokens.STRING
            }
            c == '(' -> GoTokens.LEFT_PAREN
            c == ')' -> GoTokens.RIGHT_PAREN
            c == '=' && end < limit && source[end] == '>' -> { end++; GoTokens.OPERATOR }
            else -> {
                while (end < limit && !source[end].isWhitespace() && source[end] != '(' && source[end] != ')' && !isCommentStart(end)) end++
                val word = source.subSequence(start, end).toString()
                when {
                    word in DIRECTIVES -> GoTokens.KEYWORD
                    word.first() in '0'..'9' || (word.startsWith('v') && word.getOrNull(1)?.let { it in '0'..'9' } == true) -> GoTokens.NUMBER
                    else -> GoTokens.IDENTIFIER
                }
            }
        }
    }

    private fun isCommentStart(offset: Int) = offset + 1 < limit && source[offset] == '/' && source[offset + 1] == '/'

    companion object {
        private val DIRECTIVES = setOf("module", "go", "toolchain", "require", "exclude", "replace", "retract", "use", "tool", "godebug", "ignore")
    }
}

abstract class GoManifestParserDefinition(
    private val manifestLanguage: Language,
    private val manifestFileType: LanguageFileType,
) : ParserDefinition {
    private val fileType = IFileElementType(manifestLanguage)
    override fun createLexer(project: Project?): Lexer = GoManifestLexer()
    override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
        val file = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        file.done(root)
        builder.treeBuilt
    }
    override fun getFileNodeType(): IFileElementType = fileType
    override fun getWhitespaceTokens(): TokenSet = TokenSet.WHITE_SPACE
    override fun getCommentTokens(): TokenSet = GoTokens.COMMENTS
    override fun getStringLiteralElements(): TokenSet = GoTokens.STRINGS
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile =
        object : PsiFileBase(viewProvider, manifestLanguage) {
            override fun getFileType(): FileType = manifestFileType
        }
}

class GoModParserDefinition : GoManifestParserDefinition(GoModLanguage, GoModFileType)
class GoWorkParserDefinition : GoManifestParserDefinition(GoWorkLanguage, GoWorkFileType)

class GoManifestSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        object : GoSyntaxHighlighter() {
            override fun getHighlightingLexer(): Lexer = GoManifestLexer()
        }
}

class GoManifestCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "//"
    override fun getBlockCommentPrefix(): String? = null
    override fun getBlockCommentSuffix(): String? = null
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
