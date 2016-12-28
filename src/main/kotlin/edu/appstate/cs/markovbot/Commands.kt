package edu.appstate.cs.markovbot

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 */
fun ignore(args: CommandArgs): Boolean {
    val bot = args.event.bot
    val sendNick = args.event.user.nick
    args.listener.ignoreList.add(sendNick)
    bot.sendIRC().message(sendNick, "You are now being ignored. You can reverse this by " +
            "using !markov-listen.")
    return true
}

fun listen(args: CommandArgs): Boolean {
    val bot = args.event.bot
    val sendNick = args.event.user.nick
    args.listener.ignoreList.remove(sendNick)
    bot.sendIRC().message(sendNick, "You are now being recorded. You can reverse this by " +
            "using !markov-ignore.")
    return true
}

fun chance(args: CommandArgs): Boolean {
    if(args.args.size != 1)
        return false
    val sendNick = args.event.user.nick
    val lowerNick = toIrcLowerCase(sendNick)
    val bot = args.event.bot
    try {
        args.listener.userChances[lowerNick] = Math.min(args.args[0].toDouble(), args.listener.randomChance)
    } catch(ex: NumberFormatException) {
        return false
    }
    bot.sendIRC().message(sendNick, "Your random message chance has been set to " +
            "${args.listener.userChances[lowerNick]}.")
    return true
}

fun force(args: CommandArgs, all: Boolean = false): Boolean {
    val bot = args.event.bot
    val sendNick = args.event.user.nick
    val lowerNick = toIrcLowerCase(sendNick)
    val markovChain = if(all)
        args.listener.allChain
    else
        args.listener.chainMap[lowerNick]
    val maxSentences = args.listener.maxSentences
    var sentenceCount = if(args.args.size == 1) {
        try {
            Math.min(maxSentences, Math.abs(args.args[0].toInt()))
        } catch(ex: NumberFormatException) {
            1
        }
    } else {
        1
    }
    if(markovChain != null) {
        var result = ""
        while(sentenceCount > 0) {
            result += markovChain.randomSentence()
            sentenceCount--
        }
        bot.sendIRC().message(args.listener.channel, "$sendNick: $result")
    }
    return true
}

fun about(args: CommandArgs): Boolean {
    val bot = args.event.bot
    val aboutMessage = """markov-bot ${VersionInfo.STR}
source located at https://github.com/alekratz/markov-bot"""

    val sendNick = args.event.user.nick
    for(line in aboutMessage.split("\n"))
        bot.sendIRC().message(sendNick, line)
    return true
}

fun help(args: CommandArgs): Boolean {
    val bot = args.event.bot
    val randomChance = args.listener.randomChance
    val helpMessage = """usage: !markov [COMMAND]
where COMMANDs consist of:
    ignore  - stops the markov bot from listening to your messages
    listen  - starts the bot listening to your messages again
    chance  - sets the random chance that the bot will respond to you.
              Valid values are any real number between 0.0 and $randomChance.
    status  - gets your "social status" from Markov. Calculates your "social worth" and your percentile.
    force   - forces a markov chain to be generated based on what the markov bot has seen from you
    all     - forces a markov chain to be generated based on the collective of everything everyone has said
    about   - about the markov bot and version information
    """

    val sendNick = args.event.user.nick
    for(line in helpMessage.split("\n"))
        bot.sendIRC().message(sendNick, line)
    return true
}

fun status(args: CommandArgs): Boolean {
    val bot = args.event.bot
    val sendNick = args.event.user.nick
    val chains = args.listener.chainMap
    if(!chains.containsKey(sendNick))
        return true
    val chain = chains[sendNick]
    val chainSum = chain!!.sumNodeWeights()
    val totalSum = chains
            .filterNot { v -> v.key == ALL_CHAIN }
            .map { c -> c.value.sumNodeWeights() }
            .sum()
    val socialWorth = chainSum.toDouble() / totalSum.toDouble()
    println("chain sum: $chainSum")
    println("total sum: $totalSum")
    bot.sendIRC().message(args.listener.channel,
            "$sendNick: You are worth ${(socialWorth * 100.0).format(4)}% of the value of this channel")
    return true
}