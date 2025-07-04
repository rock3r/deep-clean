#!/usr/bin/env kotlin
@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:5.0.3")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

// --- Global Properties ---
val userHome = File(System.getProperty("user.home"))
val gradleHome = locateGradleHome()
val mavenLocalRepository = locateMavenLocalRepository()
val workingDir = File(Paths.get("").toAbsolutePath().toString())

// --- Main Command Definition using Clikt ---

class DeepClean :
    CliktCommand(
        name = "deep-clean",
    ) {
    override fun help(context: Context) =
        "This script nukes all build caches from Gradle/Android projects. Run this in a Gradle/Android project folder."

    init {
        versionOption("2.0.0")
    }

    // --- Option Definitions ---

    val backup by
        option("-b", "--backup", help = "Renames files and folders instead of deleting them. Implies --verbose.").flag()

    val dryRun by
        option("-d", "--dry-run", help = "Don't delete anything. Useful for testing. Implies --verbose.").flag()

    val ideFiles by
        option(
                "-i",
                "--ide-files",
                help =
                    "This also deletes IDEA/Android Studio project files (*.iml). If used in conjunction with --nuke it will also delete the .idea folder in the current directory.",
            )
            .flag()

    val idePreferences by
        option(
                "-p",
                "--ide-preferences",
                help =
                    "⚠️  THIS IS DANGEROUS SHIT ⚠️  Will wipe your IDE settings! This option requires the --nuke option to be active too, since it touches global system state.",
            )
            .flag()

    val notRecursive by
        option(
                "--not-recursive",
                help =
                    "Don't recursively search sub-folders of this folder for matches. The default behaviour is to look for matches in sub-directories.",
            )
            .flag()

    val nuke by
        option(
                "-n",
                "--nuke",
                help =
                    "⚠️  THIS IS DANGEROUS SHIT ⚠️  Super-deep clean. This includes clearing out global folders like Gradle/Maven caches.",
            )
            .flag()

    val verbose by option("-v", "--verbose", help = "Print detailed information about all commands.").flag()

    /** The main entry point for the script logic, executed by Clikt. */
    override fun run() {
        // --- Derived Flags ---
        // These are calculated from the options provided by the user.
        val isVerbose = backup || dryRun || verbose
        val isRecursive = !notRecursive
        val isWetRun = !dryRun
        val shouldClearIdePreferences = idePreferences && nuke

        // --- Pre-run Checks ---
        assert(userHome.exists()) { "Unable to determine the user home folder, aborting..." }

        if (idePreferences && !shouldClearIdePreferences) {
            println("\n⚠️  To clear the IDE preferences you must also enable nuke mode (--nuke).")
        }

        if (dryRun) {
            println("\nℹ️  This is a dry-run. No files will be moved/deleted.\n")
        }

        val gradlew = "./gradlew" + if (isOsWindows()) ".bat" else ""
        if (!File(gradlew.removePrefix("./")).exists()) {
            printInBold("❌  Could not find Gradle wrapper in the work directory: $gradlew")
            exitProcess(-1)
        }

        // --- Execution Flow ---
        Runtime.getRuntime().apply {
            printInBold("⏳ Executing Gradle clean...")
            doWithGradleWrapper { execOnWetRun("$gradlew clean -q", isWetRun)?.printOutput(onlyErrors = !isVerbose) }
            println()

            printInBold("🔫 Killing Gradle daemon...")
            doWithGradleWrapper { execOnWetRun("$gradlew --stop", isWetRun)?.printOutput() }
            println()

            printInBold("🔫 Killing ADB server...")
            killAdb(isWetRun)
            println()

            printInBold("🔥 Removing every 'build' folder...")
            workingDir.removeSubfoldersMatching(isRecursive, backup, isVerbose, isWetRun) {
                it.name.equals("build", ignoreCase = true)
            }
            println()

            printInBold("🔥 Removing every '.gradle' folder...")
            workingDir.removeSubfoldersMatching(isRecursive, backup, isVerbose, isWetRun) {
                it.name.equals(".gradle", ignoreCase = true)
            }
            println()

            if (ideFiles) {
                deleteIdeaProjectFiles(isRecursive, backup, isVerbose, isWetRun)
            }

            if (shouldClearIdePreferences) {
                printIdePreferencesWarning(timeoutSeconds = 3)
                clearIdePreferences(backup, isVerbose, isWetRun)
            }

            if (nuke) {
                printNukeModeWarning(timeoutSeconds = 3)
                if (ideFiles) {
                    nukeIdeaProjectSettingsFolder(backup, isVerbose, isWetRun)
                }
                nukeGlobalCaches(gradlew, isWetRun, isVerbose, backup)
            }

            printInBold("🔫 Killing Kotlin compile daemon...")
            println("     ℹ️  Note: this kills any CLI Java instance running (including this script)")
            execOnWetRun("killall java", isWetRun)
            println()
        }
    }
}

