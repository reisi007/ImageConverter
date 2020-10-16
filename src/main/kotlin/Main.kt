package at.reisishot.mintha;

import kotlinx.html.div
import kotlinx.html.stream.appendHTML
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.function.Supplier
import java.util.stream.Collectors

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        // Img Size (longest side) to Quality (in percent)
        val config = listOf(100 to 0.2f, 350 to 0.25f, 500 to 0.25f, 800 to 0.25f, 1300 to 0.25f, 2000 to 0.3f, 3000 to 0.35f)
        val parallelism = 4

        val workingDir = args.getOrElse(0) { "." }
        val basePath = Paths.get(workingDir).toAbsolutePath()
        val outPath = basePath.resolve("out")
        val htmlOut = basePath.resolve("html")
        Files.createDirectories(outPath)
        Files.createDirectories(htmlOut)

        val foundFiles = resolveOutFileDates(outPath)
        resolveOutFileDates(basePath)

        Executors.newFixedThreadPool(parallelism).use { executor ->
            println("Starting execution")

            listFiles(workingDir)
                    .map { it.toPath() }
                    .filter { Files.isRegularFile(it) }
                    .map { makeAbsolute(basePath, it) }
                    .filter {
                        !it.fileName.toString().endsWith("jar", true) &&
                                foundFiles[it.filenameWithoutExtension]?.let { oldestOutFile ->
                                    Files.getLastModifiedTime(it).let { inFile ->
                                        inFile > oldestOutFile
                                    }
                                } ?: true
                    }
                    .flatMap { createJobs(it, outPath, config) }
                    .map { (p, it) -> p to CompletableFuture.supplyAsync(it, executor) }
                    .execute()
                    .map { (originalFile, future) -> originalFile to future.join() }
                    .toMapWithListValues()
                    .createHtmlHelp(htmlOut)

            println("Finished execution")
        }
    }

    private fun listFiles(workingDir: String) = File(workingDir)
            .listFiles()
            ?.asSequence()
            ?: throw IllegalStateException("Folder is not valid!")


    private fun createJobs(imgPath: Path, outPath: Path, config: Collection<Pair<Int, Float>>)
    // Convert image
            = config.asSequence()
            .map { (size, quality) ->
                imgPath to Supplier {
                    createJobs(imgPath, outPath, size, quality, "jpg") to
                            createJobs(imgPath, outPath, size, quality, "webp")
                }
            }


    private fun Map<Path, List<Pair<Path, Path>>>.createHtmlHelp(outPath: Path) =
            forEach { (inFile, outFiles) ->
                Files.newBufferedWriter(
                        outPath.resolve(inFile.filenameWithoutExtension + ".embed.html"),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
                ).use {
                    val prefix = "/img/"
                    it.appendHTML().div("lazy") {
                        attributes["data-alt"] = ">>> Alternativtext <<<"
                        attributes["data-sizes"] = outFiles.size.toString()
                        outFiles.forEachIndexed { idx, (jpgOut, webPOut) ->
                            jpgOut.readJpgMetadata()?.let {
                                attributes["data-$idx"] = """{"jpg":"${prefix + jpgOut.fileName}","webp":"${prefix + webPOut.fileName}","w":${it.imageWidth},"h":${it.imageHeight}}"""
                            }

                        }
                    }
                }
            }

    private fun createJobs(imgPath: Path, outPath: Path, size: Int, quality: Float, extension: String): Path {
        val outFile = outPath.resolve(imgPath.filenameWithoutExtension + '_' + size + '.' + extension).normalize()
        arrayOf(
                "magick", imgPath.toString(),
                "-quality", "${(quality * 100).toInt()}",
                "-resize", "${size}x${size}>",
                "-strip",
                "-sampling-factor", "4:1:1",
                "-interlace", "Plane",
                outFile.toString()
        ).runAndWaitOnConsole()
        return outFile
    }


    private fun resolveOutFileDates(outPath: Path): Map<String, FileTime> =
            if (Files.exists(outPath)) {
                Files.list(outPath)
                        .map { it.filenameWithoutExtension to Files.getLastModifiedTime(it) }
                        .collect(Collectors.toMap({ it.first }, { it.second }, { a, b -> if (a < b) a else b }))
            } else emptyMap()


    fun makeAbsolute(base: Path, target: Path): Path =
            if (target.isAbsolute)
                target.normalize()
            else
                base.resolve(target).normalize()


}