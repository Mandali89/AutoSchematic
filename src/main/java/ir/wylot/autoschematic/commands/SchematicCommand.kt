package ir.wylot.autoschematic.commands

import ir.wylot.autoschematic.AutoSchematic
import ir.wylot.autoschematic.managers.MessageManager
import ir.wylot.autoschematic.managers.SchematicManager
import org.bukkit.Location
import org.bukkit.command.Command as BukkitCommand
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player as BukkitPlayer
import kotlin.math.floor

class SchematicCommand(private val plugin: AutoSchematic) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: BukkitCommand,
        label: String,
        args: Array<String>
    ): Boolean {
        when {
            args.isEmpty() -> {
                MessageManager.sendHelp(sender)
                return true
            }
            args[0].equals("reload", ignoreCase = true) -> {
                if (!sender.hasPermission("autoschematic.reload")) {
                    MessageManager.sendNoPermission(sender)
                    return true
                }
                plugin.reloadConfig()
                MessageManager.reload()
                MessageManager.sendReloadSuccess(sender)
                return true
            }
            args[0].equals("load", ignoreCase = true) -> {
                if (!sender.hasPermission("autoschematic.load")) {
                    MessageManager.sendNoPermission(sender)
                    return true
                }
                return handleLoadCommand(sender, args)
            }
            args[0].equals("list", ignoreCase = true) -> {
                handleListCommand(sender)
                return true
            }
            else -> {
                MessageManager.sendHelp(sender)
                return false
            }
        }
    }

    private fun handleListCommand(sender: CommandSender) {
        val schematics = SchematicManager.getAvailableSchematics(plugin)
        if (schematics.isEmpty()) {
            MessageManager.sendListEmpty(sender)
        } else {
            MessageManager.sendListHeader(sender, schematics.size)
            schematics.sorted().forEach { schematic ->
                MessageManager.sendListEntry(sender, schematic)
            }
        }
    }

    private fun handleLoadCommand(sender: CommandSender, args: Array<String>): Boolean {
        if (args.size < 2) {
            MessageManager.sendLoadUsage(sender)
            return false
        }

        val fileName = args[1]
        val location = when {
            args.size >= 3 -> parseLocation(sender, args[2])
            sender is BukkitPlayer -> sender.location
            else -> {
                MessageManager.sendPlayerOnly(sender)
                return false
            }
        } ?: run {
            MessageManager.sendInvalidLocation(sender)
            return false
        }

        val blockLocation = location.apply {
            x = floor(x)
            y = floor(y)
            z = floor(z)
        }

        SchematicManager.loadSchematic(
            plugin,
            fileName,
            blockLocation.blockX,
            blockLocation.blockY,
            blockLocation.blockZ,
            sender
        )
        return true
    }

    private fun parseLocation(sender: CommandSender, locationArg: String): Location? {
        if (locationArg.equals("here", ignoreCase = true)) {
            return (sender as? BukkitPlayer)?.location
        }

        val coords = locationArg.split(",").map { it.trim() }
        if (coords.size != 3) return null

        return try {
            val world = (sender as? BukkitPlayer)?.world
            Location(
                world,
                coords[0].toDouble(),
                coords[1].toDouble(),
                coords[2].toDouble()
            )
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: BukkitCommand,
        alias: String,
        args: Array<String>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("load", "list", "reload").filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> {
                if (args[0].equals("load", ignoreCase = true)) {
                    listOf("<File Name>") + SchematicManager.getAvailableSchematics(plugin)
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                } else emptyList()
            }
            3 -> {
                if (args[0].equals("load", ignoreCase = true) && sender is BukkitPlayer) {
                    listOf(
                        "here",
                        "${sender.location.blockX},${sender.location.blockY},${sender.location.blockZ}"
                    ).filter { it.startsWith(args[2], ignoreCase = true) }
                } else emptyList()
            }
            else -> emptyList()
        }
    }
}