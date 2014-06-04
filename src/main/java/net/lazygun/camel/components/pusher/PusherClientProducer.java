/*
 * Copyright 2014 Ewan Dawson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lazygun.camel.components.pusher;

import com.pusher.client.channel.Channel;
import com.pusher.client.channel.PresenceChannel;
import com.pusher.client.channel.PrivateChannel;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Pusher client producer.
 */
public class PusherClientProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(PusherClientProducer.class);
    private final Channel channel;
    private final Class<? extends Channel> channelType;

    public PusherClientProducer(PusherClientEndpoint endpoint) {
        super(endpoint);
        this.channel = endpoint.getChannel();
        this.channelType = channelType(channel);
        if (channelType.equals(Channel.class)) {
            throw new IllegalStateException("Cannot create a Producer for a public channel");
        }
    }

    private Class<? extends Channel> channelType(Channel channel) {
        String name = channel.getName();
        return name.startsWith("private-") ? PrivateChannel.class : (name.startsWith("presence-") ? PresenceChannel.class : Channel.class);
    }

    public void process(Exchange exchange) throws Exception {
        System.out.println(exchange.getIn().getBody());
        String eventName = exchange.getProperty(PusherClientComponent.EVENT_NAME).toString();
        if (!eventName.startsWith("client-")) {
            throw new IllegalArgumentException("Client-triggered events must have names starting with 'client-' (tried to trigger event named '" + eventName + "'");
        }
        String data = exchange.getIn(String.class);
        LOG.debug("Triggering event {} with data {} on channel {}", new Object[]{eventName, data, channel.getName()});
        if (channelType.equals(PrivateChannel.class)) {
            ((PrivateChannel)channel).trigger(eventName, data);
        } else if (channelType.equals(PresenceChannel.class)) {
            ((PresenceChannel)channel).trigger(eventName, data);
        } else {
            throw new IllegalStateException("Can only trigger events on private or presence channels");
        }
    }

}
