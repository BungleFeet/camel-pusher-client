package net.lazygun.camel.components.pusher;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Pusher producer.
 */
public class PusherProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(PusherProducer.class);
    private PusherEndpoint endpoint;

    public PusherProducer(PusherEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        System.out.println(exchange.getIn().getBody());    
    }

}
