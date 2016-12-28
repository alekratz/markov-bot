package top.intercal.markovbot

import java.io.File
import java.util.*

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

# Optional. Sets the order of the markov chain. Note that if you wish to chain the order of the markov chain, you must
# delete the original file.
# The order is recommended to be 1 or 2. A higher order makes sentences more coherent; too high of an order will just
# parrot back original sentences to users.
# freenode.order = 1

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