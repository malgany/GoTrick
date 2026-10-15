package dev.gotrick.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

class GoRunConfigurationType : ConfigurationType, DumbAware {
    private val factory = object : ConfigurationFactory(this) {
        override fun getId() = "GoTrickGoFactory"
        override fun createTemplateConfiguration(project: Project): RunConfiguration =
            GoRunConfiguration(project, this, "Go")
        override fun isEditableInDumbMode() = true
    }
    override fun getId() = "GoTrickGo"
    override fun getDisplayName() = "Go"
    override fun getConfigurationTypeDescription() = "Run, build, test and inspect Go packages"
    override fun getIcon() = AllIcons.RunConfigurations.Application
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)

    companion object {
        @JvmStatic fun getInstance(): GoRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(GoRunConfigurationType::class.java)
    }
}
