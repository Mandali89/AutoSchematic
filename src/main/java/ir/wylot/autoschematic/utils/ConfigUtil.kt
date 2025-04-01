package ir.wylot.autoschematic.utils

import ir.wylot.autoschematic.AutoSchematic
import java.io.File

data class ConfigData(
    val schematicFile: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val loadOnStartup: Boolean,
    val asyncOperations: Boolean,
    val maxBlocksPerTick: Int,
    val skipLightUpdates: Boolean,
    val skipPhysics: Boolean,
    val chunkSize: Int,
    val timeoutSeconds: Int,
    val ignoreAirBlocks: Boolean,
    val schematicsFolder: String
)

object ConfigUtil {
    private var initialized = false

    fun loadConfig(plugin: AutoSchematic): ConfigData {
        if (initialized) {
            return fromExistingConfig(plugin)
        }
        initialized = true

        plugin.saveDefaultConfig()
        val config = plugin.config

        val schematicsFolder = config.getString("schematics-folder", "schematics")!!
        File(plugin.dataFolder, schematicsFolder).mkdirs()

        return ConfigData(
            schematicFile = config.getString("schematic-file", "$schematicsFolder/spawn.schem")!!,
            x = config.getInt("spawn-x", 0),
            y = config.getInt("spawn-y", 0),
            z = config.getInt("spawn-z", 0),
            loadOnStartup = config.getBoolean("load-on-startup", true),
            asyncOperations = config.getBoolean("performance.async-operations", true),
            maxBlocksPerTick = config.getInt("performance.max-blocks-per-tick", 1000).coerceAtLeast(100),
            skipLightUpdates = config.getBoolean("performance.skip-light-updates", true),
            skipPhysics = config.getBoolean("performance.skip-physics", true),
            chunkSize = config.getInt("performance.chunk-size", 16).coerceIn(4, 32),
            timeoutSeconds = config.getInt("performance.timeout-seconds", 30).coerceIn(10, 300),
            ignoreAirBlocks = config.getBoolean("performance.ignore-air-blocks", true),
            schematicsFolder = schematicsFolder
        )
    }

    private fun fromExistingConfig(plugin: AutoSchematic): ConfigData {
        return ConfigData(
            schematicFile = plugin.config.getString("schematic-file", "schematics/spawn.schem")!!,
            x = plugin.config.getInt("spawn-x", 0),
            y = plugin.config.getInt("spawn-y", 0),
            z = plugin.config.getInt("spawn-z", 0),
            loadOnStartup = plugin.config.getBoolean("load-on-startup", true),
            asyncOperations = plugin.config.getBoolean("performance.async-operations", true),
            maxBlocksPerTick = plugin.config.getInt("performance.max-blocks-per-tick", 1000).coerceAtLeast(100),
            skipLightUpdates = plugin.config.getBoolean("performance.skip-light-updates", true),
            skipPhysics = plugin.config.getBoolean("performance.skip-physics", true),
            chunkSize = plugin.config.getInt("performance.chunk-size", 16).coerceIn(4, 32),
            timeoutSeconds = plugin.config.getInt("performance.timeout-seconds", 30).coerceIn(10, 300),
            ignoreAirBlocks = plugin.config.getBoolean("performance.ignore-air-blocks", true),
            schematicsFolder = plugin.config.getString("schematics-folder", "schematics")!!
        )
    }
}