#camel-pusher-client

Adds Pusher (http://pusher.com) client endpoints to Apache Camel.

##Usage

Define a Consumer or Producer using the URI `pusher-client://{app-key}/{channel}?events={event1},{event2}...`

Where:
* `{app-key}` is the application key which you can get from the apps API Access section in the Pusher dashboard
* `{channel}` is the name of the channel to which you wish to subscribe
* `{event1},{event2}` is a comma-separated list of events for which you wish to listen

### Consumer

The Consumer will connect to the Pusher app and listen for events on the specified channel. When the consumer
successfully subscribes to the channel, it will generate an Exchange with body `"{}"`, and the headers:
* `pusher.appKey: {app-key}`
* `pusher.channel: {channel}`
* `pusher.eventName: "pusher:subscribe"`

The Consumer will then listen for the specified events, and create a new Exchange for each one it receives. The Exchange
body will contain the raw event data String. The Exchange headers will contain the app key, channel and event name
information.

#### Private and presence channels

To create a Consumer or Producer endpoint for a private or presence channel (i.e. one where the channel name being with 'private-'
or 'presence-'), there must be an `com.pusher.client.Authorizer` instance (e.g. `com.pusher.client.util.HttpAuthorizer`)
registered in the Registry of the current CamelContext. If more than one Authorizer is registered, the one whose name
includes the app-key for that endpoint will be used.

A Consumer subscribed to a presence channel will receive a `pusher:subscription_succeeded` message on startup, the body
of which will contain a Set of `com.pusher.client.channel.User` objects. In addition, when users join or leave the
channel, the Consumer will receive `pusher:member_added` and `pusher:member_removed` messages. These event names
should not be added to the endpoint URI.

### Producer

A Producer endpoint can only be created for private and presence channels, since client events can't be triggered
on public channels. To trigger a client event, set the `pusher.eventName` property of the Exchange header to the
name of the event you wish to trigger (remembering that client-triggered events must have names beginning 'client-').
The body of the Exchange inbound message will be converted to a String and used as the event data.

## License

This software is subject to the [Apache License v2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
