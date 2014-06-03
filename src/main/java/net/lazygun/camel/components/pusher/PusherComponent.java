package net.lazygun.camel.components.pusher;

import java.util.Map;

import com.pusher.client.channel.Channel;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

/**
 * Represents the component that manages {@link PusherEndpoint}.
 */
public class PusherComponent extends DefaultComponent {

    public static final String APP_KEY = "pusher.appKey";
    public static final String CHANNEL = "pusher.channel";
    public static final String EVENT_NAME = "pusher.eventName";

    public static final String SUBSCRIBE_EVENT = "pusher:subscribe";
    public static final String USER_INFORMATION_RECEIVED_EVENT = "pusher:user_information_received";
    public static final String MEMBER_ADDED_EVENT = "pusher:member_added";
    public static final String MEMBER_REMOVED_EVENT = "pusher:member_removed";

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        PusherEndpoint endpoint = new PusherEndpoint(uri, this);
        String[] path = remaining.split("/");
        if (path.length != 2) {
            throw new IllegalArgumentException("Pusher uri path must contain app key and channel name: pusher://<app_key>/<channel_name>");
        }
        endpoint.setAppKey(path[0]);
        endpoint.setChannel(path[1]);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
