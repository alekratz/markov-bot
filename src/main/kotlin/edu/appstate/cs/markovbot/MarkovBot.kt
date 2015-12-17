package edu.appstate.cs.markovbot

import org.pircbotx.*
import org.pircbotx.hooks.ListenerAdapter
import org.pircbotx.hooks.events.JoinEvent
import org.pircbotx.hooks.types.GenericMessageEvent
import java.io.File
import java.util.*
import javax.net.ssl.SSLSocketFactory

internal const val ALL_CHAIN = "/"

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 *     Documentation on pircbotx: http://thelq.github.io/pircbotx/latest/apidocs/
 */
class MarkovBot(saveEvery: Int, shouldSave: Boolean, saveDirectory: String, randomChance: Double) : ListenerAdapter<PircBotX>() {
    var present: Boolean = false
    val chainMap: HashMap<String, MarkovChain> = HashMap()
    val allChain: MarkovChain
        get() {
            synchronized(chainMap) {
                if (chainMap[ALL_CHAIN] == null) {
                    println("Constructing all-chain")
                    val allChain = MarkovChain()
                    for(nick in chainMap.keys)
                        allChain.merge(chainMap[nick])
                    chainMap[ALL_CHAIN] = allChain
                }
            }

            return chainMap[ALL_CHAIN]!!
        }
    val ignoreList: HashSet<String> = HashSet()
    val gen: Random = Random()

    val saveDirectory = saveDirectory
    val saver = MessageSaver(chainMap, saveDirectory, saveEvery)
    val saveThread = Thread(saver)
    val randomChance = randomChance

    val commandMap = HashMap<String, (Array<String>, GenericMessageEvent<PircBotX>) -> Boolean>()

    init {
        loadChains()

        if(shouldSave) {
            println("Starting save thread, saving every $saveEvery seconds")
            saveThread.start()
        }

        // Set up commands here
        commandMap["ignore"] = fun(command: Array<String>, event: GenericMessageEvent<PircBotX>): Boolean {
            val bot = event.bot
            val sendNick = event.user.nick
            ignoreList.add(sendNick)
            bot.sendIRC().message(sendNick, "You are now being ignored. You can reverse this by " +
                    "using !markov-listen.")
            return true
        }

        commandMap["listen"] = fun(command: Array<String>, event: GenericMessageEvent<PircBotX>): Boolean {
            val bot = event.bot
            val sendNick = event.user.nick
            ignoreList.remove(sendNick)
            bot.sendIRC().message(sendNick, "You are now being recorded. You can reverse this by " +
                    "using !markov-ignore.")
            return true
        }

        commandMap["force"] = fun(command: Array<String>, event: GenericMessageEvent<PircBotX>): Boolean {
            val bot = event.bot
            val sendNick = event.user.nick
            val channel = bot.getFirstChannel() ?: return false
            val markovChain = chainMap[sendNick]
            if(markovChain != null) {
                val sentence = markovChain.generateSentence()
                bot.sendIRC().message(channel, "$sendNick: $sentence")
            }
            return true
        }

        commandMap["all"] = fun(command: Array<String>, event: GenericMessageEvent<PircBotX>): Boolean {
            val bot = event.bot
            val sendNick = event.user.nick
            val channel = bot.getFirstChannel() ?: return false
            val markovChain = allChain
            val sentence = markovChain.generateSentence()
            bot.sendIRC().message(channel, "$sendNick: $sentence")
            return true
        }

        commandMap["about"] = fun(command: Array<String>, event: GenericMessageEvent<PircBotX>): Boolean {
            val bot = event.bot
            val aboutMessage = """markov-bot ${VersionInfo.STR}
source located at https://github.com/alekratz/markov-bot"""

            val sendNick = event.user.nick
            for(line in aboutMessage.split("\n"))
                bot.sendIRC().message(sendNick, line)
            return true
        }

        commandMap["help"] = fun(command: Array<String>, event: GenericMessageEvent<PircBotX>): Boolean {
            val bot = event.bot
            val helpMessage = """usage: !markov [COMMAND]
where COMMANDs consist of:
    ignore  - stops the markov bot from listening to your messages
    listen  - starts the bot listening to your messages again
    force   - forces a markov chain to be generated based on what the markov bot has seen from you
    all     - forces a markov chain to be generated based on the collective of everything everyone has said
    about   - about the markov bot and version information"""

            val sendNick = event.user.nick
            for(line in helpMessage.split("\n"))
                bot.sendIRC().message(sendNick, line)
            return true
        }
    }

