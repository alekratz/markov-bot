package edu.appstate.cs.markovbot

import org.pircbotx.*
import org.pircbotx.hooks.ListenerAdapter
import org.pircbotx.hooks.events.JoinEvent
import org.pircbotx.hooks.events.MessageEvent
import java.io.File
import java.util.*

internal const val ALL_CHAIN = "/"

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 *     Documentation on pircbotx: http://thelq.github.io/pircbotx/latest/apidocs/
 */
class MessageListener(channel: String, saveDirectory: String, randomChance: Double, chainMap: HashMap<String, MarkovChain>) : ListenerAdapter<PircBotX>() {
    /**
     * Determines if the bot is in the room it is supposed to be in yet.
     */
    var present = false
    val chainMap = chainMap

    /**
     * @author Alek Ratzloff <alekratz@gmail.com>
     *     This is complex to instantiate and doesn't make much sense to instantiate if nobody is using the !markov all
     *     command. This is instantiated on demand (maybe make it lazy?)
     */
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

    val channel = channel
    val saveDirectory = saveDirectory
    val randomChance = randomChance

    /**
     * @author Alek Ratzloff <alekratz@gmail.com>
     *     Constructor for the markov bot. This loads up the necessary markov chains with the specified directory.
     */
    init {
        loadChains()
    }

    /**
     * @author Alek Ratzloff <alekratz@gmail.com>
     *     Handler for a generic message. It routes the message to the correct place if need be (i.e. is a command for
     *     a bot), and records the user's message into their markov chain if it's not a command.
     */
    public override fun onMessage(event: MessageEvent<PircBotX>) {
        val serverName = event.bot.serverInfo.serverName
        val messageChannel = event.channel.name
        if(messageChannel != channel) return

        println("$serverName ${event.user.nick} : ${event.message}")
        // if we're not in a room, don't bother listening
        if(!present)
            return

        // command was handled
        if(CommandHandler.doCommand(event, this)) return

        // ensure that the message ends in some sort of symbol. otherwise paste in a period at the end
        val msg = if(event.message.endsWith(".") || event.message.endsWith("!") || event.message.endsWith("?")) {
            event.message
        } else {
            event.message + "."
        }

        val sendNick = event.user.nick
        synchronized(chainMap) {
            chainMap.putIfAbsent(sendNick, MarkovChain())
            val chain = chainMap[sendNick]!!
            chain.train(msg) // choo choo
        }

        synchronized(allChain) {
            allChain.train(msg)
        }

        // random chance that a markov chain will be generated
        if(gen.nextDouble() < randomChance) {
            val markovChain = chainMap[sendNick]
            if(markovChain != null) {
                val sentence = markovChain.generateSentence()
                event.bot.sendIRC().message(channel, "$sendNick: $sentence")
            }
        }
    }

    /**
     * @author Alek Ratzloff <alekratz@gmail.com>
     *     Handler for when the bot joins the room. This mostly important to determine whether the bot is present and
     *     thus should listen to messages.
     */
    public override fun onJoin(event: JoinEvent<PircBotX>) {
        val nick = event.user.nick
        val joinedChannel = event.channel.name
        if(nick == event.bot.nick && joinedChannel == channel) {
            present = true
            println("Joined channel $joinedChannel")
        }
    }

    /**
     * @author Alek Ratzloff <alekratz@gmail.com>
     *     Gets the chain directory for this listener, provided that it exists. If it doesn't exists it will make it.
     * @return a file object of the chain save/load directory. If it cannot be created, returns null.
     */
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

    /**
     * @author Alek Ratzloff <alekratz@gmail.com>
     *     Loads the set of markov chains from the save/load directory.
     */
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
