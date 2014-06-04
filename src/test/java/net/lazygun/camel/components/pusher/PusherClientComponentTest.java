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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static net.lazygun.camel.components.pusher.PusherClientComponent.SCHEME;

public class PusherClientComponentTest extends CamelTestSupport {

    public static final String TEST_APP = "22364f2f790269bec0a0"; // see http://test.pusher.com
    public static final String TEST_CHANNEL = "channel";
    public static final String TEST_EVENT_NAME = "event";
    public static final String TEST_TRIGGER_URL = "http://test.pusher.com/hello";

    @Test
    public void testPusher() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        triggerTestMessage();
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final String uri = SCHEME + "://" + TEST_APP + "/" + TEST_CHANNEL + "?events=" + TEST_EVENT_NAME;
        return new RouteBuilder() {
            public void configure() {
                from(uri)
                  .to("mock:result");
            }
        };
    }

    private void triggerTestMessage() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(TEST_TRIGGER_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.getContent();
    }
}
