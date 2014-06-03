package net.lazygun.camel.components.pusher;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * Represents a Pusher endpoint.
 */
public class PusherEndpoint extends DefaultEndpoint {

    private String appKey = "";
    private String channel = "";
    private String[] events = new String[]{};

    public PusherEndpoint() {
    }

    public PusherEndpoint(String uri, PusherComponent component) {
        super(uri, component);
    }

    public PusherEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Triggering Pusher client events is not supported");
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new PusherConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String[] getEvents() {
        return events;
    }

    public void setEvents(String event) {
        this.events = event.split(",");
    }
}
