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

import com.pusher.client.Authorizer;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.PresenceChannelEventListener;
import com.pusher.client.channel.User;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import org.apache.camel.impl.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.SynchronousQueue;

/**
 * Represents the component that manages {@link PusherClientEndpoint}.
 */
public class PusherClientComponent extends DefaultComponent {

    public static final String SCHEME = "pusher-client";
    public static final String APP_KEY = "pusher.appKey";
    public static final String CHANNEL = "pusher.channel";
    public static final String EVENT_NAME = "pusher.eventName";
    // Presence channel events of which we will handle propagation
    public static final String SUBSCRIBE_EVENT = "pusher:subscribe";
    public static final String SUBSCRIPTION_SUCCEEDED_EVENT = "pusher:subscription_succeeded";
    public static final String MEMBER_ADDED_EVENT = "pusher:member_added";
    public static final String MEMBER_REMOVED_EVENT = "pusher:member_removed";
    private static final Logger LOG = LoggerFactory.getLogger(PusherClientComponent.class);
    // Maintain cache of pusher domain objects that we don't want to create twice
    private static final ConcurrentHashMap<String, Pusher> apps = new ConcurrentHashMap<String, Pusher>();
    private static final ConcurrentSkipListSet<String> subscriptions = new ConcurrentSkipListSet<String>();
    private static final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<String, Channel>();

    // Collections used to propagate presence channel events to PusherClientConsumers that are subscribed to a presence channel
    private static final ConcurrentHashMap<String, Set<User>> presenceChannelUsers = new ConcurrentHashMap<String, Set<User>>();
    private static final ConcurrentHashMap<String, Set<PusherClientConsumer>> presenceChannelConsumers = new ConcurrentHashMap<String, Set<PusherClientConsumer>>();

    // SynchronousQueue used for thread synchronisation. Needed when dealing with Pusher callback functions
    private final SynchronousQueue<Object> queue = new SynchronousQueue<Object>(true);

    /**
     * Constructs a new PusherClientEndpoint based on the given URI and parameters. The URI should be of the form
     * <pre>
     *     pusher-client://{app-key}/{channel_name}?events=event1,event2...
     * </pre>
     * To create an endpoint for a private or presence channel (i.e. one where the channel name being with 'private-'
     * or 'presence-'), there must be an {@link com.pusher.client.Authorizer} instance (e.g. {@link com.pusher.client.util.HttpAuthorizer})
     * registered in the {@link org.apache.camel.spi.Registry} of the current {@link org.apache.camel.CamelContext}. If
     * more than one Authorizer is registered, the one whose name includes the app-key present in the URI will be chosen.
     * <P/>
     * A presence channel endpoint will automatically receive events when a member is added or removed; no need to
     * register for these events explicitly.
     *
     * @param uri the URI of the endpoint
     * @param remaining the un-matched portion of the URI
     * @param parameters a map of any query parameters on the endpoint URI
     * @return a PusherClientEndpoint constructed from the given URI
     * @throws Exception
     */
    protected PusherClientEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Extract app key and channel name from endpoint URI
        String[] path = remaining.split("/");
        if (path.length != 2) {
            throw new IllegalArgumentException("Pusher uri path must contain app key and channel name: " + SCHEME + "://{app-key}/{channel_name}");
        }
        final String appKey = path[0];
        final String channelName = path[1];
        final String channelId = getChannelId(appKey, channelName);

        // Set Pusher options - first, try to find an Authorizer instance in the registry
        PusherOptions options = new PusherOptions();
        Map<String, Authorizer> authorizers = getCamelContext().getRegistry().findByTypeWithName(Authorizer.class);
        if (authorizers.size() == 1) {
            LOG.debug("Found 1 authorizer: {}", authorizers.keySet().iterator().next());
            options.setAuthorizer(authorizers.values().iterator().next());
        } else if (authorizers.size() > 1) {
            LOG.debug("Found {} authorizers", authorizers.size());
            for (String name : authorizers.keySet()) {
                if (name.contains(appKey)) {
                    LOG.debug("Setting authorizer: " + name);
                    options.setAuthorizer(authorizers.get(name));
                    break;
                }
            }
            options.setAuthorizer(authorizers.values().iterator().next());
        }
        // set options from the endpoint parameters
        setProperties(options, parameters);

        // Create a new pusher from these options, and add to our collection of apps if not
        // already there
        Pusher pusher = apps.putIfAbsent(appKey, new Pusher(appKey, options));

