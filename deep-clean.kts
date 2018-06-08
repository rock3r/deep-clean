#!/usr/bin/env kscript

@file:DependsOn("com.offbytwo:docopt:0.6.0.20150202")

import org.docopt.Docopt
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

typealias CommandLineArguments = Map<String, Any>

val usage = """
This script nukes all build caches from Gradle/Android projects.
Run this in a Gradle/Android project folder.

Usage: deep-clean [options]

Options:
    -d --dry-run  Don't delete anything. Useful for testing. Implies --verbose.
    -b --backup   Renames files and folders instead of deleting them. Implies
                  --verbose.
    -n --nuke     ⚠️  THIS IS DANGEROUS SHIT ⚠️  Super-deep clean
                  This includes clearing out global folders, including:
                   * the global Gradle cache
                   * the wrapper-downloaded Gradle distros
                   * the Gradle daemon data (logs, locks, etc.)
                   * the Android build cache
                  Nukes the entire thing from orbit — it's the only way to be sure.
    -v --verbose  Print detailed information about all commands.
"""

val userHome = File(System.getProperty("user.home"))
val gradleHome = locateGradleHome()

assert(userHome.exists(), { "Unable to determine the user home folder, aborting..." })

val parsedArgs: CommandLineArguments = Docopt(usage)
    .withVersion("deep-clean 1.2.0")
    .parse(args.toList())

val nukeItFromOrbit: Boolean = parsedArgs.isFlagSet("--nuke", "-n")
val dryRun: Boolean = parsedArgs.isFlagSet("--dry-run", "-d")
val backup: Boolean = parsedArgs.isFlagSet("--backup", "-b")
val verbose: Boolean = backup || dryRun || parsedArgs.isFlagSet("--verbose", "-v")

if (dryRun) println("\nℹ️  This is a dry-run. No files will be moved/deleted.\n")

val wetRun = dryRun.not()
val gradlew = "./gradlew" + if (isOsWindows()) ".bat" else ""

Runtime.getRuntime().apply {
    println("⏳ Executing Gradle clean...")
    execOnWetRun("$gradlew clean -q")
        ?.printOutput(onlyErrors = false)
    println()

    println("🔫 Killing Gradle daemons...")
    execOnWetRun("$gradlew --stop")
        ?.printOutput()
    println()

    println("🔫 Killing ADB server...")
    execOnWetRun("adb kill-server")
        ?.printIfNoError("Adb server killed.")
    execOnWetRun("killall adb")
    println()

    val currentDir = File(Paths.get("").toAbsolutePath().toString())

    println("🔥 Removing every 'build' folder...")
    currentDir.removeSubfoldersMatching { it.name.toLowerCase() == "build" }
    println()

    println("🔥 Removing every '.gradle' folder...")
    currentDir.removeSubfoldersMatching { it.name.toLowerCase() == ".gradle" }
    println()

    if (nukeItFromOrbit) nukeGlobalCaches()

    println("🔫 Killing Kotlin compile daemon...")
    println("    ℹ️  Note: this kills any CLI Java instance running (including this script)")
    execOnWetRun("killall java")
    println()
}

fun locateGradleHome(): File? {
    val envGradleHome = System.getenv("GRADLE_HOME")
        ?.let { File(it) }
    val userGradleHome = File(userHome, ".gradle")

    return when {
        envGradleHome?.exists() == true -> envGradleHome
        userGradleHome.exists() -> userGradleHome
        else -> null
    }
}

fun CommandLineArguments.isFlagSet(vararg flagAliases: String): Boolean =
    flagAliases.map { this[it] as Boolean? }.first { it != null }!!

fun Runtime.execOnWetRun(command: String) = if (wetRun) exec(command) else null

fun Process.printOutput(onlyErrors: Boolean = true) {
    if (onlyErrors.not()) {
        inputStream.bufferedReader().lines().forEach { println("    $it") }
    }
    errorStream.bufferedReader().lines().forEach { println("    $it") }
}

fun Process.printIfNoError(message: String) {
    if (errorStream.bufferedReader().lineSequence().none()) {
        println("    $message")
    }
}

