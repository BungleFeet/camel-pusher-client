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
import com.pusher.client.channel.SubscriptionEventListener;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

import static net.lazygun.camel.components.pusher.PusherClientComponent.*;

/**
 * The Pusher consumer.
 */
public class PusherClientConsumer extends DefaultConsumer {

    private final SubscriptionEventListener listener;

    public PusherClientConsumer(final PusherClientEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        String[] events = endpoint.getEvents();
        Channel channel = endpoint.getChannel();
        this.listener = new SubscriptionEventListener() {
            @Override
            public void onEvent(String channelName, String eventName, String data) {
                handleEvent(channelName, eventName, data);
            }
        };

        String channelName = channel.getName();

        for (String event: events) {
            log.debug("Binding listener for '{}' events on channel {}", event, endpoint.getChannelId());
            channel.bind(event, listener);
        }

        handleEvent(channelName, PusherClientComponent.SUBSCRIBE_EVENT, "{}");

        if (channelName.startsWith("presence-")) {
            // Register presence listeners for this channel too
            endpoint.getComponent().registerForPresenceEvents(this);
        }
    }

    @Override
    public PusherClientEndpoint getEndpoint() {
        return (PusherClientEndpoint) super.getEndpoint();
    }

    public void handleEvent(String channelName, String eventName, Object data) {
        String appKey = getEndpoint().getAppKey();
        log.debug("Pusher app {} channel {} received event {} with data {}",
                new Object[]{appKey, channelName, eventName, data});

        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setHeader(APP_KEY, appKey);
        exchange.getIn().setHeader(CHANNEL, channelName);
        exchange.getIn().setHeader(EVENT_NAME, eventName);
        exchange.getIn().setBody(data);

        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            log.error("Error processing exchange", e);
        } finally {
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        Channel channel = getEndpoint().getChannel();
        String channelId = getEndpoint().getChannelId();
        for (String event: getEndpoint().getEvents()) {
            log.debug("Unbinding listener for {} events on channel {}", event, channelId);
            channel.unbind(event, listener);
        }
    }
}
