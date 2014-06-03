camel-pusher-client
============

Adds Pusher (http://pusher.com) client endpoints to Apache Camel.

Usage
-----

Define a Consumer or Producer using the URI `pusher-client://<app-key>/<channel>?events=<event1>,<event2>...`

Where:
* `<app-key>` is the application key which you can get from the app's API Access section in the Pusher dashboard
* `<channel>` is the name of the channel to which you wish to subscribe
* `<event1>,<event2>` is a comma-separated list of events for which you wish to listen

### Consumer

The Consumer will connect to the Pusher app and listen for events on the specified channel. When the consumer successfully
subscribes to the channel, it will generate an Exchange with an empty body, and the headers:
* `pusher.appKey: <app-key>`
* `pusher.channel: <channel>`
* `pusher.eventName: "pusher:subscribe"`

The Consumer will then listen for the specified events, and create a new Exchange for each one it receives. The Exchange body will contain the unparsed event data String.

Currently, only public channels are supported. Private and presence channels require authorization, which hasn't been implemented yet.

### Producer

The Producer endpoint hasn't been implemented yet. Any attempt to create a Producer using the `pusher-client://` scheme will result in an `UnsupportedOperationException`.
