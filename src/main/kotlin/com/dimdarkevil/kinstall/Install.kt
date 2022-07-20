package com.dimdarkevil.kinstall

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.utils.IOUtils
import org.docopt.Docopt
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread
import kotlin.system.exitProcess

object Install {
    private val log = LoggerFactory.getLogger(Install::class.java)
    private val HOME = File(System.getProperty("user.home"))
    private val kinstallDir = File(HOME, ".kinstall")
    private val userBinDir = File(HOME, "bin")
    private val pwd = File(System.getProperty("user.dir"))
    private val projectNameRegex = Regex("rootProject: root project '(.*?)'")
    private val projectVerRegex = Regex("version: (.*)")
    private val tarballExtensions = setOf("tar", "gz", "tgz")

    private val help = """
        kinstall ${Version.version}
        Usage:
            kinstall [options]
            kinstall --version
            kinstall -h | --help

        Options:
            -l --list                                   list installs
            -r <name:version> --remove=<name:version>   remove an install
            -u <name:version> --use=<name:version>      use a specific version of an install
        """.trimIndent().replace("\t","    ")

    @JvmStatic
    fun main(args: Array<String>) {
        val opts = try {
            docOptFor(help, args)
        } catch (e: ExitProcessException) {
            exitProcess(0)
        }
        try {
            val listInst = opts["--list"] as? Boolean ?: false
            val removeInst = opts["--remove"]?.toString()?.nullIfBlank()
            val useInst = opts["--use"]?.toString()?.nullIfBlank()

            when {
                listInst -> listInstalls()
                removeInst != null -> removeInstall(removeInst)
                useInst != null -> useInstall(useInst)
                else -> createInstall()
            }
        } catch (e: Exception) {
            println(e.message)
            exitProcess(1)
        }
    }

    fun listInstalls() {
        kinstallDir.listFiles { f -> f.isDirectory }?.forEach { projectDir ->
            val versions = projectDir.listFiles { vf -> vf.isDirectory && vf.name != "_current" }?.map { versionDir ->
                val currentDir = File(projectDir, "_current")
                val isCurrent = (currentDir.exists() && currentDir.canonicalPath == versionDir.canonicalPath)
                val currentMarker = if (isCurrent) "*" else " "
                "${currentMarker}${versionDir.name}"
            } ?: emptyList()
            if (versions.isNotEmpty()) {
                println(projectDir.name)
                versions.forEach{ println(it) }
                println()
            }
        }
    }

    fun removeInstall(install: String) {
        val (project, version) = install.toProjectVersion()
        println("remove project: $project, version: $version")
        val versionDir = File(kinstallDir, "${project}/${version}")
        if (!versionDir.exists()) throw RuntimeException("No project found for $install")
        val currentDir = File(kinstallDir, "${project}/_current")
        val isCurrent = (currentDir.exists() && currentDir.canonicalPath == versionDir.canonicalPath)
        if (isCurrent) {
            println("${version} of ${project} is your current version. Are you sure (y/N)?")
            if ((readLine() ?: "n").lowercase() != "y") {
                println("aborting removal of $project $version")
                return
            }
            currentDir.delete()
        }
        versionDir.deleteRecursively()
        versionDir.delete()
        val projectDir = File(kinstallDir, project)
        val otherVersions = projectDir.listFiles { vf -> vf.isDirectory && vf.name != "_current" }?.toList() ?: emptyList()
        if (otherVersions.isEmpty()) {
            projectDir.deleteRecursively()
            projectDir.delete()
        }
    }

    fun useInstall(install: String) {
        val (project, version) = install.toProjectVersion()
        println("use project: $project, version: $version")
        val versionDir = File(kinstallDir, "${project}/${version}")
        if (!versionDir.exists()) throw RuntimeException("No project found for $install")
        val currentDir = File(kinstallDir, "${project}/_current")
        val isCurrent = (currentDir.exists() && currentDir.canonicalPath == versionDir.canonicalPath)
        if (isCurrent) {
            println("${version} of ${project} is already your current version")
            return
        }
        if (currentDir.exists()) currentDir.delete()
        Files.createSymbolicLink(currentDir.toPath(), versionDir.toPath())
        val targetBinDir = File(currentDir, "bin")
        targetBinDir.listFiles { f -> f.extension != "bat" }?.forEach { f ->
            val targetFile = File(userBinDir, f.name)
            if (targetFile.exists()) targetFile.delete()
            println("creating symlink to ${targetFile.path}")
            Files.createSymbolicLink(File(userBinDir, f.name).toPath(), f.toPath())
        }
    }

    fun createInstall() {
        val (project, version) = getProjectNameAndVersion()
        println("create project: $project, version: $version")
        val installSubDirStr = "$project/$version"
        val installDir = File(kinstallDir, installSubDirStr)
        installDir.mkdirs()
        userBinDir.mkdirs()
        val distDir = File(pwd, "build/distributions")
        if (!distDir.exists()) throw RuntimeException("No dist dir ${distDir.path}")
        if (!distDir.isDirectory) throw RuntimeException("Dist dir is not a directory: ${distDir.path}")
        val distTarFile = distDir.listFiles { f -> f.extension in tarballExtensions }?.toList()?.firstOrNull()
            ?: throw RuntimeException("No distributing tar/tgz files found in ${distDir.path}")
        untarFile(distTarFile, installDir)
        val currentDir = File(kinstallDir, "${project}/_current")
        if (currentDir.exists() && currentDir.canonicalPath != installDir.canonicalPath) {
            println("Do you want to make $version the current version of $project (y/N)?")
            if ((readLine() ?: "n").lowercase() != "y") return
        }
        currentDir.deleteIfExists()
        Files.createSymbolicLink(currentDir.toPath(), installDir.toPath())
        val targetBinDir = File(currentDir, "bin")
        targetBinDir.listFiles { f -> f.extension != "bat" }?.forEach { f ->
            val targetFile = File(userBinDir, f.name)
            if (targetFile.exists()) targetFile.delete()
            println("creating symlink to ${targetFile.path}")
            Files.createSymbolicLink(File(userBinDir, f.name).toPath(), f.toPath())
        }
    }

