package io.knowledgeos.vault

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

private val log = KotlinLogging.logger {}

class VaultWatcher(
    private val vaultPath: Path,
    private val vaultReader: VaultReader,
    private val pollIntervalMs: Long = 1_000,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    var onCreated: ((Document) -> Unit)? = null
    var onModified: ((Document) -> Unit)? = null
    var onDeleted: ((Path) -> Unit)? = null

    private var watchJob: Job? = null
    private val snapshot = mutableMapOf<Path, FileTime>()

    fun start() {
        snapshot.clear()
        snapshot.putAll(scan())
        log.info { "VaultWatcher started polling $vaultPath every ${pollIntervalMs}ms (${snapshot.size} files in snapshot)" }
        watchJob = scope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                poll()
            }
        }
    }

    fun stop() {
        watchJob?.cancel()
    }

    private fun poll() {
        val current = scan()

        val created = current.keys - snapshot.keys
        val deleted = snapshot.keys - current.keys
        val modified = current.keys.intersect(snapshot.keys)
            .filter { current[it] != snapshot[it] }

        for (path in created) {
            log.debug { "VaultWatcher: created $path" }
            try {
                onCreated?.invoke(vaultReader.readFile(path))
            } catch (e: Exception) {
                log.warn(e) { "VaultWatcher: failed to read created file $path" }
            }
        }
        for (path in modified) {
            log.debug { "VaultWatcher: modified $path" }
            try {
                onModified?.invoke(vaultReader.readFile(path))
            } catch (e: Exception) {
                log.warn(e) { "VaultWatcher: failed to read modified file $path" }
            }
        }
        for (path in deleted) {
            log.debug { "VaultWatcher: deleted $path" }
            onDeleted?.invoke(vaultPath.relativize(path))
        }

        snapshot.clear()
        snapshot.putAll(current)
    }

    private fun scan(): Map<Path, FileTime> {
        if (!Files.exists(vaultPath)) return emptyMap()
        return try {
            Files.walk(vaultPath)
                .filter { it.isRegularFile() && it.name.endsWith(".md") }
                .toList()
                .associateWith { Files.getLastModifiedTime(it) }
        } catch (e: Exception) {
            log.warn(e) { "VaultWatcher: scan failed" }
            emptyMap()
        }
    }
}
