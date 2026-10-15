package dev.gotrick.run

import com.intellij.util.execution.ParametersListUtil

enum class GoOperation(val command: String, val label: String) {
    RUN("run", "Run package"), BUILD("build", "Build"), TEST("test", "Test"),
    VET("vet", "Vet"), TIDY("mod", "Tidy modules");
    override fun toString() = label
}

/** Builds an argument vector, never a shell command. */
data class GoCommandSpec(
    val operation: GoOperation,
    val packages: String = ".",
    val goFlags: String = "",
    val programArguments: String = "",
    val testName: String = "",
) {
    fun arguments(): List<String> = buildList {
        add(operation.command)
        if (operation == GoOperation.TIDY) {
            add("tidy")
            addAll(ParametersListUtil.parse(goFlags))
            return@buildList
        }
        addAll(ParametersListUtil.parse(goFlags))
        if (operation == GoOperation.TEST && testName.isNotBlank()) {
            if (testName.startsWith("Benchmark")) {
                add("-run"); add("^$"); add("-bench"); add("^${Regex.escape(testName)}$")
            } else {
                add("-run"); add("^${Regex.escape(testName)}$")
            }
        }
        addAll(ParametersListUtil.parse(packages.ifBlank { "." }))
        if (programArguments.isNotBlank() && (operation == GoOperation.RUN || operation == GoOperation.TEST)) {
            if (operation == GoOperation.TEST) add("-args")
            addAll(ParametersListUtil.parse(programArguments))
        }
    }

    companion object {
        fun parseEnvironment(text: String): Map<String, String> = buildMap {
            for ((index, raw) in text.lineSequence().withIndex()) {
                if (raw.isBlank()) continue
                val line = raw.trimStart()
                val separator = line.indexOf('=')
                require(separator > 0) { "Environment line ${index + 1} must be NAME=value." }
                val name = line.substring(0, separator).trim()
                require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Invalid environment variable name: $name" }
                put(name, line.substring(separator + 1))
            }
        }
    }
}