// --- Helper Functions ---
// These functions are now refactored to accept flags as parameters
// instead of relying on global state.

fun locateGradleHome(): File? {
    val envGradleHome = System.getenv("GRADLE_HOME")?.let { File(it) }
    val userGradleHome = File(userHome, ".gradle")
    return when {
        envGradleHome?.exists() == true -> envGradleHome
        userGradleHome.exists() -> userGradleHome
        else -> null
    }
}

fun locateMavenLocalRepository(): File? {
    return File(userHome, ".m2").takeIf { it.exists() }
}

fun Runtime.execOnWetRun(command: String, isWetRun: Boolean) = if (isWetRun) exec(command) else null

fun Process.printOutput(onlyErrors: Boolean = true) {
    if (!onlyErrors) {
        inputStream.bufferedReader().lines().forEach { println("     $it") }
    }
    errorStream.bufferedReader().lines().forEach { println("     $it") }
}

fun Process.printIfNoError(message: String) {
    if (errorStream.bufferedReader().lineSequence().none()) {
        println("     $message")
    }
}

fun Runtime.doWithGradleWrapper(action: () -> Unit) {
    if (!Files.exists(Paths.get("gradlew")) && !isExecutableOnPath("adb")) {
        println("⚠️  Gradle wrapper not found. Nothing to do here.")
        return
    }
    action()
}

fun Runtime.killAdb(isWetRun: Boolean) {
    if (!isExecutableOnPath("adb")) {
        println("⚠️  ADB not found. Nothing to do here.")
        return
    }
    execOnWetRun("adb kill-server", isWetRun)?.printIfNoError("Adb server killed.")
    execOnWetRun("killall adb", isWetRun)
}

fun Runtime.isExecutableOnPath(executableName: String): Boolean =
    System.getenv("PATH").split(File.pathSeparator).map(Paths::get).any { pathEntry ->
        Files.exists(pathEntry.resolve(executableName))
    }

fun deleteIdeaProjectFiles(isRecursive: Boolean, isBackup: Boolean, isVerbose: Boolean, isWetRun: Boolean) {
    printInBold("🔥 Removing IntelliJ IDEA/Android Studio '.iml' project files...")
    workingDir.removeFilesWithExtension("iml", isRecursive, isBackup, isVerbose, isWetRun)
    println()
}

fun File.removeFilesWithExtension(
    extension: String,
    isRecursive: Boolean,
    isBackup: Boolean,
    isVerbose: Boolean,
    isWetRun: Boolean,
) {
    val matchingFiles =
        this.listContents(recursively = isRecursive) {
            !it.isDirectory && it.extension.equals(extension, ignoreCase = true)
        }
    if (isBackup) {
        matchingFiles.backupAndDeleteByRenaming(isVerbose, isWetRun)
    } else {
        matchingFiles.deleteRecursively(isVerbose, isWetRun)
    }
}

fun printIdePreferencesWarning(timeoutSeconds: Long) {
    printInBold("⚠️  ⚠️  ⚠️  ⚠️  WARNING: deleting IDE settings ⚠️  ⚠️  ⚠️  ⚠️  ")
    println()
    println("               (  .      )")
    println("           )           (              )")
    println("                 .  '   .   '  .  '  .")
    println("        (    , )       (.   )  (   ',    )")
    println("         .' ) ( . )    ,  ( ,     )   ( .")
    println("      ). , ( .   (  ) ( , ')  .' (  ,    )")
    println("     (_,) . ), ) _) _,')  (, ) '. )  ,. (' )")
    println(" jgs^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^")
    println()
    println("⚠️  This will reset all your IDE preferences! ⚠️")
    println()
    printInBold("    ⏲️  You have $timeoutSeconds seconds to cancel! ⏲️")
    println("       Press Ctrl-C to stop now.")
    println()
    println()
    Thread.sleep(TimeUnit.SECONDS.toMillis(timeoutSeconds))
}

