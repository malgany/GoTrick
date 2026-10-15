package dev.gotrick.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement

class GoRunLineMarkerContributor : RunLineMarkerContributor(), DumbAware {
    override fun getInfo(element: PsiElement): Info? {
        if (element.firstChild != null) return null
        val file = element.containingFile ?: return null
        val name = element.text
        if (!GoRunContext.isRunnableFunction(file.name, name)) return null
        val text = file.text
        val offset = element.textOffset
        val lineStart = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)) + 1
        if (!text.substring(lineStart, offset).matches(Regex("\\s*func\\s+"))) return null
        return Info(AllIcons.Actions.Execute, ExecutorAction.getActions(0))
    }
}
