package edu.appstate.cs

import org.pircbotx.*
import org.pircbotx.hooks.ListenerAdapter
import org.pircbotx.hooks.events.JoinEvent
import org.pircbotx.hooks.types.GenericMessageEvent
import java.io.File
import java.util.*

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 *     Documentation on pircbotx: http://thelq.github.io/pircbotx/latest/apidocs/
 */
class MarkovBot(val saveInterval: Int, val shouldsave: Boolean) : ListenerAdapter<PircBotX>() {
    var present: Boolean = false
    val chainMap: HashMap<String, MarkovChain> = HashMap()
    val ignoreList: HashSet<String> = HashSet()
    val gen: Random = Random()
    val saveEvery = saveInterval
    val shouldSave = shouldsave
    val saveDirectory = "chains"
    var messageCount = 0

    public override fun onGenericMessage(event: GenericMessageEvent<PircBotX>) {
        println("${event.user.nick} : ${event.message}")
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

    private fun saveChains() {
        println("Saving markov chains")
        // make sure directory exists
        val dir = File(saveDirectory)
        if(dir.exists() && dir.isFile) {
            println("Error: file with name $saveDirectory already exists, not as a directory.")
            println("Skipped saving chains")
            return
        } else if(!dir.exists() && !dir.mkdir()) {
            println("Error: could not make $saveDirectory, make sure you have write permissions in the current directory.")
            println("Skipped saving chains")
            return
        }
        for(nickname in chainMap.keys) {
            println("Saving chain for $nickname")
            val chain = chainMap[nickname]
            chain?.saveToFile("$saveDirectory/$nickname.json")
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
"""# Required. The nickname of your markov bot.
nickname = markovbot
# Required. The server to connect to.
server = chat.freenode.net
# Required. The channel to join. Must include the leading #
channel =
# Optional. Default true. Determines if the bot should save markov chains.
should-save = true
# Optional. Default 100. The amount of messages received in between markov chain saves
save-every = 100
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
    // verify that all required properties exist
    checkProp("nickname")
    checkProp("server")
    checkProp("channel")

    return props
}

fun main(args: Array<String>) {
    val props = loadProperties()
    val config = Configuration.Builder<PircBotX>()
            .setName(props.getProperty("nickname"))
            .setServerHostname(props.getProperty("server"))
            .addAutoJoinChannel(props.getProperty("channel"))
            .addListener(MarkovBot(
                    props.getProperty("save-every").toInt(),
                    props.getProperty("should-save").toBoolean()))
            .buildConfiguration()

    val bot = PircBotX(config)
    bot.stopBotReconnect()
    bot.startBot()
}