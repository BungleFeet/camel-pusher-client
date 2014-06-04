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
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * Represents a Pusher client endpoint.
 */
public class PusherClientEndpoint extends DefaultEndpoint {

    private final Channel channel;
    private final String appKey;
    private final String channelId;

    private String[] events = new String[]{};

    public PusherClientEndpoint(String uri, PusherClientComponent component, String appKey, Channel channel) {
       super(uri, component);
       this.appKey = appKey;
       this.channel = channel;
       this.channelId = component.getChannelId(appKey, channel.getName());
    }

    public Producer createProducer() throws Exception {
        return new PusherClientProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new PusherClientConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }

    public String getAppKey() {
        return appKey;
    }

    public Channel getChannel() {
        return channel;
    }

    public String[] getEvents() {
        return events;
    }

    public void setEvents(String event) {
        this.events = event.split(",");
    }

    public String getChannelId() { return channelId; }

    /**
     * Returns the component that created this endpoint.
     *
     * @return the component that created this endpoint, or <tt>null</tt> if
     * none set
     */
    @Override
    public PusherClientComponent getComponent() {
        return (PusherClientComponent) super.getComponent();
    }
}