        if (pusher == null) {
            LOG.debug("Creating new Pusher instance for appKey: {}", appKey);
            pusher = apps.get(appKey);

            // Connect to the app
            pusher.connect(new ConnectionEventListener() {
                @Override
                public void onConnectionStateChange(ConnectionStateChange change) {
                    LOG.info("Pusher app {} connection state change from {} to {}",
                            new Object[]{appKey, change.getPreviousState(), change.getCurrentState()});
                    if (change.getCurrentState() == ConnectionState.CONNECTED) {
                        finished(change.getCurrentState());
                    }
                }

                @Override
                public void onError(String message, String code, Exception e) {
                    LOG.error("Pusher could not connect to app {}: {} ({})", new Object[]{appKey, message, code});
                    finished(e);
                }
            }, ConnectionState.ALL);

            join(); // Wait for connection
            LOG.info("Pusher connected to app {}", appKey);

            // Listen for disconnections, and re-connect if they occur.
            final Pusher finalPusher = pusher;
            pusher.getConnection().bind(ConnectionState.DISCONNECTED, new ConnectionEventListener() {
                @Override
                public void onConnectionStateChange(ConnectionStateChange change) {
                    if (change.getCurrentState() == ConnectionState.DISCONNECTED && !isStoppingOrStopped()) {
                        LOG.info("App {} disconnected. Reconnecting...");
                        finalPusher.connect();
                    }
                }

                @Override
                public void onError(String message, String code, Exception e) {
                    LOG.error("Message: {}, code: {}", message, code);
                }
            });
        }

        Channel channel = channels.get(channelId);
        if (subscriptions.add(channelId)) {

            // Subscribe to the channel
            LOG.debug("Subscribing to channel {}", channelId);

            // Set up callbacks appropriate to the type of channel - public, private or presence
            PresenceChannelEventListener presenceListener = subscriptionListener(channelId);
            if (channelName.startsWith("private-")) {
                channel = pusher.subscribePrivate(channelName, presenceListener);
            } else if (channelName.startsWith("presence-")) {
                channel = pusher.subscribePresence(channelName, presenceListener);
            } else {
                channel = pusher.subscribe(channelName, presenceListener);
            }

            join(); // Wait for channel subscription
            LOG.info("Subscribed to channel {}", channelId);

            channels.put(channelId, channel);
        }

        // Create a new endpoint for this URI, and set properties on it
        final PusherClientEndpoint endpoint = new PusherClientEndpoint(uri, this, appKey, channel);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    public void registerForPresenceEvents(PusherClientConsumer consumer) {
        String channelName = consumer.getEndpoint().getChannel().getName();
        String channelId = consumer.getEndpoint().getChannelId();
        // Add to collection for presence channel consumers for this channelId, so that
        // the consumer will be triggered when we receive presence events on this channel
        addToPresenceChannelConsumers(channelId, consumer);
        // Trigger synthetic subscription succeeded event, so that the first message the consumer receives
        // will be a list of all users currently present
        consumer.handleEvent(channelName, SUBSCRIPTION_SUCCEEDED_EVENT, presenceChannelUsers.get(channelId));
    }

    // Causes the caller thread to block until an object is placed in the sync queue/
    // If an Exception is place in the queue, it this thrown by the calling thread.
    private void join() throws Exception {
        while (true) {
            Object result = null;
            try {
                 result = queue.take();
                if (result instanceof Exception) {
                    throw (Exception) result;
                }
                break;
            } catch (InterruptedException e) {
                if (result != null) throw e;
            }
        }
    }

    // Place an object in the sync queue, indicating that the calling thread is finished,
    // and any waiting thread may continue. Placing an Exception into the queue indicates
    // an error condition has occurred that should be handled by the waiting thread.
    private void finished(Object obj) {
        while (true) {
            try {
                queue.put(obj);
                break;
            } catch (InterruptedException e1) {
                // no-op
            }
        }
    }

    /**
     * A channel name alone cannot uniquely identify an channel across multiple apps, but when combined
     * with the app-key, a globally unique channel identifier can be created.
     *
     * @param appKey the id of the pusher app
     * @param channelName the name of the channel
     * @return the id for the given channel, constructed from the appKey and channelName
     */
    public String getChannelId(String appKey, String channelName) {
        return appKey + "/" + channelName;
    }

