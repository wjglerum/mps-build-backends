package de.itemis.mps.gradle.launcher

import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.process.CommandLineArgumentProvider
import java.io.File
import java.io.Serializable
import java.util.Properties
import javax.inject.Inject

open class MpsBackendLauncher @Inject constructor(
    private val javaToolchainService: JavaToolchainService,
    private val providers: ProviderFactory) {

    fun configureJavaForMpsVersion(javaExec: JavaExec, mpsHome: File, mpsVersion: String) {
        configureJavaForMpsVersion(javaExec,
            providers.provider { mpsHome },
            providers.provider { mpsVersion })
    }

    /**
     * Retrieves the MPS platform version from `$mpsHome/build.properties`, property `mps.build.number`.
     */
    fun mpsVersionFromMpsHome(mpsHome: Provider<Directory>): Provider<String> {
        val buildPropertiesFile = mpsHome.map { it.file("build.properties") }
        val fileContents = providers.fileContents(buildPropertiesFile)
        return fileContents.asText.map {
            val properties = it.reader().use { reader -> Properties().also { it.load(reader) } }

            val fullNumber = properties["mps.build.number"] as String?
                ?: throw GradleException("Could not read mps.build.number property from file ${buildPropertiesFile.get().asFile}")

            val dash = fullNumber.indexOf("-")
            fullNumber.substring(dash + 1)
        }
    }

    fun configureJavaForMpsVersion(javaExec: JavaExec, mpsHome: Provider<File>, mpsVersion: Provider<String>) {
        val launcher = mpsVersion.flatMap { mpsVersionValue ->
            javaToolchainService.launcherFor {
                vendor.set(JvmVendorSpec.matching("JetBrains"))
                languageVersion.set(JavaLanguageVersion.of(if (mpsVersionValue < "2022") 11 else 17))
            }
        }

        javaExec.javaLauncher.set(launcher)

        javaExec.jvmArgumentProviders.add(CommandLineArgumentProvider {
            if (mpsVersion.get() >= "2022.3") {
                listOf("-Djna.boot.library.path=${mpsHome.get().resolve("lib/jna/${System.getProperty("os.arch")}").path}")
            } else {
                listOf()
            }
        })

        val modules = listOf(
            "java.base/java.io",
            "java.base/java.lang",
            "java.base/java.lang.reflect",
            "java.base/java.net",
            "java.base/java.nio",
            "java.base/java.nio.charset",
            "java.base/java.text",
            "java.base/java.time",
            "java.base/java.util",
            "java.base/java.util.concurrent",
            "java.base/java.util.concurrent.atomic",
            "java.base/jdk.internal.vm",
            "java.base/sun.nio.ch",
            "java.base/sun.nio.fs",
            "java.base/sun.security.ssl",
            "java.base/sun.security.util",
            "java.desktop/java.awt",
            "java.desktop/java.awt.dnd.peer",
            "java.desktop/java.awt.event",
            "java.desktop/java.awt.image",
            "java.desktop/java.awt.peer",
            "java.desktop/javax.swing",
            "java.desktop/javax.swing.plaf.basic",
            "java.desktop/javax.swing.text.html",
            "java.desktop/sun.awt.datatransfer",
            "java.desktop/sun.awt.image",
            "java.desktop/sun.awt",
            "java.desktop/sun.font",
            "java.desktop/sun.java2d",
            "java.desktop/sun.swing",
            "jdk.attach/sun.tools.attach",
            "jdk.compiler/com.sun.tools.javac.api",
            "jdk.internal.jvmstat/sun.jvmstat.monitor",
            "jdk.jdi/com.sun.tools.jdi",
            "java.desktop/sun.lwawt",
            "java.desktop/sun.lwawt.macosx",
            "java.desktop/com.apple.laf",
            "java.desktop/com.apple.eawt",
            "java.desktop/com.apple.eawt.event"
        )

        javaExec.jvmArgs(modules.map { "--add-opens=$it=ALL-UNNAMED" })

        // MPS versions up to and including 2021.x create logs under their working directory so set it to a temporary
        // directory to avoid polluting the checkout directory or MPS home.
        javaExec.workingDir = javaExec.temporaryDir
        javaExec.systemProperty("idea.config.path", javaExec.temporaryDir.resolve("config"))
        javaExec.systemProperty("idea.system.path", javaExec.temporaryDir.resolve("system"))
    }
}

private class ProviderToString(val provider: Provider<String>): Serializable {
    override fun toString(): String = provider.get()
}