fun Runtime.nukeGlobalCaches() {
    println()
    println("☢️ ☢️ ☢️ ☢️  WARNING: nuke mode activated ☢️ ☢️ ☢️ ☢️ ")
    println()
    println("                     __,-~~/~    `---.")
    println("                   _/_,---(      ,    )")
    println("               __ /        <    /   )  \\___")
    println("- ------===;;;'====------------------===;;;===----- -  -")
    println("                  \\/  ~\"~\"~\"~\"~\"~\\~\"~)~\"/")
    println("                  (_ (   \\  (     >    \\)")
    println("                   \\_( _ <         >_>'")
    println("                      ~ `-i' ::>|--\"")
    println("                          I;|.|.|")
    println("                         <|i::|i|`.")
    println("                        (` ^'\"`-' \")")
    println("------------------------------------------------------------------")
    println("")
    println("⚠️  This will affect system-wide caches for Gradle and IDEs! ⚠️")
    println("⚠️  You will lose local version history and other IDE data!  ⚠️")
    println()
    println("    ⏲️  You have 2 seconds to cancel!")
    println("        Press Ctrl-C to stop now.")
    println()
    println()

    Thread.sleep(TimeUnit.SECONDS.toMillis(2))

    println("⏳ Clearing Android Gradle build cache...")
    exec("$gradlew cleanBuildCache")
    println()

    println("🔥 Clearing ${Ide.IntelliJIdea} caches...")
    clearIdeCache(Ide.IntelliJIdea)
    println()

    println("🔥 Clearing ${Ide.AndroidStudio} caches...")
    clearIdeCache(Ide.AndroidStudio)
    println()

    if (gradleHome != null) {
        println("🔥 Clearing Gradle global cache directories: build-scan-data, caches, daemon, wrapper...")
        gradleHome.removeSubfoldersMatching {
            it.name.toLowerCase() == "build-scan-data" ||
                it.name.toLowerCase() == "caches" ||
                it.name.toLowerCase() == "daemon" ||
                it.name.toLowerCase() == "wrapper"
        }
    } else {
        println("⚠️  Unable to locate Gradle home directory. Checked \$GRADLE_HOME and ~/.gradle")
    }
    println()
    println()
}

fun clearIdeCache(ide: Ide) {
    val cacheDirectories = locateCacheFolderFor(ide)

    when {
        backup -> cacheDirectories
            .onEach {
                println("    ℹ️  Clearing cache for $ide ${extractVersion(it.parentFile, ide)}...")
            }
            .backupAndDeleteByRenaming()
        else -> cacheDirectories
            .onEach {
                println("    ℹ️  Clearing cache for $ide ${extractVersion(it.parentFile, ide)}...")
            }
            .deleteRecursively()
    }
}

fun locateCacheFolderFor(ide: Ide): Sequence<File> {
    return when {
        isOsWindows() || isOsLinux() -> {
            userHome.listFiles { file -> file.isDirectory }
                .filter { it.name.startsWith(".${ide.folderPrefix}") }
                .map { File(it, "system") }
        }
        isOsMacOs() -> {
            File(userHome, "Library/Caches")
                .listFiles { file -> file.isDirectory }
                .filter { it.name.startsWith(ide.folderPrefix, ignoreCase = true) }
                .map { File(it, "system") }
        }
        else -> {
            println("    Unsupported OS, skipping.")
            emptyList()
        }
    }.asSequence()
}

fun isOsWindows() = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
fun isOsLinux() = System.getProperty("os.name").startsWith("Linux", ignoreCase = true)
fun isOsMacOs() = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

fun extractVersion(it: File, ide: Ide): String {
    val versionName = it.name.substringAfter(ide.folderPrefix)
    return if (versionName.startsWith("Preview")) {
        "${versionName.substring("Preview".length)} Preview"
    } else {
        versionName
    }
}

fun File.removeSubfoldersMatching(matcher: (file: File) -> Boolean) {
    val matchingDirectories = this.listFiles { file -> file.isDirectory }
        .asSequence()
        .onEach { println("      ${it.absolutePath}") }
        .filter(matcher)

    when {
        backup -> matchingDirectories.backupAndDeleteByRenaming()
        else -> matchingDirectories.deleteRecursively()
    }
}

fun Sequence<File>.backupAndDeleteByRenaming() =
    this.onEach { if (verbose) println("      📁  Deleting directory: ${it.absolutePath}") }
        .map { Pair(it, generateBackupNameFor(it)) }
        .onEach { (_, backup) -> if (verbose) println("        ✅ ️ Backed up to: ${backup.name}") }
        .onEach { println() }
        .forEach { (original, backup) -> if (wetRun) original.renameTo(backup) }

fun generateBackupNameFor(file: File): File {
    var backupFile: File
    var index = 0
    do {
        backupFile = File(file.parentFile, "${file.name}-backup%02d".format(index))
        index++
    } while (backupFile.exists())
    return backupFile
}

fun Sequence<File>.deleteRecursively() =
    this.onEach { if (verbose) println("    📁  Deleting directory: ${it.absolutePath}") }
        .onEach { println() }
        .forEach { if (wetRun) it.deleteRecursively() }

sealed class Ide(private val name: String, val folderPrefix: String) {

    object IntelliJIdea : Ide("IntelliJ IDEA", "IntelliJIdea")

    object AndroidStudio : Ide("Android Studio", "AndroidStudio")

    override fun toString() = name
}
