package edu.appstate.cs.markovbot

import java.io.File
import java.util.*

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 *     @param markovChain the markov chain that we will be consistently saving
 *     @param sleepTime the number of seconds to sleep in between saves.
 */
class MessageSaver(chainMap: HashMap<String, MarkovChain>, saveDirectory: String, sleepTime: Int) : Runnable {
    // Like Caesar and Brutus
    // Like Jesus and Judas
    val chainMap = chainMap
    val saveDirectory = saveDirectory
    val sleepTime = sleepTime * 1000
    var needsUpdate = false

    override fun run() {
        var interrupted = false
        while(!interrupted) {
            try {
                Thread.sleep(sleepTime.toLong())
                saveChains()
            } catch(ex: InterruptedException) {
                println("Chain saver interrupted, saving one last time")
                saveChains()
            }
        }
    }

    private fun saveChains() {
        if(needsUpdate) {
            needsUpdate = false
        } else {
            println("No new messages to save - skipping")
            return
        }

        println("Saving markov chains")
        if(getChainDirFile() == null) {
            println("Skipped saving chains")
            return
        }

        synchronized(chainMap) {
            for (nickname in chainMap.keys) {
                if(nickname == ALL_CHAIN)
                    continue
                println("Saving chain for $nickname")
                val chain = chainMap[nickname]
                chain?.saveToFile("$saveDirectory/$nickname.json")
            }
        }
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
}