fun clearIdePreferences(isBackup: Boolean, isVerbose: Boolean, isWetRun: Boolean) {
    printInBold("🔥 Clearing ${Ide.IntelliJIdea} preferences...")
    clearIdePreferencesFor(Ide.IntelliJIdea, isBackup, isVerbose, isWetRun)
    println()

    printInBold("🔥 Clearing ${Ide.AndroidStudio} preferences...")
    clearIdePreferencesFor(Ide.AndroidStudio, isBackup, isVerbose, isWetRun)
    println()
}

fun clearIdePreferencesFor(ide: Ide, isBackup: Boolean, isVerbose: Boolean, isWetRun: Boolean) {
    val preferencesDirectories = locatePreferencesFolderFor(ide)
    val processedDirs =
        preferencesDirectories.onEach {
            println("     ℹ️  Clearing preferences for $ide ${extractVersion(it, ide)}...")
        }
    if (isBackup) {
        processedDirs.backupAndDeleteByRenaming(isVerbose, isWetRun)
    } else {
        processedDirs.deleteRecursively(isVerbose, isWetRun)
    }
}

fun locatePreferencesFolderFor(ide: Ide): Sequence<File> =
    when {
        isOsWindows() || isOsLinux() -> {
            userHome.listContents(recursively = false) { it.isDirectory && it.name.startsWith(".${ide.folderPrefix}") }
        }
        isOsMacOs() -> {
            File(userHome, "Library/Preferences").listContents(recursively = false) {
                it.isDirectory && it.name.startsWith(ide.folderPrefix, ignoreCase = true)
            }
        }
        else -> {
            println("     ⚠️  Unsupported OS, skipping.")
            emptySequence()
        }
    }.filter { it.exists() }

fun nukeIdeaProjectSettingsFolder(isBackup: Boolean, isVerbose: Boolean, isWetRun: Boolean) {
    printInBold("🔥 Removing IntelliJ IDEA/Android Studio '.idea' folders...")
    // .idea folders are never searched recursively from the root, so isRecursive is false.
    workingDir.removeSubfoldersMatching(false, isBackup, isVerbose, isWetRun) {
        it.isDirectory && it.name.equals(".idea", ignoreCase = true)
    }
    println()
}

fun printNukeModeWarning(timeoutSeconds: Long) {
    printInBold("☢️ ☢️ ☢️ ☢️  WARNING: nuke mode activated ☢️ ☢️ ☢️ ☢️ ")
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
    printInBold("    ⏲️  You have $timeoutSeconds seconds to cancel! ⏲️")
    println("       Press Ctrl-C to stop now.")
    println()
    println()
    Thread.sleep(TimeUnit.SECONDS.toMillis(timeoutSeconds))
}

fun Runtime.nukeGlobalCaches(gradlew: String, isWetRun: Boolean, isVerbose: Boolean, isBackup: Boolean) {
    printInBold("⏳ Clearing Android Gradle build cache...")
    execOnWetRun("$gradlew cleanBuildCache", isWetRun)
    println()

    printInBold("🔥 Clearing ${Ide.IntelliJIdea} caches...")
    clearIdeCache(Ide.IntelliJIdea, isBackup, isVerbose, isWetRun)
    println()

    printInBold("🔥 Clearing ${Ide.AndroidStudio} caches...")
    clearIdeCache(Ide.AndroidStudio, isBackup, isVerbose, isWetRun)
    println()

    printInBold("🔥 Clearing Maven local repository artefacts...")
    if (mavenLocalRepository != null) {
        if (isVerbose) println("     ℹ️  Maven local repository found at: ${mavenLocalRepository.absolutePath}")
        // Maven repo is never searched recursively, so isRecursive is false.
        mavenLocalRepository.removeSubfoldersMatching(false, isBackup, isVerbose, isWetRun) {
            it.name.lowercase() == "repository"
        }
    } else {
        println("     ⚠️  Unable to locate Maven local repository. Checked ~/.m2")
    }

    printInBold("🔥 Clearing Gradle global cache directories: build-scan-data, caches, daemon, wrapper...")
    if (gradleHome != null) {
        if (isVerbose) println("     ℹ️  Gradle home found at: ${gradleHome.absolutePath}")
        // Gradle home is never searched recursively, so isRecursive is false.
        gradleHome.removeSubfoldersMatching(false, isBackup, isVerbose, isWetRun) {
            val lowerCaseName = it.name.lowercase()
            lowerCaseName == "build-scan-data" ||
                lowerCaseName == "caches" ||
                lowerCaseName == "daemon" ||
                lowerCaseName == "wrapper"
        }
    } else {
        println("     ⚠️  Unable to locate Gradle home directory. Checked \$GRADLE_HOME and ~/.gradle")
    }
    println()
}

