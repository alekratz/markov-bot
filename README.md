# Markov-bot

Read more in the [Rundown](#rundown) section.

Markov chains are written in Java.

Written in Kotlin. Just run `mvn package` to get it to compile. Use `java -classpath target/markov-bot-1.0-SNAPSHOT.jar edu.appstate.cs.MarkovBot` to run it. Getting executable jars is on the TODO list.

# Development

**[Current development branch is here!](https://github.com/alekratz/markov-bot/tree/v0.1-dev)**

Version 0.1 roadmap:
* [x] Multiple server support
* [x] Multithreaded markov chain saving
* [x] Add main class to the compiled jar so we can just use `java -jar target/markov-bot-1.0-SNAPSHOT.jar`
* [x] SSL support
* [x] `!markov all` command that forces a message generated from all messages recorded on this channel
* [x] Restructure commands to be something along the line of `!markov [command] [args]` to make things more extensive.
* [ ] Nameserver login methods
* [ ] Basic wiki entries
* [x] Stubs for method documentation in the code

Wishlist:
* Nameserver registration
* Accepting invalid SSL certificates
* Custom number of sentences generated with !markov force and !markov all
* More to come...

# Rundown

This bot records everything everyone says in an IRC channel. Each message sent has a random chance to make the IRC bot reply with a markov chain. Actual order of words/messages is not recorded, just the frequency of each word. Channel users may use the following commands to control how the bot interacts with them:

* `!markov force` forces the bot to create a markov chain customized for you. If a user's markov chain is not very fleshed out, this may not work; some IRC networks don't like the same message being sent more than once in a row.
* `!markov all` forces the bot to create a markov chain customized by all of the messages received in the channel, collectively. This will only mess up if this markov bot has absolutely no messages.
* `!markov ignore` tells the bot to ignore everything you say. This is in case a user doesn't want the bot to record any hint of what the user says.
* `!markov listen` undoes the markov-ignore command above. The markov bot will start listening and recording frequency of words.

# License

BSD Simplified License
