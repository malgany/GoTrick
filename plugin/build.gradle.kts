import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

group = "dev.gotrick"
version = rootProject.file("VERSION").readText().trim()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        local(rootProject.file(".tools/idea"))
        localPlugin(rootProject.file(".tools/lsp4ij"))
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin { jvmToolchain(21) }

intellijPlatform {
    pluginConfiguration {
        id = "dev.gotrick"
        name = "GoTrick"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "252"
            untilBuild = "252.*"
        }
        vendor { name = "GoTrick" }
    }
    buildSearchableOptions = false
}

tasks.test {
    useJUnit()
    systemProperty("gotrick.tools", rootProject.file(".tools").absolutePath)
    systemProperty("java.awt.headless", "true")
    maxHeapSize = "1g"
    testLogging { events("passed", "skipped", "failed") }
}

tasks.runIde {
    systemProperty("gotrick.tools", rootProject.file(".tools").absolutePath)
    systemProperty("idea.config.path", rootProject.file(".runtime/config").absolutePath)
    systemProperty("idea.system.path", rootProject.file(".runtime/system").absolutePath)
    systemProperty("idea.log.path", rootProject.file(".runtime/log").absolutePath)
}
