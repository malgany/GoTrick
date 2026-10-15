package dev.gotrick.language

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object GoTrickLanguage : Language("GoTrickGo") {
    override fun getDisplayName(): String = "Go"
}

object GoFileType : LanguageFileType(GoTrickLanguage) {
    override fun getName(): String = "GoTrick Go"
    override fun getDescription(): String = "Go source file"
    override fun getDefaultExtension(): String = "go"
    override fun getIcon(): Icon = IconLoader.getIcon("/icons/goTrickGo.svg", javaClass)
}
