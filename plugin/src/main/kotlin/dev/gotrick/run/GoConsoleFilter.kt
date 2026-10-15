package dev.gotrick.run

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/** Understands both Windows absolute paths and Go's package-relative compiler messages. */
class GoConsoleFilter(private val project: Project, private val directory: String) : Filter {
    private val location = Regex("(?:^|\\s)([^\\r\\n]+?\\.go):(\\d+)(?::(\\d+))?:")
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = location.find(line) ?: return null
        val raw = match.groupValues[1].trim()
        val candidate = File(raw).let { if (it.isAbsolute) it else File(directory, raw) }
        val file = LocalFileSystem.getInstance().findFileByIoFile(candidate) ?: return null
        val start = line.indexOf(raw, match.range.first)
        val row = (match.groupValues[2].toIntOrNull() ?: 1) - 1
        val column = (match.groupValues[3].toIntOrNull() ?: 1) - 1
        return Filter.Result(entireLength - line.length + start, entireLength - line.length + match.range.last + 1,
            OpenFileHyperlinkInfo(project, file, row.coerceAtLeast(0), column.coerceAtLeast(0)))
    }
}
