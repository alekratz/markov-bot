package edu.appstate.cs.markovbot

import org.pircbotx.Configuration
import org.pircbotx.PircBotX
import java.io.File
import java.util.*
import javax.net.ssl.SSLSocketFactory

var threads = ArrayList<Thread>()

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 *     Catches a CTRL-C from the terminal. This only works for SIGINT as far as I am aware - if you send the process
 *     some other signal (e.g. SIGKILL, SIGQUIT) to kill it, this will likely not get called.
 */
class CatchCtrlC : Runnable {
    override fun run() {
        println()
        println("ctrl-c caught; shutting down")
        for(t in threads)
            t.interrupt()
        for(t in threads)
            t.join()
    }
}

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 *     Loads the properties from the props file. For now, the props path is markov-bot.properties. This may change later.
 */
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

# Optional. Default 3600. The number of seconds in between markov chain saves. This can be set per-server or per-channel.
# freenode.save-every = 3600
# freenode.#myroom.save-every = 1800

# Optional. Default servername/chains. The location to save markov chains to. This may be set per-server, or per-channel.
# These directories may be shared among rooms.
# freenode.save-directory = freenode/chains
# freenode.#java.save-directory = freenode/chains

# Optional. Default 0.01. The chance that every time a message is sent, the markov bot will come up with something random to say to the channel.
# Must be less than 1.0 and greater than 0.0.
# freenode.random-chance = 0.01

# Optional. Default false. Determines whether to use SSL or not for a server.
# freenode.ssl = false

# Optional. Default none. Defines a password to send to the nickserv for authentication.
# If you don't want a password, DO NOT set this to blank; just comment it out.
# freenode.password =
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
    defaultProps.setProperty("save-every", "3600")

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
        val saveEvery = props.getProperty("$serverName.save-every") ?: props.getProperty("save-every")
        val saveDirectory = props.getProperty("$serverName.save-directory") ?: "chains/$serverName"
        val port = props.getProperty("$serverName.port") ?: "6667"
        val ssl = props.getProperty("$serverName.ssl") ?: "false"
        val randomChance = props.getProperty("$serverName.random-chance") ?: "0.01"
        val maxSentences = props.getProperty("$serverName.max-sentences") ?: "1"
        props.setProperty("$serverName.save-every", saveEvery)
        props.setProperty("$serverName.save-directory", saveDirectory)
        props.setProperty("$serverName.port", port)
        props.setProperty("$serverName.ssl", ssl)
        props.setProperty("$serverName.random-chance", randomChance)
        props.setProperty("$serverName.max-sentences", maxSentences)
    }

    return props
}

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 *     Main method. Sets up the CTRL-C shutdown hook, loads properties, and starts bot threads.
 */
fun main(args: Array<String>) {
    // add jvm shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread(CatchCtrlC()))

    val props = loadProperties()
    val servers = props.getProperty("servers").split(",")
    // If we have any shared chain directories, those chains will be shared among the different listeners.
    data class SaveInfo(val saveEvery: Int, val chainMap: HashMap<String, MarkovChain>)
    val chainSaves: HashMap<String, SaveInfo> = HashMap()

    for(serverName in servers) {
        val configBuilder = Configuration.Builder<PircBotX>()
                .setName(props.getProperty("$serverName.nickname"))
                .setServerHostname(props.getProperty("$serverName.hostname"))
                .setServerPort(props.getProperty("$serverName.port").toInt())

        if(props.getProperty("$serverName.ssl").toBoolean()) {
            val acceptInvalidCerts = props.getProperty("$serverName.ssl.accept-invalid-certs")?.toBoolean() ?: false

            if(acceptInvalidCerts)
                configBuilder.setSocketFactory(getNaiveSocketFactory())
            else
                configBuilder.setSocketFactory(SSLSocketFactory.getDefault())
        }

        // Set up listeners for each channel
        val channels = props.getProperty("$serverName.channels").split(",")
        for(channelName in channels) {
            val saveEvery = (props.getProperty("$serverName.$channelName.save-every")
                    ?: props.getProperty("$serverName.save-every")).toInt()
            val saveDirectory = props.getProperty("$serverName.$channelName.save-directory")
                    // basically, get the server directory and make a channel subdirectory inside of it
                    ?: "${props.getProperty("$serverName.save-directory")}/${channelName.filterNot { c -> c == '#' }}"
            val randomChance = (props.getProperty("$serverName.$channelName.random-chance")
                    ?: props.getProperty("$serverName.random-chance")).toDouble()
            val maxSentences = (props.getProperty("$serverName.$channelName.max-sentences")
                    ?: props.getProperty("$serverName.max-sentences")).toInt()

            // Get if this is a shared chain; if not, create its sharedness
            chainSaves.putIfAbsent(saveDirectory, SaveInfo(saveEvery, HashMap()))
            val chainMap = chainSaves[saveDirectory]!!
            configBuilder
                    .addAutoJoinChannel(channelName)
                    .addListener(MessageListener(
                            channelName,
                            saveDirectory,
                            randomChance,
                            maxSentences,
                            chainMap.chainMap
                    ))
                    .setMessageDelay(50)
            println("Adding channel $channelName")
        }

        // Create the final config and start the bot
        val nickServPassword = props.getProperty("$serverName.password")
        val config = configBuilder
                .setNickservPassword(nickServPassword)
                .buildConfiguration()
        val bot = PircBotX(config)
        bot.stopBotReconnect()
        val botThread = Thread(PircBotRunner(bot))

        // Add the bot thread
        threads.add(botThread)
    }

    // Add all of the savers for this bot
    for(dir in chainSaves.keys) {
        val chainMap = chainSaves[dir]!!.chainMap
        val saveEvery = chainSaves[dir]!!.saveEvery
        val saver = MessageSaver(chainMap, dir, saveEvery)
        threads.add(Thread(saver))
    }

    for(t in threads)
        t.start()
    // wait for all connections to close
    for(t in threads)
        t.join()
}