package sealmc.swe3tie.sealcore.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import sealmc.swe3tie.sealcore.SealCore
import sealmc.swe3tie.sealcore.config.ConfigManager
import sealmc.swe3tie.sealcore.module.ModuleReport
import sealmc.swe3tie.sealcore.module.ModuleState

/**
 * The `/sealcore` command: framework introspection and maintenance.
 *
 * Feature modules get their own commands; this one only covers the framework
 * itself, which is what an operator needs when something looks wrong.
 */
class SealCoreCommand(private val plugin: SealCore) : CommandExecutor, TabCompleter {

    private val root: CommandNode = CommandNode(
        name = "sealcore",
        description = "SealCore framework commands",
        permission = "sealcore.command",
    ).apply {
        child("version", "Show the loaded version", "sealcore.command.version") { context ->
            context.replySuccess("SealCore ${plugin.pluginMeta.version} on ${plugin.platform.displayName}")
        }
        child("reload", "Reload config, modules and language", "sealcore.command.reload") { context ->
            reportReload(context, plugin.reloadPlugin())
        }
        child("modules", "List module state", "sealcore.command.debug") { context ->
            printModules(context)
        }
        child("debug", "Print framework state", "sealcore.command.debug", usage = "<platform|economy|storage|gui|hud|config>") { context ->
            printDebug(context)
        }
    }

    private val dispatcher = CommandDispatcher(root, plugin.logger)

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        dispatcher.dispatch(sender, label, args.toList())
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): List<String> = dispatcher.complete(sender, args.toList())

    /**
     * Reports what a reload actually did. A module that failed keeps its last
     * good settings, so saying "reloaded" alone would be a lie the operator
     * cannot act on.
     */
    private fun reportReload(context: CommandContext, report: ConfigManager.ReloadReport) {
        val active = report.modules.count { it.state == ModuleState.ACTIVE }
        val header = "SealCore reloaded: $active/${report.modules.size} modules active."
        if (report.clean) {
            context.replySuccess(header)
        } else {
            context.replyError(header)
        }
        for (module in report.broken) {
            context.replyError("${module.id} ${module.state.name.lowercase()}: ${module.problems.joinToString("; ")}")
        }
        for (key in report.restartRequired) {
            context.replyError("config.yml: '$key' changed and applies on the next restart.")
        }
        for (module in report.modules) {
            for (key in module.restartRequired) {
                context.replyError("${module.id}: '$key' changed and applies on the next restart.")
            }
        }
    }

    private fun printModules(context: CommandContext) {
        val reports = plugin.modules.reports()
        if (reports.isEmpty()) {
            context.reply("<gray>No modules registered.</gray>")
            return
        }
        context.reply("<gray>Modules:</gray> <white>${plugin.modules.activeCount()}/${reports.size} active</white>")
        for (report in reports) {
            context.reply(describe(report))
        }
    }

    private fun describe(report: ModuleReport): String {
        val colour = when (report.state) {
            ModuleState.ACTIVE -> "<green>"
            ModuleState.DISABLED -> "<gray>"
            else -> "<red>"
        }
        val head = "$colour${report.state.name.lowercase()}</color> <white>${report.id}</white> <dark_gray>${report.file}</dark_gray>"
        return if (report.problems.isEmpty()) head else "$head <red>${report.problems.joinToString("; ")}</red>"
    }

    private fun printDebug(context: CommandContext) {
        when (context.string(0).lowercase()) {
            "platform" -> context.reply(
                "<gray>Platform:</gray> <white>${plugin.platform.displayName}</white> " +
                    "<gray>MC:</gray> <white>${plugin.server.minecraftVersion}</white>",
            )

            "economy" -> {
                val service = plugin.economy
                context.reply("<gray>Provider:</gray> <white>${service.provider.displayName}</white>")
                context.reply("<gray>Available:</gray> <white>${service.isReady}</white>")
                context.reply("<gray>Currencies:</gray> <white>${service.knownCurrencies().joinToString()}</white>")
            }

            "storage" -> {
                context.reply("<gray>Storage:</gray> <white>${plugin.config.storage.type}</white>")
                context.reply("<gray>Open:</gray> <white>${plugin.isStorageReady}</white>")
                if (plugin.isStorageReady) {
                    context.reply("<gray>Dialect:</gray> <white>${plugin.database.dialect.id}</white>")
                    context.reply("<gray>Players:</gray> <white>${plugin.players.count()}</white>")
                }
            }

            "gui" -> {
                context.reply("<gray>PacketEvents:</gray> <white>${plugin.bridge.packetEventsVersion}</white>")
                context.reply("<gray>Enabled:</gray> <white>${plugin.hasGui}</white>")
                if (plugin.hasGui) context.reply("<gray>Open screens:</gray> <white>${plugin.gui.openSessions}</white>")
            }

            "hud" -> context.reply(
                "<gray>HUD enabled:</gray> <white>${plugin.hasHud}</white> " +
                    "<gray>interval:</gray> <white>${plugin.config.hud.updateIntervalTicks}t</white>",
            )

            "config" -> {
                val messages = plugin.messages
                context.reply("<gray>Language:</gray> <white>${messages.language}</white>")
                context.reply(
                    "<gray>Fallback:</gray> <white>${messages.fallbackLanguage ?: "none"}</white>",
                )
                context.reply(
                    "<gray>Modules:</gray> <white>${plugin.modules.activeCount()}/${plugin.modules.ids().size} active</white>",
                )
                val pending = plugin.configManager.restartPending()
                context.reply(
                    "<gray>Pending restart:</gray> <white>${pending.joinToString().ifEmpty { "nothing" }}</white>",
                )
            }

            else -> context.replyError("Usage: /sealcore debug <platform|economy|storage|gui|hud>")
        }
    }
}
