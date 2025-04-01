package ir.wylot.autoschematic

import ir.wylot.autoschematic.managers.MessageManager
import ir.wylot.autoschematic.managers.SchematicManager
import ir.wylot.autoschematic.utils.ConfigUtil
import ir.wylot.autoschematic.commands.SchematicCommand
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class AutoSchematic : JavaPlugin() {
    companion object {
        lateinit var instance: AutoSchematic
            private set
    }

    override fun onEnable() {
        instance = this

        // Initialize configuration
        saveDefaultConfig()
        val configData = ConfigUtil.loadConfig(this)

        // initialize messages
        MessageManager.initialize(this)

        // Register command
        getCommand("autoschematic")?.let { cmd ->
            cmd.setExecutor(SchematicCommand(this))
            cmd.tabCompleter = SchematicCommand(this)
        } ?: logger.severe("Failed to register command!")

        if (configData.loadOnStartup) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(this, ::loadStartupSchematic, 100L)
        }
    }

    private fun loadStartupSchematic() {
        val configData = ConfigUtil.loadConfig(this)
        try {
            SchematicManager.loadSchematic(
                this,
                configData.schematicFile,
                configData.x,
                configData.y,
                configData.z
            ).thenAccept { success ->
                if (!success) {
                    logger.severe("Failed to load startup schematic!")
                }
            }
        } catch (e: Exception) {
            logger.severe("Failed to load schematic: ${e.message}")
        }
    }

    override fun onDisable() {
    }
}