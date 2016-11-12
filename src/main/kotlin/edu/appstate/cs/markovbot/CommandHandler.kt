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

        commandMap["chance"] = fun(args: CommandArgs): Boolean {
            if(args.args.size != 1)
                return false
            val sendNick = args.event.user.nick
            val lowerNick = toIrcLowerCase(sendNick)
            val bot = args.event.bot
            try {
                args.listener.userChances[lowerNick] = Math.min(args.args[0].toDouble(), args.listener.randomChance)
            } catch(ex: NumberFormatException) {
                return false
            }
            bot.sendIRC().message(sendNick, "Your random message chance has been set to " +
                    "${args.listener.userChances[lowerNick]}.")
            return true
        }

        fun force(args: CommandArgs, all: Boolean = false): Boolean {
            val bot = args.event.bot
            val sendNick = args.event.user.nick
            val lowerNick = toIrcLowerCase(sendNick)
            val markovChain = if(all)
                args.listener.allChain
            else
                args.listener.chainMap[lowerNick]
            val maxSentences = args.listener.maxSentences
            var sentenceCount = if(args.args.size == 1) {
                try {
                    Math.min(maxSentences, Math.abs(args.args[0].toInt()))
                } catch(ex: NumberFormatException) {
                    1
                }
            } else {
                1
            }
            if(markovChain != null) {
                var result = ""
                while(sentenceCount > 0) {
                    result += markovChain.randomSentence()
                    sentenceCount--
                }
                bot.sendIRC().message(args.listener.channel, "$sendNick: $result")
            }
            return true
        }

        commandMap["force"] = fun(args: CommandArgs): Boolean { return force(args) }
        commandMap["all"]   = fun(args: CommandArgs): Boolean { return force(args, true) }

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
            val randomChance = args.listener.randomChance
            val helpMessage = """usage: !markov [COMMAND]
where COMMANDs consist of:
    ignore  - stops the markov bot from listening to your messages
    listen  - starts the bot listening to your messages again
    chance  - sets the random chance that the bot will respond to you.
              Valid values are any real number between 0.0 and $randomChance.
    force   - forces a markov chain to be generated based on what the markov bot has seen from you
    all     - forces a markov chain to be generated based on the collective of everything everyone has said
    about   - about the markov bot and version information
    """

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