package edu.appstate.cs.markovbot

import org.pircbotx.Configuration
import org.pircbotx.PircBotX
import java.io.File
import java.util.*

var threads = ArrayList<Thread>()

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
    // add jvm shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread(CatchCtrlC()))

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