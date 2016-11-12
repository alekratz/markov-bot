package edu.appstate.cs.markovbot

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*
import com.fasterxml.jackson.module.kotlin.*
import java.io.*

fun List<String>.equals(other: List<String>): Boolean {
    if(this.size != other.size)
        return false
    return this.zip(other).all { p -> p.first == p.second }
}

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 */
class MarkovChain(val order: Int = 1) : Serializable {

    // class MarkovLink(var node: MarkovNode, var weight: Int)
    data class MarkovNode(val words: List<String>, val links: MutableMap<String, Int>, var weight: Int = 1) : Serializable

    @JsonProperty("chain")
    private var chain: MutableMap<List<String>, MarkovNode> = mutableMapOf()
    @Transient
    var random: Random

    init {
        random = Random()
    }

    fun train(sentence: String) {
        val words = sentence.split(" ")
        if (words.isEmpty()) {
            return
        }

        var offset = 0
        do {
            val snippet = if(offset + order > words.size) {
                words
            }
            else {
                ArrayList(words.subList(offset, offset + order))
            }
            assert(snippet.isNotEmpty())
            assert(snippet.size <= order)
            // Get the next word. If there is none, just an empty string.
            val next = if(words.size <= offset + order)
                ""
            else
                words[order + offset]
            // If there's already a chain in there
            if(chain.keys.contains(snippet)) {
                val node = chain[snippet]!!
                node.weight += 1
                val links = node.links
                if(links.containsKey(next)) {
                    links[next] = links[next]!! + 1
                }
                else {
                    links[next] = 1
                }
            }
            else {
                val node = MarkovNode(snippet, mutableMapOf(Pair(next, 1)))
                chain[snippet] = node
            }
            offset += 1
        } while (offset + order <= words.size)
    }

    fun merge(other: MarkovChain) {
        val root = other.chain
        root.keys.forEach { words ->
            if(chain.containsKey(words)) {
                val node = chain[words]!!
                node.weight += root[words]!!.weight
                val links = root[words]!!.links
                links.forEach { link ->
                    val linkWeight = if(node.links.containsKey(link.key)) {
                        node.links[link.key]!!
                    }
                    else {
                        0
                    }
                    node.links[link.key] = linkWeight + link.value
                }
            }
            else {
                chain[words] = root[words]!!
            }
        }
    }

    private fun <T> randomWeightChoice(weightTable: Map<T, Int>): T {
        val maxWeight = weightTable.values.reduce { a, b -> a + b }
        val randomWeight = random.nextInt(maxWeight + 1)
        var totalWeight = 0
        for(pair in weightTable) {
            totalWeight += pair.value
            if(randomWeight <= totalWeight)
                return pair.key
        }
        assert(false, { "Chose random number that was not in the right range for the total weight" })
        throw Exception("Chose random number that was not in the right range for the total weight")
    }

    fun randomSentence(maxLength: Int = 30): String {
        val rootWeights = chain.mapValues { v -> v.value.weight }
        val selected = randomWeightChoice(rootWeights).toMutableList()
        val lastList = ArrayList(selected)
        while(selected.size < maxLength) {
            val nextWord = if(chain.containsKey(lastList)) {
                randomWeightChoice(chain[lastList]!!.links)
            }
            else {
                break
            }
            if(nextWord == "")
                break
            lastList.removeAt(0)
            lastList.add(nextWord)
            selected.add(nextWord)
        }
        var sentence = selected.reduce { total, next -> "$total $next"}
        if(!(sentence.endsWith('.') || sentence.endsWith('!') || sentence.endsWith('?')))
            sentence += "."
        return sentence
    }

    fun saveJson(path: String) {
        val mapper = jacksonObjectMapper()
        mapper.writeValue(File(path), this)
    }

    fun saveMarkovFile(path: String) {
        val fileOut = FileOutputStream(path)
        val objOut = ObjectOutputStream(fileOut)
        objOut.writeObject(this)
        objOut.close()
        fileOut.close()
    }

    companion object {
        private const val serialVersionUID: Long = 8600625900797980690L
    }
}


fun loadMarkovFile(path: String): MarkovChain {
    val fileIn = FileInputStream(path)
    val objIn = ObjectInputStream(fileIn)
    val chain = objIn.readObject() as MarkovChain
    chain.random = Random()
    return chain
}

