Markov-bot
=
Read more in the [Rundown](#rundown) section.

Markov chains are written in Java.

Written in Kotlin. Just run `mvn package` to get it to compile. Use `java -classpath target/markov-bot-1.0-SNAPSHOT.jar edu.appstate.cs.MarkovBot` to run it. Getting executable jars is on the TODO list.

A few TODOs:
* [x] Multiple server support
* [ ] Multithreaded markov chain saving
* [x] Add main class to the compiled jar so we can just use `java -jar target/markov-bot-1.0-SNAPSHOT.jar`

Rundown
=
This bot records everything everyone says in an IRC channel. Each message sent has a 1/100 random chance to make the IRC bot reply with a markov chain. Actual order of words/messages is not recorded, just the frequency of each word. Channel users may use the following commands to control how the bot interacts with them:

* `!markov-force` forces the bot to create a markov chain customized for you. If a user's markov chain is not very fleshed out, this may not work; some IRC networks don't like the same message being sent more than once in a row.
* `!markov-ignore` tells the bot to ignore everything you say. This is in case a user doesn't want the bot to record any hint of what the user says.
* `!markov-listen` undoes the markov-ignore command above. The markov bot will start listening and recording frequency of words.

License
=
BSD Simplified License
