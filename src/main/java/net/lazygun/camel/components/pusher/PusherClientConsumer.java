package net.lazygun.camel.components.pusher;

import com.pusher.client.Pusher;
import com.pusher.client.channel.*;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

import java.util.Set;

import static net.lazygun.camel.components.pusher.PusherClientComponent.*;

/**
 * The Pusher consumer.
 */
public class PusherClientConsumer extends DefaultConsumer {

    private final String appKey;

    public PusherClientConsumer(final PusherClientEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.appKey = endpoint.getAppKey();
        final String[] events = endpoint.getEvents();

        final Pusher pusher = new Pusher(endpoint.getAppKey());
        pusher.connect(new ConnectionEventListener() {
            @Override
            public void onConnectionStateChange(ConnectionStateChange change) {
                log.info("Pusher app {} connection state change from {} to {}",
                        new Object[]{endpoint.getAppKey(), change.getPreviousState(), change.getCurrentState()});
            }

            @Override
            public void onError(String message, String code, Exception e) {
                log.error("Pusher could not connect to app {}: {} ({})",
                        new Object[]{endpoint.getAppKey(), message, code}, e);
            }
        }, ConnectionState.ALL);

        log.debug("Subscribing to channel {}/{}, listening for events {}",
                new Object[]{endpoint.getAppKey(), endpoint.getChannel(), endpoint.getEvents()});

        if (endpoint.getChannel().startsWith("private-")) {
            pusher.subscribePrivate(endpoint.getChannel(), new PrivateChannelEventListener() {
                @Override
                public void onAuthenticationFailure(String message, Exception e) {
                    getExceptionHandler().handleException(message, e);
                }

                @Override
                public void onSubscriptionSucceeded(String channelName) {
                    log.info("Pusher app {} successfully subscribed to private channel {}", endpoint.getAppKey(), channelName);
                    handleEvent(channelName, SUBSCRIBE_EVENT, null);
                }

                @Override
                public void onEvent(String channelName, String eventName, String data) {
                    handleEvent(channelName, eventName, data);
                }
            }, events);

        } else if (endpoint.getChannel().startsWith("presence-")) {
            pusher.subscribePresence(endpoint.getChannel(), new PresenceChannelEventListener() {
                @Override
                public void onUsersInformationReceived(String channelName, Set<User> users) {
                    handleEvent(channelName, USER_INFORMATION_RECEIVED_EVENT, users);
                }

                @Override
                public void userSubscribed(String channelName, User user) {
                    handleEvent(channelName, MEMBER_ADDED_EVENT, user);
                }

                @Override
                public void userUnsubscribed(String channelName, User user) {
                    handleEvent(channelName, MEMBER_REMOVED_EVENT, user);
                }

                @Override
                public void onAuthenticationFailure(String message, Exception e) {
                    getExceptionHandler().handleException(message, e);
                }

                @Override
                public void onSubscriptionSucceeded(String channelName) {
                    log.info("Pusher app {} successfully subscribed to presence channel {}", endpoint.getAppKey(), channelName);
                    handleEvent(channelName, SUBSCRIBE_EVENT, null);
                }

                @Override
                public void onEvent(String channelName, String eventName, String data) {
                    handleEvent(channelName, eventName, data);
                }
            }, events);

        } else {
            pusher.subscribe(endpoint.getChannel(), new ChannelEventListener() {
                @Override
                public void onSubscriptionSucceeded(String channelName) {
                    log.info("Pusher app {} successfully subscribed to public channel {}", endpoint.getAppKey(), channelName);
                    handleEvent(channelName, SUBSCRIBE_EVENT, null);
                }

                @Override
                public void onEvent(String channelName, String eventName, String data) {
                    handleEvent(channelName, eventName, data);
                }
            }, events);
        }
    }

    private void handleEvent(String channelName, String eventName, Object data) {
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
}
