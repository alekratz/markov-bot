package edu.appstate.cs.markovbot

import org.pircbotx.*
import org.pircbotx.hooks.ListenerAdapter
import org.pircbotx.hooks.events.JoinEvent
import org.pircbotx.hooks.types.GenericMessageEvent
import java.io.File
import java.util.*
import javax.net.ssl.SSLSocketFactory

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 *     Documentation on pircbotx: http://thelq.github.io/pircbotx/latest/apidocs/
 */
class MarkovBot(saveEvery: Int, shouldSave: Boolean, saveDirectory: String) : ListenerAdapter<PircBotX>() {
    var present: Boolean = false
    val chainMap: HashMap<String, MarkovChain> = HashMap()
    val ignoreList: HashSet<String> = HashSet()
    val gen: Random = Random()

    val saveDirectory = saveDirectory
    val saveThread = Thread(MessageSaver(chainMap, saveDirectory, saveEvery))

    init {
        loadChains()

        if(shouldSave) {
            println("Starting save thread, saving every $saveEvery seconds")
            saveThread.start()
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
            val chain = chainMap[name]
            chain!!.train(msg) // choo choo
        }

        // also, 1/100 chance to send a random markov chain generated message
        if(gen.nextInt(100) == 0) {
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
        if(!event.message.startsWith("!"))
            return false

        val command = event.message.split(" ")[0]
        val bot = event.bot
        val sendNick = event.user.nick

        when(command) {
            "!markov-ignore" -> {
                ignoreList.add(sendNick)
                bot.sendIRC().message(sendNick, "You are now being ignored. You can reverse this by " +
                        "using !markov-listen.")
            }
            "!markov-listen" -> {
                ignoreList.remove(sendNick)
                bot.sendIRC().message(sendNick, "You are now being recorded. You can reverse this by " +
                        "using !markov-ignore.")
            }
            "!markov-force" -> {
                // force generate sentence here
                val channel = bot.getFirstChannel() ?: return false
                val markovChain = chainMap[sendNick]
                if(markovChain != null) {
                    val sentence = markovChain.generateSentence()
                    bot.sendIRC().message(channel, "$sendNick: $sentence")
                }
            }
            "!markov-help" -> {
                val channel = bot.getFirstChannel() ?: return false
                bot.sendIRC().message(channel,
                        "available commands: !markov-ignore !markov-listen")
            }
            else -> return false
        }

        return true
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
