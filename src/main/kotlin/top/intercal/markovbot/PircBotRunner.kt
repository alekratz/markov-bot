package top.intercal.markovbot

import org.pircbotx.PircBotX

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 */
class PircBotRunner(theBot: PircBotX) : Runnable {
    val bot = theBot

    override fun run() {
        bot.startBot()
    }
}