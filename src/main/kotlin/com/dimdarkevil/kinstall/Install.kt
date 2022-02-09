package com.dimdarkevil.kinstall

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.utils.IOUtils
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.system.exitProcess

object Install {
    private val log = LoggerFactory.getLogger(Install::class.java)
    private val HOME = File(System.getProperty("user.home"))
    private val installDir = File(HOME, ".kinstall")
    private val userBinDir = File(HOME, "bin")

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            installDir.mkdirs()
            userBinDir.mkdirs()
            val pwd = File(System.getProperty("user.dir"))
            val distDir = File(pwd, "build/distributions")
            if (!distDir.exists()) throw RuntimeException("No dist dir ${distDir.path}")
            if (!distDir.isDirectory) throw RuntimeException("Dist dir is not a directory: ${distDir.path}")
            val dists = distDir.listFiles { f -> f.extension == "tar" }?.toList() ?: emptyList()
            if (dists.isEmpty()) throw RuntimeException("No distributing tar files found in ${distDir.path}")
            dists.forEach { distTarFile ->
                val destRoot = untarFile(distTarFile, installDir)
                val targetBinDir = File(installDir, "$destRoot/bin")
                targetBinDir.listFiles { f -> f.extension != "bat" }?.forEach { f ->
                    val targetFile = File(userBinDir, f.name)
                    if (targetFile.exists()) targetFile.delete()
                    log.info("creating symlink to ${targetFile.path}")
                    Files.createSymbolicLink(File(userBinDir, f.name).toPath(), f.toPath())
                }
            }
        } catch (e: Exception) {
            log.error(e.message)
            exitProcess(1)
        }
    }

    fun untarFile(fileToUntar: File, destFolder: File) : String {
        val destRoots = mutableSetOf<String>()
        BufferedInputStream(FileInputStream(fileToUntar)).use { fin ->
            TarArchiveInputStream(fin).use { ais ->
                var entry : TarArchiveEntry? = ais.nextTarEntry
                while (entry != null) {
                    //println("${entry.name} ${entry.mode.toString(8).substring(3)}")
                    destRoots.add(entry.name.split("/").first())
                    val outputFile = File(destFolder, entry.name)
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
        return destRoots.first()
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

}
