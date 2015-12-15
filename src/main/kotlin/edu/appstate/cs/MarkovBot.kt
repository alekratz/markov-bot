package edu.appstate.cs

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
class MarkovBot(val saveInterval: Int, val shouldsave: Boolean, val savedirectory: String) : ListenerAdapter<PircBotX>() {
    var present: Boolean = false
    val chainMap: HashMap<String, MarkovChain> = HashMap()
    val ignoreList: HashSet<String> = HashSet()
    val gen: Random = Random()
    val saveEvery = saveInterval
    val shouldSave = shouldsave
    val saveDirectory = savedirectory
    var messageCount = 0

    init {
        loadChains()
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

        chainMap.putIfAbsent(name, MarkovChain())
        val chain = chainMap[name]
        chain!!.train(msg) // choo choo

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

        // Save logic
        if(shouldSave) {
            messageCount++
            if (messageCount % saveEvery == 0) {
                // save markov stuff
                saveChains()
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

    private fun saveChains() {
        println("Saving markov chains")
        if(getChainDirFile() == null) {
            println("Skipped saving chains")
            return
        }

        for(nickname in chainMap.keys) {
            println("Saving chain for $nickname")
            val chain = chainMap[nickname]
            chain?.saveToFile("$saveDirectory/$nickname.json")
        }
    }

    private fun loadChains() {
        println("Loading markov chains")
        val dir = getChainDirFile()
        if(dir == null) {
            println("Skipped loading chains")
            return
        }

        for(path in dir.listFiles({ f -> f.extension == "json" }).orEmpty()) {
            val nickname = path.name.split(".")[0]
            println("Loading chain for $nickname")
            val chain = MarkovChain()
            chain.loadFromFile(path.canonicalPath)
            chainMap[nickname] = chain
        }
    }
}

fun PircBotX.getFirstChannel(): String? {
    for(channel in userBot.channels)
        return channel.name
    return null
}

fun loadProperties(): Properties {
    val propsTemplate =
"""# Required. A comma-separated list of servers that the bot uses. These each have their own subsections.
servers = freenode

# Required for each server. A comma-separated list of channels that the bot will be a part of.
freenode.channels =

# Required. The nickname of the markov bot for the given server.
freenode.nickname = markovbot

# Required. The hostname of the server that you will be operating on.
freenode.hostname = chat.freenode.net

# Optional. Default true. Determines if the bot should save markov chains. This may be set per-server, or per-channel.
# freenode.should-save = true
# freenode.java.should-save = false

# Optional. Default 20. The amount of messages received in between markov chain saves. This may be set per-server, or per-channel.
# freenode.save-every = 20
# freenode.myroom.save-every = 100

# Optional. Default servername/chains. The location to save markov chains to. This may be set per-server, or per-channel.
# freenode.save-directory = freenode/chains

# Optional. Default false. Determines whether to use SSL or not for a server.
# freenode.ssl = false
"""
    val propsPath = "markov-bot.properties"
    val propsFile = File(propsPath)
    if(!propsFile.exists()) {
        println("Properties file not found, creating template in $propsPath")
        if(!propsFile.createNewFile()) {
            println("Could not create properties file. Exiting")
            System.exit(1)
        }
        val writer = propsFile.printWriter()
        writer.print(propsTemplate)
        writer.close()
        println("Exiting")
        System.exit(0)
    }

    val defaultProps = Properties()
    defaultProps.setProperty("should-save", "true")
    defaultProps.setProperty("save-every", "100")

    val props = Properties(defaultProps)
    props.load(propsFile.bufferedReader())
    fun checkProp(propName: String) {
        val prop = props.getProperty(propName)
        if(prop == null || prop.trim() == "") {
            println("$propName field in properties must be filled out")
            System.exit(1)
        }
    }

    checkProp("servers")
    val servers = props.getProperty("servers").split(",")
    for(serverName in servers) {
        // verify that all required properties exist
        checkProp("$serverName.nickname")
        checkProp("$serverName.hostname")
        checkProp("$serverName.channels")

        // I know this is kinda inefficient, but we can fix that later. Maybe make a custom overridden Properties method?
        val shouldSave = props.getProperty("$serverName.should-save") ?: props.getProperty("should-save")
        val saveEvery = props.getProperty("$serverName.save-every") ?: props.getProperty("save-every")
        val saveDirectory = props.getProperty("$serverName.save-directory") ?: "chains/$serverName"
        val port = props.getProperty("$serverName.port") ?: "6667"
        val ssl = props.getProperty("$serverName.ssl") ?: "false"
        props.setProperty("$serverName.should-save", shouldSave)
        props.setProperty("$serverName.save-every", saveEvery)
        props.setProperty("$serverName.save-directory", saveDirectory)
        props.setProperty("$serverName.port", port)
        props.setProperty("$serverName.ssl", ssl)
    }

    return props
}

fun main(args: Array<String>) {
    var threads = ArrayList<Thread>()
    val props = loadProperties()
    val servers = props.getProperty("servers").split(",")
    for(serverName in servers) {
        val configBuilder = Configuration.Builder<PircBotX>()
                .setName(props.getProperty("$serverName.nickname"))
                .setServerHostname(props.getProperty("$serverName.hostname"))
                .setServerPort(props.getProperty("$serverName.port").toInt())

        //if(props.getProperty("$serverName.ssl").toBoolean())
        //    configBuilder.setSocketFactory(SSLSocketFactory.getDefault())

        // channels
        val channels = props.getProperty("$serverName.channels").split(",")
        for(channelName in channels) {
            val shouldSave = (props.getProperty("$serverName.$channelName.should-save")
                    ?: props.getProperty("$serverName.should-save")).toBoolean()
            val saveEvery = (props.getProperty("$serverName.$channelName.save-every")
                    ?: props.getProperty("$serverName.save-every")).toInt()
            val saveDirectory = props.getProperty("$serverName.$channelName.save-directory")
                    ?: props.getProperty("$serverName.save-directory")

            configBuilder
                .addAutoJoinChannel(channelName)
                .addListener(MarkovBot(
                        saveEvery,
                        shouldSave,
                        saveDirectory
                ))
            println("Adding channel $channelName")
        }

        val config = configBuilder.buildConfiguration()
        val bot = PircBotX(config)
        bot.stopBotReconnect()
        val botThread = Thread(PircBotRunner(bot))
        threads.add(botThread)
    }

    for(t in threads)
        t.start()
    // wait for all connections to close
    for(t in threads)
        t.join()
}