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
    -d --dry-run     Don't delete anything. Useful for testing. Implies --verbose.
    -b --backup      Renames files and folders instead of deleting them. Implies
                     --verbose.
    -i --ide         This also deletes IDEA/Android Studio project files (*.iml).
                     If used in conjunction with --nuke it will also delete the
                     .idea folder in the current directory.
    -n --nuke        ⚠️  THIS IS DANGEROUS SHIT ⚠️  Super-deep clean
                     This includes clearing out global folders, including:
                      * the global Gradle cache
                      * the wrapper-downloaded Gradle distros
                      * the Gradle daemon data (logs, locks, etc.)
                      * the Android build cache
                     Nukes the entire thing from orbit — it's the only way to be sure.
    --not-recursive  Don't recursively search sub-folders of this folder for matches.
                     The default behaviour is to look for matches in sub-directories,
                     since things like 'build' folders and '.iml' files are not all
                     found at the top level of a project directory structure. This
                     flag is useful if you know you have matches you want to keep,
                     e.g., if your code contains a package with a name like 'build'.
                     This option severely limits the effectiveness of the deep clean.
    -v --verbose     Print detailed information about all commands.
"""

val userHome = File(System.getProperty("user.home"))
val gradleHome = locateGradleHome()

val workingDir = File(Paths.get("").toAbsolutePath().toString())

assert(userHome.exists(), { "Unable to determine the user home folder, aborting..." })

val parsedArgs: CommandLineArguments = Docopt(usage)
    .withVersion("deep-clean 1.2.0")
    .parse(args.toList())

val nukeItFromOrbit = parsedArgs.isFlagSet("--nuke", "-n")
val ideFiles = parsedArgs.isFlagSet("--ide", "-i")
val recursively = parsedArgs.isFlagSet("--not-recursive").not()
val dryRun = parsedArgs.isFlagSet("--dry-run", "-d")
val backup = parsedArgs.isFlagSet("--backup", "-b")
val verbose = backup || dryRun || parsedArgs.isFlagSet("--verbose", "-v")

if (dryRun) println("\nℹ️  This is a dry-run. No files will be moved/deleted.\n")

val wetRun = dryRun.not()
val gradlew = "./gradlew" + if (isOsWindows()) ".bat" else ""

Runtime.getRuntime().apply {
    printInBold("⏳ Executing Gradle clean...")
    execOnWetRun("$gradlew clean -q")
        ?.printOutput(onlyErrors = !verbose)
    println()

    printInBold("🔫 Killing Gradle daemon...")
    execOnWetRun("$gradlew --stop")
        ?.printOutput()
    println()

    printInBold("🔫 Killing ADB server...")
    execOnWetRun("adb kill-server")
        ?.printIfNoError("Adb server killed.")
    execOnWetRun("killall adb")
    println()

    printInBold("🔥 Removing every 'build' folder...")
    workingDir.removeSubfoldersMatching { it.name.equals("build", ignoreCase = true) }
    println()

    printInBold("🔥 Removing every '.gradle' folder...")
    workingDir.removeSubfoldersMatching { it.name.equals(".gradle", ignoreCase = true) }
    println()

    if (ideFiles) deleteIdeaProjectFiles()

    if (nukeItFromOrbit) {
        printNukeModeWarning(timeoutSeconds = 3)

        if (ideFiles) nukeIdeaSettingsFolder()

        nukeGlobalCaches()
    }

    printInBold("🔫 Killing Kotlin compile daemon...")
    println("     ℹ️  Note: this kills any CLI Java instance running (including this script)")
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
        inputStream.bufferedReader().lines().forEach { println("     $it") }
    }
    errorStream.bufferedReader().lines().forEach { println("     $it") }
}

fun Process.printIfNoError(message: String) {
    if (errorStream.bufferedReader().lineSequence().none()) {
        println("     $message")
    }
}

fun deleteIdeaProjectFiles() {
    printInBold("🔥 Removing IntelliJ IDEA/Android Studio '.iml' project files...")
    workingDir.removeFilesWithExtension("iml")
    println()
}

fun File.removeFilesWithExtension(extension: String) {
    val matchingFiles = this
        .listContents(recursively = recursively) {
            !it.isDirectory && it.extension.equals(extension, ignoreCase = true)
        }

    when {
        backup -> matchingFiles.backupAndDeleteByRenaming()
        else -> matchingFiles.deleteRecursively()
    }
}

fun nukeIdeaSettingsFolder() {
    printInBold("🔥 Removing IntelliJ IDEA/Android Studio '.idea' folders...")

    workingDir.removeSubfoldersMatching {
        it.isDirectory && it.name.equals(".idea", ignoreCase = true)
    }
    println()
}

fun Runtime.nukeGlobalCaches() {
    printInBold("⏳ Clearing Android Gradle build cache...")
    exec("$gradlew cleanBuildCache")
    println()

    printInBold("🔥 Clearing ${Ide.IntelliJIdea} caches...")
    clearIdeCache(Ide.IntelliJIdea)
    println()

    printInBold("🔥 Clearing ${Ide.AndroidStudio} caches...")
    clearIdeCache(Ide.AndroidStudio)
    println()

    printInBold("🔥 Clearing Gradle global cache directories: build-scan-data, caches, daemon, wrapper...")
    if (gradleHome != null) {
        if (verbose) println("     ℹ️  Gradle home found at: ${gradleHome.absolutePath}")
        gradleHome.removeSubfoldersMatching {
            it.name.toLowerCase() == "build-scan-data" ||
                it.name.toLowerCase() == "caches" ||
                it.name.toLowerCase() == "daemon" ||
                it.name.toLowerCase() == "wrapper"
        }
    } else {
        println("     ⚠️  Unable to locate Gradle home directory. Checked \$GRADLE_HOME and ~/.gradle")
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

fun clearIdeCache(ide: Ide) {
    val cacheDirectories = locateCacheFolderFor(ide)

    when {
        backup -> cacheDirectories
            .onEach {
                println("     ℹ️  Clearing cache for $ide ${extractVersion(it, ide)}...")
            }
            .backupAndDeleteByRenaming()
        else -> cacheDirectories
            .onEach {
                println("     ℹ️  Clearing cache for $ide ${extractVersion(it, ide)}...")
            }
            .deleteRecursively()
    }
}

fun locateCacheFolderFor(ide: Ide): Sequence<File> =
    when {
        isOsWindows() || isOsLinux() -> {
            userHome.listContents(recursively = false) {
                it.isDirectory && it.name.startsWith(".${ide.folderPrefix}")
            }
        }
        isOsMacOs() -> {
            File(userHome, "Library/Caches")
                .listContents(recursively = false) {
                    it.isDirectory && it.name.startsWith(ide.folderPrefix, ignoreCase = true)
                }
        }
        else -> {
            println("     ⚠️  Unsupported OS, skipping.")
            emptySequence()
        }
    }
        .filter { it.exists() }

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
    val matchingDirectories = this
        .listContents(recursively = recursively) {
            it.isDirectory && matcher(it)
        }

    when {
        backup -> matchingDirectories.backupAndDeleteByRenaming()
        else -> matchingDirectories.deleteRecursively()
    }
}

fun File.listContents(recursively: Boolean, matcher: (File) -> Boolean): Sequence<File> =
    this.listFiles()
        .asSequence()
        .flatMap {
            when {
                matcher(it) -> sequenceOf(it)
                recursively && it.isDirectory -> {
                    it.listContents(recursively = true, matcher = matcher)
                }
                else -> sequenceOf()
            }
        }

fun Sequence<File>.backupAndDeleteByRenaming() =
    this.onEach { if (verbose) println("     Deleting: ${it.absolutePath}") }
        .map { Pair(it, generateBackupNameFor(it)) }
        .onEach { (_, backup) -> if (verbose) println("       ⤷ Backing up to: ${backup.name}") }
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
    this.onEach { if (verbose) println("     Deleting: ${it.absolutePath}") }
        .forEach { if (wetRun) it.deleteRecursively() }

fun printInBold(message: String) {
    when {
        isOsWindows() -> println(message)
        else -> println("\u001B[1;37m$message\u001B[0;37m")
    }
}

fun isOsWindows() = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

sealed class Ide(private val name: String, val folderPrefix: String) {

    object IntelliJIdea : Ide("IntelliJ IDEA", "IntelliJIdea")

    object AndroidStudio : Ide("Android Studio", "AndroidStudio")

    override fun toString() = name
}
