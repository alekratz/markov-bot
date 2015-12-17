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
        commandMap["ignore"] = fun(args: CommandArgs): Boolean {
            val bot = args.event.bot
            val sendNick = args.event.user.nick
            args.listener.ignoreList.add(sendNick)
            bot.sendIRC().message(sendNick, "You are now being ignored. You can reverse this by " +
                    "using !markov-listen.")
            return true
        }

        commandMap["listen"] = fun(args: CommandArgs): Boolean {
            val bot = args.event.bot
            val sendNick = args.event.user.nick
            args.listener.ignoreList.remove(sendNick)
            bot.sendIRC().message(sendNick, "You are now being recorded. You can reverse this by " +
                    "using !markov-ignore.")
            return true
        }

        commandMap["force"] = fun(args: CommandArgs): Boolean {
            val bot = args.event.bot
            val sendNick = args.event.user.nick
            val markovChain = args.listener.chainMap[sendNick]
            if(markovChain != null) {
                val sentence = markovChain.generateSentence()
                bot.sendIRC().message(args.listener.channel, "$sendNick: $sentence")
            }
            return true
        }

        commandMap["all"] = fun(args: CommandArgs): Boolean {
            val bot = args.event.bot
            val sendNick = args.event.user.nick
            val markovChain = args.listener.allChain
            val sentence = markovChain.generateSentence()
            bot.sendIRC().message(args.listener.channel, "$sendNick: $sentence")
            return true
        }

        commandMap["about"] = fun(args: CommandArgs): Boolean {
            val bot = args.event.bot
            val aboutMessage = """markov-bot ${VersionInfo.STR}
source located at https://github.com/alekratz/markov-bot"""

            val sendNick = args.event.user.nick
            for(line in aboutMessage.split("\n"))
                bot.sendIRC().message(sendNick, line)
            return true
        }

        commandMap["help"] = fun(args: CommandArgs): Boolean {
            val bot = args.event.bot
            val helpMessage = """usage: !markov [COMMAND]
where COMMANDs consist of:
    ignore  - stops the markov bot from listening to your messages
    listen  - starts the bot listening to your messages again
    force   - forces a markov chain to be generated based on what the markov bot has seen from you
    all     - forces a markov chain to be generated based on the collective of everything everyone has said
    about   - about the markov bot and version information"""

            val sendNick = args.event.user.nick
            for(line in helpMessage.split("\n"))
                bot.sendIRC().message(sendNick, line)
            return true
        }
    }

    fun doCommand(event: MessageEvent<PircBotX>, listener: MessageListener): Boolean {
        val commands = event.message.split(" ")
        fun catchall(): Boolean {
            return commandMap["help"]!!(CommandArgs(commands.drop(2).toTypedArray(), event, listener))
        }

        if(event.channel.name != listener.channel) {
            return false
        } else if (commands.size < 1) {
            return catchall()
        } else if (commands[0].startsWith("!markov-")) {
            val bot = event.bot
            val sendNick = event.user.nick
            bot.sendIRC().message(listener.channel, "$sendNick : !markov-* commands have been deprecated. Use the new ones instead.")
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