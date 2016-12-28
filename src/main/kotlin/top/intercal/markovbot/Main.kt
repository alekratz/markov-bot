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
            val saveEvery = (props.getProperty("$serverName.$channelName.save-every")
                    ?: props.getProperty("$serverName.save-every")).toInt()
            val saveDirectory = props.getProperty("$serverName.$channelName.save-directory")
                    // basically, get the server directory and make a channel subdirectory inside of it
                    ?: "${props.getProperty("$serverName.save-directory")}/${channelName.filterNot { c -> c == '#' }}"
            val randomChance = (props.getProperty("$serverName.$channelName.random-chance")
                    ?: props.getProperty("$serverName.random-chance")).toDouble()
            val maxSentences = (props.getProperty("$serverName.$channelName.max-sentences")
                    ?: props.getProperty("$serverName.max-sentences")).toInt()
            val order = (props.getProperty("$serverName.order") ?: "1").toInt()

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