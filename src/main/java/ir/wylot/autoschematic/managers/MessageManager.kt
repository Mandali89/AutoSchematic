package ir.wylot.autoschematic.managers

import ir.wylot.autoschematic.AutoSchematic
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object MessageManager {
    private lateinit var plugin: AutoSchematic
    private lateinit var messages: YamlConfiguration
    private var initialized = false

    fun initialize(plugin: AutoSchematic) {
        if (initialized) return
        initialized = true

        this.plugin = plugin
        reload()
    }

    fun reload() {
        val messagesFile = File(plugin.dataFolder, "messages.yml")
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false)
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile)
    }

    private fun getMessage(key: String): String {
        return ChatColor.translateAlternateColorCodes('&', messages.getString(key, "&cMissing message: $key")!!)
    }

    private fun getMessageList(key: String): List<String> {
        return messages.getStringList(key).map { ChatColor.translateAlternateColorCodes('&', it) }
    }

    private fun format(message: String, vararg replacements: Pair<String, String>): String {
        var result = message
        replacements.forEach { (key, value) ->
            result = result.replace("%$key%", value)
        }
        return result
    }

    fun send(sender: CommandSender, key: String, vararg replacements: Pair<String, String>) {
        val prefix = getMessage("prefix")
        val message = format(getMessage(key), *replacements)
        sender.sendMessage("$prefix$message")
    }

    fun sendList(sender: CommandSender, key: String) {
        getMessageList(key).forEach { sender.sendMessage(it) }
    }

    fun sendNoPermission(sender: CommandSender) = send(sender, "commands.no-permission")
    fun sendReloadSuccess(sender: CommandSender) = send(sender, "commands.reload-success")
    fun sendHelp(sender: CommandSender) = sendList(sender, "commands.help")
    fun sendLoadUsage(sender: CommandSender) = send(sender, "load.usage")
    fun sendPlayerOnly(sender: CommandSender) = send(sender, "load.player-only")
    fun sendInvalidLocation(sender: CommandSender) = send(sender, "load.invalid-location")
    fun sendLoadSuccess(sender: CommandSender, x: Int, y: Int, z: Int) =
        send(sender, "load.success", "x" to x.toString(), "y" to y.toString(), "z" to z.toString())
    fun sendFileNotFound(sender: CommandSender, file: String) = send(sender, "load.file-not-found", "file" to file)
    fun sendError(sender: CommandSender, error: String) = send(sender, "errors.general", "error" to error)
    fun sendTimeout(sender: CommandSender, seconds: Int) = send(sender, "errors.timeout", "seconds" to seconds.toString())
    fun sendListHeader(sender: CommandSender, count: Int) { send(sender, "commands.list.header", "count" to count.toString()) }
    fun sendListEntry(sender: CommandSender, schematic: String) { send(sender, "commands.list.entry", "schematic" to schematic) }
    fun sendListEmpty(sender: CommandSender) {send(sender, "commands.list.empty") }
}