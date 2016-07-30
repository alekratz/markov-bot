package edu.appstate.cs.markovbot

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import org.pircbotx.*
import org.pircbotx.hooks.ListenerAdapter
import org.pircbotx.hooks.events.JoinEvent
import org.pircbotx.hooks.events.MessageEvent
import java.io.File
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

internal const val ALL_CHAIN = "/"

private const val EXPECTED_INSERTIONS = 5000
private const val FPP = 0.001

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 *     Documentation on pircbotx: http://thelq.github.io/pircbotx/latest/apidocs/
 */
class MessageListener(channel: String, saveDirectory: String, randomChance: Double, maxSentences: Int,
                      chainMap: HashMap<String, MarkovChain>) : ListenerAdapter<PircBotX>() {
    /**
     * Determines if the bot is in the room it is supposed to be in yet.
     */
    var present = false
    val chainMap = chainMap
    var sentMessageFilter = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), EXPECTED_INSERTIONS, FPP)
    var messageCount = 0

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
    val userChances: HashMap<String, Double> = HashMap()
    val gen: Random = Random()

    val channel = channel
    val saveDirectory = saveDirectory
    val randomChance = randomChance
    val maxSentences = maxSentences

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
    override fun onMessage(event: MessageEvent<PircBotX>) {
        // if we're not in a room, don't bother listening
        if(!present)
            return

        val messageChannel = event.channel.name
        if(messageChannel != channel) return

        val serverName = event.bot
                .serverInfo
                .serverName
        val now = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_TIME)
        println("$now: $serverName ${event.user.nick} : ${event.message}")

        // command was handled
        if(CommandHandler.doCommand(event, this)) return

        // ignore
        if(ignoreList.contains(event.user.nick))
            return
        
        // increment message count
        messageCount++
        if(messageCount % 5000 == 0) {
            synchronized(sentMessageFilter) {
                sentMessageFilter = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()),
                        EXPECTED_INSERTIONS, FPP)
            }
        }

        // ensure that the message ends in some sort of symbol. otherwise paste in a period at the end
        val msg = if(event.message.endsWith(".") || event.message.endsWith("!") || event.message.endsWith("?")) {
            event.message
        } else {
            event.message + "."
        }

        val sendNick = event.user.nick
        val lowerNick = toIrcLowerCase(sendNick)
        synchronized(chainMap) {
            chainMap.putIfAbsent(lowerNick, MarkovChain())
            val chain = chainMap[lowerNick]!!
            chain.train(msg) // choo choo
        }

        // If the allchain hasn't been initialized, don't worry about it. We only want to start working on it when it's
        // finally been initliazed via the !markov all command.
        if(chainMap[ALL_CHAIN] != null) {
            synchronized(allChain) {
                allChain.train(msg)
            }
        }

        // set up the random chance for the user if it hasn't been already
        userChances.putIfAbsent(lowerNick, randomChance)
        // random chance that a markov chain will be generated
        if(gen.nextDouble() < userChances[lowerNick]!!) {
            val markovChain = chainMap[lowerNick]
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
    override fun onJoin(event: JoinEvent<PircBotX>) {
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
     *     Loads the set of markov chains from the save/load M directory.
     */
    private fun loadChains() {
        println("Loading markov chains")
        val dir = getChainDirFile()
        if(dir == null) {
            println("Skipped loading chains")
            return
        }

        var usernameCount = 0
        synchronized(chainMap) {
            for (path in dir.listFiles({ f -> f.extension == "json" }).orEmpty()) {
                val nickname = path.name.split(".")[0]
                val nickLower = toIrcLowerCase(nickname)
                println("Loading chain for $nickLower (aka $nickname)")
                val chain = MarkovChain()
                chain.loadFromFile(path.canonicalPath)
                usernameCount++
                if(chainMap.containsKey(nickLower))
                    chainMap[nickLower]!!.merge(chain)
                else
                    chainMap[nickLower] = chain
            }
        }
        println("I have loaded ${chainMap.keys.size} chains ($usernameCount unique users)")
    }
}
