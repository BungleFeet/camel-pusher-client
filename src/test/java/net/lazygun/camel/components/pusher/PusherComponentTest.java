package net.lazygun.camel.components.pusher;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class PusherComponentTest extends CamelTestSupport {

    public static final String PUSHER_TEST_APP = "22364f2f790269bec0a0"; // see http://test.pusher.com
    public static final String PUSHER_TEST_CHANNEL = "channel";

    @Test
    public void testPusher() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);       
        
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("pusher://" + PUSHER_TEST_APP + "/" + PUSHER_TEST_CHANNEL)
                  .to("mock:result");
            }
        };
    }
}