    // Adds the given Consumer to the collection stored by this class, in a thread-safe manner
    private void addToPresenceChannelConsumers(String channelId, PusherClientConsumer consumer) {
        Set<PusherClientConsumer> updatedConsumers = new HashSet<PusherClientConsumer>();
        updatedConsumers.add(consumer);
        // If no consumers have yet been added for this channel, simply add this user and we're done
        Set<PusherClientConsumer> consumers = presenceChannelConsumers.putIfAbsent(channelId, updatedConsumers);
        if (consumers == null) return;
        while (true) {
            // Add consumer to set of consumers, making sure we don't overwrite any updates being performed by another thread
            updatedConsumers.addAll(consumers);
            if (presenceChannelConsumers.replace(channelId, consumers, updatedConsumers)) break;
            consumers = presenceChannelConsumers.get(channelId);
            updatedConsumers.clear();
            updatedConsumers.add(consumer);
        }
    }

    // Adds the given User to the collection stored by this class, in a thread-safe manner
    private void addToPresenceChannelUsers(String channelId, User user) {
        Set<User> updatedUsers = new HashSet<User>();
        updatedUsers.add(user);
        // If no users have yet been added for this channel, simply add this user and we're done
        Set<User> users = presenceChannelUsers.putIfAbsent(channelId, updatedUsers);
        if (users == null) return;
        while (true) {
            // Add user to set of users, making sure we don't overwrite any updates being performed by another thread
            updatedUsers.addAll(users);
            if (presenceChannelUsers.replace(channelId, users, updatedUsers)) break;
            users = presenceChannelUsers.get(channelId);
            updatedUsers.clear();
            updatedUsers.add(user);
        }
    }

    // Removes the given User to the collection stored by this class, in a thread-safe manner
    private void removeFromPresenceChannelUsers(String channelId, User user) {
        // If no users have yet been added for this channel, there's nothing to do
        Set<User> users = presenceChannelUsers.get(channelId);
        if (users == null) return;
        Set<User> updatedUsers = new HashSet<User>(users);
        while (true) {
            // Remove user from set of users, making sure we don't overwrite any updates being performed by another thread
            updatedUsers.remove(user);
            if (presenceChannelUsers.replace(channelId, users, updatedUsers)) break;
            users = presenceChannelUsers.get(channelId);
            updatedUsers.clear();
            updatedUsers.addAll(users);
        }
    }

    // Sends the given presence event to all Consumers that are subscribed to that channel
    private void dispatchPresenceEvent(String channelId, String channelName, String eventName, Object data) {
        Set<PusherClientConsumer> consumers = presenceChannelConsumers.get(channelId);
        for (PusherClientConsumer consumer: consumers) {
            try {
                consumer.handleEvent(channelName, eventName, data);
            } catch (Exception e) {
                LOG.error("Error dispatching {} event to consumer {}", new Object[]{eventName, consumer});
            }
        }
    }

    // Creates a listener for handling all the presence events on a given channel, making sure
    // that all registered consumers receive the member added and member removed events.
    private PresenceChannelEventListener subscriptionListener(final String channelId) {
        return new PresenceChannelEventListener() {
            @Override
            public void onUsersInformationReceived(String channelName, Set<User> users) {
                LOG.debug("Received user information from channel {}", channelName);
                presenceChannelUsers.put(channelId, users);
                dispatchPresenceEvent(channelId, channelName, SUBSCRIPTION_SUCCEEDED_EVENT, users);
            }

            @Override
            public void userSubscribed(String channelName, User user) {
                LOG.debug("User {} has subscribed to channel {}", user, channelName);
                addToPresenceChannelUsers(channelId, user);
                dispatchPresenceEvent(channelId, channelName, MEMBER_ADDED_EVENT, user);
            }

            @Override
            public void userUnsubscribed(String channelName, User user) {
                LOG.debug("User {} has unsubscribed from channel{}", user, channelName);
                removeFromPresenceChannelUsers(channelId, user);
                dispatchPresenceEvent(channelId, channelName, MEMBER_REMOVED_EVENT, user);
            }

            @Override
            public void onAuthenticationFailure(String message, Exception e) {
                LOG.error("Authentication failure: {}", message);
                finished(e);
            }

            @Override
            public void onSubscriptionSucceeded(String channelName) {
                LOG.info("Successfully subscribed to channel {}", channelId);
                finished(channelName);
            }

            @Override
            public void onEvent(String channelName, String eventName, String data) {
                LOG.info("Received event '{}' from channel '{}': {}", new Object[]{eventName, channelName, data});
            }
        };
    }

    /**
     * Disconnect all Pusher instances when stopping the component
     * @throws Exception
     */
    @Override
    protected void doStop() throws Exception {
        LOG.info("Stopping {}", getClass().getSimpleName());
        super.doStop();
        // Disconnect from any currently connected apps
        for (Pusher pusher : apps.values()) {
            try {
                pusher.disconnect();
            } catch (Exception e) {
                // no-op
            }
        }
    }
}