fun printInBold(message: String) {
    when {
        isOsWindows() -> println(message)
        else -> println("\u001B[1;37m$message\u001B[0;37m")
    }
}

fun clearIdeCache(ide: Ide, isBackup: Boolean, isVerbose: Boolean, isWetRun: Boolean) {
    val cacheDirectories = locateCacheFolderFor(ide)
    val processedDirs =
        cacheDirectories.onEach { println("     ℹ️  Clearing cache for $ide ${extractVersion(it, ide)}...") }
    if (isBackup) {
        processedDirs.backupAndDeleteByRenaming(isVerbose, isWetRun)
    } else {
        processedDirs.deleteRecursively(isVerbose, isWetRun)
    }
}

fun locateCacheFolderFor(ide: Ide): Sequence<File> =
    when {
        isOsWindows() || isOsLinux() -> {
            userHome.listContents(recursively = false) { it.isDirectory && it.name.startsWith(".${ide.folderPrefix}") }
        }
        isOsMacOs() -> {
            File(userHome, "Library/Caches").listContents(recursively = false) {
                it.isDirectory && it.name.startsWith(ide.folderPrefix, ignoreCase = true)
            }
        }
        else -> {
            println("     ⚠️  Unsupported OS, skipping.")
            emptySequence()
        }
    }.filter { it.exists() }

fun isOsLinux() = System.getProperty("os.name").startsWith("Linux", ignoreCase = true)

fun isOsMacOs() = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

fun isOsWindows() = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

fun extractVersion(it: File, ide: Ide): String {
    val versionName = it.name.substringAfter(ide.folderPrefix)
    return if (versionName.startsWith("Preview")) {
        "${versionName.substring("Preview".length)} Preview"
    } else {
        versionName
    }
}

fun File.removeSubfoldersMatching(
    isRecursive: Boolean,
    isBackup: Boolean,
    isVerbose: Boolean,
    isWetRun: Boolean,
    matcher: (file: File) -> Boolean,
) {
    val matchingDirectories = this.listContents(recursively = isRecursive) { it.isDirectory && matcher(it) }
    if (isBackup) {
        matchingDirectories.backupAndDeleteByRenaming(isVerbose, isWetRun)
    } else {
        matchingDirectories.deleteRecursively(isVerbose, isWetRun)
    }
}

fun File.listContents(recursively: Boolean, matcher: (File) -> Boolean): Sequence<File> =
    this.listFiles()?.asSequence()?.flatMap { file ->
        when {
            matcher(file) -> sequenceOf(file)
            recursively && file.isDirectory -> file.listContents(recursively = true, matcher = matcher)
            else -> emptySequence()
        }
    } ?: emptySequence()

fun Sequence<File>.backupAndDeleteByRenaming(isVerbose: Boolean, isWetRun: Boolean) {
    this.onEach { if (isVerbose) println("     Deleting: ${it.absolutePath}") }
        .map { it to generateBackupNameFor(it) }
        .onEach { (_, backup) -> if (isVerbose) println("       ⤷ Backing up to: ${backup.name}") }
        .forEach { (original, backup) -> if (isWetRun) original.renameTo(backup) }
}

fun generateBackupNameFor(file: File): File {
    var backupFile: File
    var index = 0
    do {
        backupFile = File(file.parentFile, "${file.name}-backup%02d".format(index))
        index++
    } while (backupFile.exists())
    return backupFile
}

fun Sequence<File>.deleteRecursively(isVerbose: Boolean, isWetRun: Boolean) {
    this.onEach { if (isVerbose) println("     Deleting: ${it.absolutePath}") }
        .forEach { if (isWetRun) it.deleteRecursively() }
}

// --- Data Class for IDEs ---

sealed class Ide(private val name: String, val folderPrefix: String) {
    object IntelliJIdea : Ide(name = "IntelliJ IDEA", folderPrefix = "IntelliJIdea")

    object AndroidStudio : Ide(name = "Android Studio", folderPrefix = "AndroidStudio")

    override fun toString() = name
}

// --- Run the script ---
DeepClean().main(args)
