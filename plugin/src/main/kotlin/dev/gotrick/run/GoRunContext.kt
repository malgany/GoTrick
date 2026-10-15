package dev.gotrick.run

/** Context inference uses source declarations and needs no proprietary Go PSI. */
data class GoRunContext(val operation: GoOperation, val function: String = "") {
    companion object {
        private val functionDeclaration = Regex("(?m)^\\s*func\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
        private val testFunction = Regex("(?:Test|Benchmark|Fuzz)(?:[A-Z_0-9][A-Za-z0-9_]*)?|Example[A-Za-z0-9_]*")

        fun infer(fileName: String, source: String, offset: Int): GoRunContext? {
            if (!fileName.endsWith(".go")) return null
            val clean = maskCommentsAndStrings(source)
            if (fileName.endsWith("_test.go")) {
                val declaration = functionDeclaration.findAll(clean).lastOrNull { it.range.first <= offset }
                val name = declaration?.groupValues?.get(1).orEmpty()
                return GoRunContext(GoOperation.TEST, name.takeIf { testFunction.matches(it) }.orEmpty())
            }
            return if (Regex("(?m)^\\s*package\\s+main\\b").containsMatchIn(clean)) GoRunContext(GoOperation.RUN)
                else GoRunContext(GoOperation.TEST)
        }

        fun isRunnableFunction(fileName: String, name: String): Boolean =
            (name == "main" && !fileName.endsWith("_test.go")) ||
                (fileName.endsWith("_test.go") && testFunction.matches(name))

        /** Preserve offsets/newlines so declaration matching ignores comments and literal contents. */
        internal fun maskCommentsAndStrings(source: String): String {
            val chars = source.toCharArray()
            var i = 0
            while (i < chars.size) {
                val start = i
                val quote = source[i]
                if (quote == '/' && source.getOrNull(i + 1) == '/') {
                    i += 2
                    while (i < source.length && source[i] != '\n') i++
                } else if (quote == '/' && source.getOrNull(i + 1) == '*') {
                    i += 2
                    while (i < source.length && !(source[i] == '*' && source.getOrNull(i + 1) == '/')) i++
                    i = (i + 2).coerceAtMost(source.length)
                } else if (quote == '"' || quote == '\'' || quote == '`') {
                    i++
                    while (i < source.length) {
                        if (source[i] == quote) { i++; break }
                        if (source[i] == '\\' && quote != '`') i++
                        i++
                    }
                    i = i.coerceAtMost(source.length)
                } else { i++; continue }
                for (index in start until i) if (chars[index] != '\n' && chars[index] != '\r') chars[index] = ' '
            }
            return String(chars)
        }
    }
}
