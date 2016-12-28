package edu.appstate.cs.markovbot

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
        fun catchall(): Boolean {
            return commandMap["help"]!!(CommandArgs(commands.drop(2).toTypedArray(), event, listener))
        }

        if(event.channel.name != listener.channel) {
            return false
        } else if (commands.isEmpty()) {
            return catchall()
        } else if (commands[0].startsWith("!markov-")) {
            val bot = event.bot
            val sendNick = event.user.nick
            bot.sendIRC().message(listener.channel,
                    "$sendNick : !markov-* commands have been deprecated. Use the new ones instead.")
            return catchall()
        } else if (commands[0] != "!markov") {
            return false
        } else if(commands.size < 2) {
            return catchall()
        }

        // Split up the commands
        val command = commands[1]
        val commandEvent = commandMap[command] ?: return catchall()

        return commandEvent(CommandArgs(commands.drop(2).toTypedArray(), event, listener))
    }
}