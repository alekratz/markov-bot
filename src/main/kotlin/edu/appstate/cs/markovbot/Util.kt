package edu.appstate.cs.markovbot

/**
 * @author Alek Ratzloff <alekratz@gmail.com>
 */

fun toIrcLowerCase(name: String): String {
    val translations = hashMapOf(
            Pair('|', '\\'),
            Pair('[', '{'),
            Pair(']', '}'))
    var result = ""
    for(c in name) {
        // Deal with the weird translations for IRC
        if(translations.containsKey(c))
            result += translations[c]
        else
            result += c.toLowerCase()
    }
    return result
}

/*
fun isIrcNicknameEqual(first: String, second: String): Boolean {
    return toIrcLowerCase(first) == toIrcLowerCase(second)
}
*/