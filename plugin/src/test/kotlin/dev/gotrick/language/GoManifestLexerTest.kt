package dev.gotrick.language

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Test

class GoManifestLexerTest {
    private fun tokens(source: String): List<Pair<IElementType, String>> {
        val result = mutableListOf<Pair<IElementType, String>>()
        val lexer = GoManifestLexer()
        lexer.start(source)
        while (lexer.tokenType != null) {
            if (lexer.tokenType != TokenType.WHITE_SPACE) {
                result.add(lexer.tokenType!! to source.substring(lexer.tokenStart, lexer.tokenEnd))
            }
            lexer.advance()
        }
        return result
    }

    @Test fun keepsModulePathsAndVersionsWhole() {
        val lexed = tokens("module example.com/my-project\ngo 1.25.0\nrequire github.com/acme/pkg v1.2.3 // indirect")
        assertEquals(listOf("module", "example.com/my-project", "go", "1.25.0", "require", "github.com/acme/pkg", "v1.2.3", "// indirect"), lexed.map { it.second })
        assertEquals(GoTokens.KEYWORD, lexed[0].first)
        assertEquals(GoTokens.NUMBER, lexed[3].first)
        assertEquals(GoTokens.NUMBER, lexed[6].first)
        assertEquals(GoTokens.LINE_COMMENT, lexed[7].first)
    }

    @Test fun handlesGroupedWorkspacePathsAndQuotedPaths() {
        val lexed = tokens("use (\n ./api\n \"./shared components\"\n)\nreplace example.com/a => ../a")
        assertEquals(listOf("use", "(", "./api", "\"./shared components\"", ")", "replace", "example.com/a", "=>", "../a"), lexed.map { it.second })
        assertEquals(GoTokens.STRING, lexed[3].first)
        assertEquals(GoTokens.OPERATOR, lexed[7].first)
    }
}
