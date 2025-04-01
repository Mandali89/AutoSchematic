package ir.wylot.autoschematic.managers

import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.session.ClipboardHolder
import ir.wylot.autoschematic.AutoSchematic
import ir.wylot.autoschematic.utils.ConfigUtil
import ir.wylot.autoschematic.utils.ConfigData
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CompletableFuture

object SchematicManager {
    private const val MIN_BLOCKS_PER_TICK = 100
    private const val DEFAULT_TIMEOUT_TICKS = 600L

    fun loadSchematic(
        plugin: AutoSchematic,
        fileName: String,
        x: Int,
        y: Int,
        z: Int,
        sender: CommandSender? = null
    ): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        val config = ConfigUtil.loadConfig(plugin)

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val world = Bukkit.getWorlds().firstOrNull() ?: throw IllegalStateException("No worlds available")
                val schematicFile = findSchematicFile(plugin, fileName)
                val clipboard = loadClipboard(schematicFile)

                if (shouldUseAsync(config, clipboard)) {
                    processSchematicChunked(plugin, clipboard, world, x, y, z, config, future, sender)
                } else {
                    processSchematicImmediate(plugin, clipboard, world, x, y, z, config, future, sender)
                }
            } catch (e: Exception) {
                when (e) {
                    is IllegalArgumentException -> {
                        sender?.let { MessageManager.sendFileNotFound(it, fileName) }
                        plugin.logger.severe("Schematic file not found: $fileName")
                    }
                    else -> {
                        sender?.let { MessageManager.sendError(it, e.message ?: "Unknown error") }
                        plugin.logger.severe("Load failed: ${e.message}")
                    }
                }
                future.complete(false)
            }
        })

        return future
    }

    private fun shouldUseAsync(config: ConfigData, clipboard: Clipboard): Boolean {
        if (!config.asyncOperations) return false
        val blockCount = clipboard.region.volume
        val chunkVolume = config.chunkSize.toLong() * config.chunkSize * 2L
        return blockCount > chunkVolume
    }

    private fun processSchematicChunked(
        plugin: AutoSchematic,
        clipboard: Clipboard,
        world: World,
        x: Int,
        y: Int,
        z: Int,
        config: ConfigData,
        future: CompletableFuture<Boolean>,
        sender: CommandSender? = null
    ) {
        val adapter = BukkitAdapter.adapt(world)
        val region = clipboard.region

        val editSession = WorldEdit.getInstance()
            .newEditSessionBuilder()
            .world(adapter)
            .build()

        try {
            val operation = ClipboardHolder(clipboard)
                .createPaste(editSession)
                .to(BlockVector3.at(x, y, z))
                .ignoreAirBlocks(config.ignoreAirBlocks)
                .build()

            val totalBlocks = region.volume
            var processedBlocks = 0L
            val maxBlocksPerTick = config.maxBlocksPerTick.toLong().coerceAtLeast(MIN_BLOCKS_PER_TICK.toLong())

            val task = object : org.bukkit.scheduler.BukkitRunnable() {
                override fun run() {
                    try {
                        var blocksThisTick = 0L
                        while (blocksThisTick < maxBlocksPerTick && processedBlocks < totalBlocks) {
                            Operations.complete(operation)
                            blocksThisTick += estimateBlocksProcessed()
                            processedBlocks = (processedBlocks + blocksThisTick).coerceAtMost(totalBlocks)
                        }

                        if (processedBlocks >= totalBlocks) {
                            completeOperation(plugin, editSession, x, y, z, future, true, sender)
                            cancel()
                        }
                    } catch (e: Exception) {
                        handleOperationError(plugin, editSession, future, e, sender)
                        cancel()
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L)

            val timeoutTicks = (config.timeoutSeconds * 20).toLong()
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (!future.isDone) {
                    task.cancel()
                    editSession.close()
                    sender?.let { MessageManager.sendTimeout(it, config.timeoutSeconds) }
                    plugin.logger.severe("Paste timed out after ${config.timeoutSeconds} seconds")
                    future.complete(false)
                }
            }, timeoutTicks)

        } catch (e: Exception) {
            handleOperationError(plugin, editSession, future, e, sender)
        }
    }

    private fun processSchematicImmediate(
        plugin: AutoSchematic,
        clipboard: Clipboard,
        world: World,
        x: Int,
        y: Int,
        z: Int,
        config: ConfigData,
        future: CompletableFuture<Boolean>,
        sender: CommandSender? = null
    ) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val editSession = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world))
                .build()

            try {
                val operation = ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(config.ignoreAirBlocks)
                    .build()

                Operations.complete(operation)
                completeOperation(plugin, editSession, x, y, z, future, true, sender)
            } catch (e: Exception) {
                handleOperationError(plugin, editSession, future, e, sender)
            }
        })
    }

    private fun completeOperation(
        plugin: AutoSchematic,
        editSession: EditSession,
        x: Int,
        y: Int,
        z: Int,
        future: CompletableFuture<Boolean>,
        success: Boolean,
        sender: CommandSender? = null
    ) {
        try {
            editSession.close()
            if (success) {
                sender?.let { MessageManager.sendLoadSuccess(it, x, y, z) }
                plugin.logger.info("Schematic pasted successfully at ($x, $y, $z)")
                future.complete(true)
            } else {
                future.complete(false)
            }
        } catch (e: Exception) {
            sender?.let { MessageManager.sendError(it, "Error closing edit session: ${e.message}") }
            plugin.logger.severe("Error closing edit session: ${e.message}")
            future.complete(false)
        }
    }

    private fun handleOperationError(
        plugin: AutoSchematic,
        editSession: EditSession,
        future: CompletableFuture<Boolean>,
        e: Exception,
        sender: CommandSender? = null
    ) {
        try {
            editSession.close()
        } catch (closeEx: Exception) {
            plugin.logger.warning("Error closing edit session: ${closeEx.message}")
        }
        sender?.let { MessageManager.sendError(it, e.message ?: "Unknown error during paste") }
        plugin.logger.severe("Paste failed: ${e.message}")
        future.complete(false)
    }

    private fun findSchematicFile(plugin: AutoSchematic, fileName: String): File {
        val exactFile = File(plugin.dataFolder, fileName)
        if (exactFile.exists()) return exactFile

        val schematicsFolder = File(plugin.dataFolder, "schematics")
        val schemFile = when {
            fileName.endsWith(".schematic") || fileName.endsWith(".schem") ->
                File(schematicsFolder, fileName)
            else -> {
                File(schematicsFolder, "$fileName.schematic").takeIf { it.exists() }
                    ?: File(schematicsFolder, "$fileName.schem")
            }
        }

        return schemFile.takeIf { it.exists() }
            ?: throw IllegalArgumentException("Schematic not found (tried: $fileName in schematics folder)")
    }

    private fun loadClipboard(file: File): Clipboard {
        return try {
            val format = ClipboardFormats.findByFile(file) ?: throw IllegalArgumentException("Unsupported format")
            FileInputStream(file).use { fis ->
                format.getReader(fis).use { reader ->
                    reader.read() ?: throw IllegalArgumentException("Empty schematic")
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load schematic: ${e.message}")
        }
    }

    private fun estimateBlocksProcessed(): Int = 10

    fun getAvailableSchematics(plugin: AutoSchematic): List<String> {
        val schematicsDir = File(plugin.dataFolder, ConfigUtil.loadConfig(plugin).schematicsFolder)
        if (!schematicsDir.exists()) return emptyList()

        return schematicsDir.listFiles()?.filter { file ->
            file.isFile && (file.name.endsWith(".schem") || file.name.endsWith(".schematic"))
        }?.map { it.name.substringBeforeLast(".") } ?: emptyList()
    }
}