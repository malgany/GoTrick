package dev.gotrick.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

class GoRunConfigurationProducer : LazyRunConfigurationProducer<GoRunConfiguration>(), DumbAware {
    override fun getConfigurationFactory(): ConfigurationFactory = GoRunConfigurationType.getInstance().configurationFactories[0]

    override fun setupConfigurationFromContext(configuration: GoRunConfiguration, context: ConfigurationContext,
        sourceElement: Ref<PsiElement>): Boolean {
        val element = context.psiLocation ?: return false
        val file = element.containingFile ?: return false
        val virtual = file.virtualFile ?: return false
        val detected = GoRunContext.infer(file.name, file.text, element.textOffset) ?: return false
        configuration.operation = detected.operation
        configuration.workingDirectory = virtual.parent.path
        configuration.packages = "."
        configuration.goFlags = if (detected.operation == GoOperation.TEST) "-v" else ""
        configuration.testName = detected.function
        configuration.name = if (detected.function.isNotBlank()) detected.function
            else "${detected.operation.label}: ${virtual.parent.name}"
        sourceElement.set(element)
        return true
    }

    override fun isConfigurationFromContext(configuration: GoRunConfiguration, context: ConfigurationContext): Boolean {
        val element = context.psiLocation ?: return false
        val file = element.containingFile ?: return false
        val virtual = file.virtualFile ?: return false
        val detected = GoRunContext.infer(file.name, file.text, element.textOffset) ?: return false
        return configuration.operation == detected.operation && configuration.testName == detected.function &&
            configuration.packages == "." && configuration.resolvedWorkingDirectory().replace('\\', '/') == virtual.parent.path
    }
}
