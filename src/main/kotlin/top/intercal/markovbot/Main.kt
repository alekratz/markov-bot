package top.intercal.markovbot

import org.pircbotx.Configuration
import org.pircbotx.PircBotX
import sun.misc.Signal
import sun.misc.SignalHandler
import java.util.*
import javax.net.ssl.SSLSocketFactory

var threads = ArrayList<Thread>()

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 *     Catches a CTRL-C from the terminal. This only works for SIGINT as far as I am aware - if you send the process
 *     some other signal (e.g. SIGKILL, SIGQUIT) to kill it, this will likely not get called.
 */
class CatchCtrlC : SignalHandler {
    override fun handle(p0: Signal?) {
        println()
        println("ctrl-c caught; shutting down")
        for(t in threads)
            t.interrupt()
        for(t in threads)
            t.join()
    }
}

fun installSIGINTHandler() {
    val ctrlC = Signal("INT")
    val handler = CatchCtrlC()
    Signal.handle(ctrlC, handler)
}

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 *     Main method. Sets up the CTRL-C shutdown hook, loads properties, and starts bot threads.
 */
fun main(args: Array<String>) {
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
            fun prop(p: String): String? { return props.getProperty(p) }
            fun or(first: String, second: String): String? { return prop(first) ?: prop(second) }

            val saveEvery = or("$serverName.$channelName.save-every", "$serverName.save-every")?.toInt() ?: 3600
            val saveDirectory = prop("$serverName.$channelName.save-directory")
                    // basically, get the server directory and make a channel subdirectory inside of it
                    ?: "${prop("$serverName.save-directory")}/${channelName.filterNot { c -> c == '#' }}"
            val randomChance = or("$serverName.$channelName.random-chance", "$serverName.random-chance")?.toDouble() ?: 0.01
            val maxSentences = or("$serverName.$channelName.max-sentences", "$serverName.max-sentences")?.toInt() ?: 1
            val order = prop("$serverName.order")?.toInt() ?: 1

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
                            chainMap.chainMap,
                            order
                    ))
                    .setMessageDelay(50)
                    .setSocketTimeout(5000)
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

    // add jvm shutdown hook
    installSIGINTHandler()

    for(t in threads)
        t.start()
    // wait for all connections to close
    for(t in threads)
        t.join()
}