package io.knowledgeos.vault

import kotlinx.coroutines.*
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*

class VaultWatcher(
    private val vaultPath: Path,
    private val vaultReader: VaultReader,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    var onCreated: ((Document) -> Unit)? = null
    var onModified: ((Document) -> Unit)? = null
    var onDeleted: ((Path) -> Unit)? = null

    private var watchService: WatchService? = null
    private var watchJob: Job? = null
    private val watchedDirs = mutableSetOf<Path>()

    fun start() {
        watchService = FileSystems.getDefault().newWatchService()
        registerAll(vaultPath)
        watchJob = scope.launch {
            val watcher = watchService ?: return@launch
            while (isActive) {
                try {
                    val key = withContext(Dispatchers.IO) { watcher.poll() } ?: run {
                        delay(100)
                        continue
                    }
                    val dir = key.watchable() as Path
                    for (event in key.pollEvents()) {
                        val kind = event.kind()
                        if (kind == OVERFLOW) continue
                        val filename = event.context() as? Path ?: continue
                        val fullPath = dir.resolve(filename)
                        if (!fullPath.toString().endsWith(".md")) continue

                        when (kind) {
                            ENTRY_CREATE -> {
                                registerNested(fullPath)
                                handleCreate(fullPath)
                            }
                            ENTRY_MODIFY -> handleModify(fullPath)
                            ENTRY_DELETE -> handleDelete(fullPath)
                        }
                    }
                    if (!key.reset()) break
                } catch (e: ClosedWatchServiceException) {
                    break
                } catch (e: CancellationException) {
                    break
                }
            }
        }
    }

    fun stop() {
        watchJob?.cancel()
        watchService?.close()
    }

    private fun registerAll(dir: Path) {
        if (Files.isDirectory(dir) && dir !in watchedDirs) {
            try {
                dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
                watchedDirs.add(dir)
                Files.list(dir).filter { Files.isDirectory(it) }.forEach { registerAll(it) }
            } catch (_: Exception) { }
        }
    }

    private fun registerNested(path: Path) {
        if (Files.isDirectory(path)) {
            registerAll(path)
        }
    }

    private fun handleCreate(path: Path) {
        try {
            val doc = vaultReader.readFile(path)
            onCreated?.invoke(doc)
        } catch (_: Exception) { }
    }

    private fun handleModify(path: Path) {
        try {
            val doc = vaultReader.readFile(path)
            onModified?.invoke(doc)
        } catch (_: Exception) { }
    }

    private fun handleDelete(path: Path) {
        val relative = vaultPath.relativize(path)
        onDeleted?.invoke(relative)
    }
}