    public override fun onGenericMessage(event: GenericMessageEvent<PircBotX>) {
        val serverName = event.bot.serverInfo.serverName
        println("$serverName ${event.user.nick} : ${event.message}")
        // if we're not in a room, don't bother listening
        if(!present)
            return

        // command was handled
        if(doCommand(event))
            return

        val name = event.user.nick

        // ensure that the message ends in some sort of symbol. otherwise paste in a period at the end
        val msg = if(event.message.endsWith(".") || event.message.endsWith("!") || event.message.endsWith("?")) {
            event.message
        } else {
            event.message + "."
        }

        synchronized(chainMap) {
            chainMap.putIfAbsent(name, MarkovChain())
            val chain = chainMap[name]!!
            chain.train(msg) // choo choo
            saver.needsUpdate = true
        }

        synchronized(allChain) {
            allChain.train(msg)
        }

        // random chance that a markov chain will be generated
        if(gen.nextDouble() < randomChance) {
            val channel = event.bot.getFirstChannel() ?: return
            val sendNick = event.user.nick
            val markovChain = chainMap[sendNick]
            if(markovChain != null) {
                val sentence = markovChain.generateSentence()
                event.bot.sendIRC().message(channel, "$sendNick: $sentence")
            }
        }
    }

    public override fun onJoin(event: JoinEvent<PircBotX>) {
        if(event.user.nick == event.bot.nick) {
            present = true
            println("Joined channel ${event.bot.getFirstChannel()}")
        }
    }

    public fun doCommand(event: GenericMessageEvent<PircBotX>): Boolean {
        val commands = event.message.split(" ")
        fun catchall(): Boolean {
            return commandMap["help"]!!(commands.drop(2).toTypedArray(), event)
        }

        if (commands.size < 1) {
            return catchall()
        } else if (commands[0].startsWith("!markov-")) {
            val bot = event.bot
            val sendNick = event.user.nick
            val channel = bot.getFirstChannel() ?: return false
            bot.sendIRC().message(channel, "$sendNick : !markov-* commands have been deprecated. Use the new ones instead.")
            return catchall()
        } else if (commands[0] != "!markov") {
            return false
        } else if(commands.size < 2) {
            return catchall()
        }

        // Split up the commands
        val command = commands[1]
        val commandEvent = commandMap[command] ?: return catchall()

        return commandEvent(commands.drop(2).toTypedArray(), event)
    }

    private fun getChainDirFile(): File? {
        // make sure directory exists
        val dir = File(saveDirectory)
        if(dir.exists() && dir.isFile) {
            println("Error: file with name $saveDirectory already exists, not as a directory.")
            return null
        } else if(!dir.exists() && !dir.mkdir()) {
            println("Error: could not make $saveDirectory, make sure you have write permissions in the current directory.")
            return null
        }

        return dir
    }

    private fun loadChains() {
        println("Loading markov chains")
        val dir = getChainDirFile()
        if(dir == null) {
            println("Skipped loading chains")
            return
        }

        synchronized(chainMap) {
            for (path in dir.listFiles({ f -> f.extension == "json" }).orEmpty()) {
                val nickname = path.name.split(".")[0]
                println("Loading chain for $nickname")
                val chain = MarkovChain()
                chain.loadFromFile(path.canonicalPath)
                chainMap[nickname] = chain
            }
        }
    }
}

fun PircBotX.getFirstChannel(): String? {
    for(channel in userBot.channels)
        return channel.name
    return null
}
