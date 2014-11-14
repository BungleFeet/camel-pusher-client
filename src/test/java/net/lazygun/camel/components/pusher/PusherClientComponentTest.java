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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static net.lazygun.camel.components.pusher.PusherClientComponent.SCHEME;

/**
 * Test the PusherClientComponent against a live channel. When running this test,
 * you must set the following system properties:
 * <ul>
 *     <li>{@code test.app} - the key of the pusher app to connect to</li>
 *     <li>{@code test.channel} - the name of the channel to subscribe to</li>
 *     <li>{@code test.event} - the name of the event to listen for</li>
 * </ul>
 * The Pusher app which you are testing against must produce an event of the set
 * name on the set channel at least once every 10 seconds for this test to pass.
 */
public class PusherClientComponentTest extends CamelTestSupport {

    public static final String TEST_APP = System.getProperty("test.app");
    public static final String TEST_CHANNEL = System.getProperty("test.channel");
    public static final String TEST_EVENT_NAME = System.getProperty("test.event");

    @Test
    public void testPusher() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final String uri = SCHEME + "://" + TEST_APP + "/" + TEST_CHANNEL + "?events=" + TEST_EVENT_NAME;
        return new RouteBuilder() {
            public void configure() {
                from(uri).to("mock:result");
            }
        };
    }
}
