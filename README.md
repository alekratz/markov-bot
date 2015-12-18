# Markov-bot v0.1.0
Read more in the [Rundown](#rundown) section.

Written in Kotlin. Markov chains are written in Java.

Check the Wiki for [building instructions](https://github.com/alekratz/markov-bot/wiki/Building).

# Development
Here are a list of the candidates for v0.2.0 features, pulled from the wishlist. I believe these will be the most doable.

v0.2.0 roadmap:
* [x] Accepting invalid SSL certificates (upon explicit configuration)
* [ ] Custom number of sentences generated with !markov force and !markov all
* [ ] User-defined setting of random chance (see [1](https://github.com/alekratz/markov-bot/issues/1))

Wishlist:

* Nameserver registration
* Status log support
* More to come...

# Rundown
This bot records everything everyone says in an IRC channel. Each message sent has a random chance to make the IRC bot reply with a markov chain. Actual order of words/messages is not recorded, just the frequency of each word. Channel users may use the following commands to control how the bot interacts with them:

* `!markov force` forces the bot to create a markov chain customized for you. If a user's markov chain is not very fleshed out, this may not work; some IRC networks don't like the same message being sent more than once in a row.
* `!markov all` forces the bot to create a markov chain customized by all of the messages received in the channel, collectively. This will only mess up if this markov bot has absolutely no messages.
* `!markov ignore` tells the bot to ignore everything you say. This is in case a user doesn't want the bot to record any hint of what the user says.
* `!markov listen` undoes the markov-ignore command above. The markov bot will start listening and recording frequency of words.

For more details, check the [wiki](https://github.com/alekratz/markov-bot/wiki)!

# Features

* Multiple server support
* Multiple channel support
* Markov chain saving
* SSL support
* Nameserver login methods

# License

BSD Simplified License