    fun File.deleteIfExists() {
        if (this.exists()) this.delete()
    }

    fun File.isGzip() = (extension == "tgz" || extension == "gz")

    fun File.bufferedInputStream() = if (isGzip()) {
        BufferedInputStream(GZIPInputStream(FileInputStream(this)))
    } else {
        BufferedInputStream(FileInputStream(this))
    }

    fun untarFile(fileToUntar: File, destFolder: File) {
        fileToUntar.bufferedInputStream().use { fin ->
            TarArchiveInputStream(fin).use { ais ->
                var entry : TarArchiveEntry? = ais.nextTarEntry
                while (entry != null) {
                    //println("${entry.name} ${entry.mode.toString(8).substring(3)}")
                    val outputFileRel = entry.name.split("/").drop(1).joinToString("/")
                    val outputFile = File(destFolder, outputFileRel)
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile.mkdirs()
                        FileOutputStream(outputFile).use { fos ->
                            IOUtils.copy(ais, fos)
                            val fileMode = octalToPermStr(entry?.mode?.toString(8)?.substring(3) ?: "644")
                            Files.setPosixFilePermissions(outputFile.toPath(), PosixFilePermissions.fromString(fileMode))
                        }
                    }
                    entry = ais.nextTarEntry
                }
            }
        }
    }

    fun octalToPermStr(octal: String) : String {
        return octal.map { c ->
            when (c) {
                '0' -> "---"
                '1' -> "--x"
                '2' -> "-w-"
                '3' -> "-wx"
                '4' -> "r--"
                '5' -> "r-x"
                '6' -> "rw-"
                '7' -> "rwx"
                else -> throw RuntimeException("$octal is not octal")
            }
        }.joinToString("")
    }

    fun getProjectNameAndVersion() : Pair<String,String> {
        val (exitCode, res) = shellExecReturningStdOut("gradle", listOf("properties", "--console=plain", "-q"), workingDir = pwd)
        if (exitCode != 0) throw RuntimeException("failed to run gradle properties in ${pwd.path}")
        val propLines = res.split("\n")
        val pnameLine = propLines.find { projectNameRegex.matches(it) }
            ?: throw RuntimeException("unable to find project name in gradle properties")
        val pverLine = propLines.find { projectVerRegex.matches(it) }
            ?: throw RuntimeException("unable to find project version in gradle properties")
        val pname = projectNameRegex.find(pnameLine)?.groupValues?.get(1)
            ?: throw RuntimeException("unable to match project name in gradle properties ($pnameLine)")
        val pver = projectVerRegex.find(pverLine)?.groupValues?.get(1)
            ?: throw RuntimeException("unable to match project version in gradle properties ($pverLine)")
        return Pair(pname, pver)
    }

    fun shellExec(
        cmd: String,
        args: List<String> = listOf(),
        envs: Map<String,String> = mapOf(),
        workingDir: File,
        cleanupFn: () -> Unit = {}
    ) : Int {
        return try {
            if (args.isNotEmpty()) {
                ProcessBuilder(cmd, *args.toTypedArray()).directory(workingDir)
            } else {
                ProcessBuilder(cmd).directory(workingDir)
            }.let { processBuilder ->
                if (envs.isNotEmpty()) processBuilder.environment().putAll(envs)
                processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
                processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
                processBuilder.start().waitFor()
            }
        } finally {
            cleanupFn()
        }
    }

    fun shellExecReturningStdOut(
        cmd: String,
        args: List<String> = listOf(),
        envs: Map<String,String> = mapOf(),
        workingDir: File,
        cleanupFn: () -> Unit = {}
    ) : Pair<Int,String> {
        return try {
            if (args.isNotEmpty()) {
                ProcessBuilder(cmd, *args.toTypedArray()).directory(workingDir)
            } else {
                ProcessBuilder(cmd).directory(workingDir)
            }.let { processBuilder ->
                if (envs.isNotEmpty()) processBuilder.environment().putAll(envs)
                processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE)
                processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
                processBuilder.start().let { process ->
                    val output = ByteArrayOutputStream()
                    thread {
                        process.inputStream.copyTo(output)
                    }
                    val exitCode = process.waitFor()
                    Pair(exitCode, output.toString(Charsets.UTF_8))
                }
            }
        } finally {
            cleanupFn()
        }
    }

    fun docOptFor(help: String, args: Array<String>) : Map<String,Any> {
        val opts = Docopt(help)
            .withHelp(true)
            .withExit(true)
            .parse(args.toList())
        if (opts["--version"] == true) {
            println(help.split("\n").first())
            throw ExitProcessException()
        }
        return opts
    }

    fun String.nullIfBlank() = if (this.isBlank()) null else this

    fun String.toProjectVersion() = this.split(":").let { Pair(it[0], it.drop(1).joinToString(":")) }
}
