package at.reisishot.mintha

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.jpeg.JpegDirectory
import java.nio.file.Path
import java.util.concurrent.ExecutorService

fun <T> Sequence<T>.peek(peeker: (T) -> Unit) = map {
    peeker(it)
    it
}

fun <T> Sequence<T>.execute() = toList().asSequence()

val Path.filenameWithoutExtension
    get() = fileName.toString().substringBeforeLast('.')

fun Array<String>.runAndWaitOnConsole() =
        ProcessBuilder(*this)
                .inheritIO()
                .start()
                .waitFor()


fun <K, V> Sequence<Pair<K, V>>.toMapWithListValues(): Map<K, List<V>> {
    val map = mutableMapOf<K, MutableList<V>>()
    forEach { (k, v) ->
        map.computeIfAbsent(k, { mutableListOf() }).add(v)
    }

    return map
}

fun Path.readJpgMetadata(): JpegDirectory? = ImageMetadataReader
        .readMetadata(toFile())
        .getFirstDirectoryOfType(JpegDirectory::class.java)

fun ExecutorService.use(doSomething: (ExecutorService) -> Unit) = try {
    doSomething(this)
} finally {
    shutdown()
}