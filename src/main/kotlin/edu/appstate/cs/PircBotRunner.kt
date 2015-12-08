package edu.appstate.cs

import org.pircbotx.PircBotX

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 */
class PircBotRunner(theBot: PircBotX) : Runnable {
    val bot = theBot

    public override fun run() {
        bot.startBot()
    }
}