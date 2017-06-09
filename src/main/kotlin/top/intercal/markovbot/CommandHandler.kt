package top.intercal.markovbot

import org.pircbotx.PircBotX
import org.pircbotx.hooks.events.MessageEvent
import java.util.*

data class CommandArgs(val args: Array<String>, val event: MessageEvent<PircBotX>, val listener: MessageListener)

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 */
object CommandHandler {
    val commandMap = HashMap<String, (args: CommandArgs) -> Boolean>()

    init {
        // Set up commands here
        commandMap["ignore"] = ::ignore
        commandMap["listen"] = ::listen
        commandMap["chance"] = ::chance
        commandMap["force"] = fun(args: CommandArgs): Boolean { return force(args) }
        commandMap["all"]   = fun(args: CommandArgs): Boolean { return force(args, true) }
        commandMap["about"] = ::about
        commandMap["help"] = ::help
        commandMap["status"] = ::status
    }

    fun doCommand(event: MessageEvent<PircBotX>, listener: MessageListener): Boolean {
        val commands = event.message.split(" ")

        if(event.channel.name != listener.channel) {
            return false
        } else if (commands.isEmpty()) {
            return false
        } else if (commands[0] != "!markov") {
            return false
        } else if(commands.size < 2) {
            return false
        }

        // Split up the commands
        val command = commands[1]
        val commandEvent = commandMap[command] ?: return false

        return commandEvent(CommandArgs(commands.drop(2).toTypedArray(), event, listener))
    }
}