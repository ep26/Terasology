/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.telemetry;

import com.snowplowanalytics.snowplow.tracker.emitter.BatchEmitter;
import com.snowplowanalytics.snowplow.tracker.emitter.RequestCallback;
import com.snowplowanalytics.snowplow.tracker.http.ApacheHttpClientAdapter;
import com.snowplowanalytics.snowplow.tracker.http.HttpClientAdapter;
import com.snowplowanalytics.snowplow.tracker.payload.TrackerPayload;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * TelemetryEmitter emit metrics to the telemetry server.
 * @see <a href="https://github.com/snowplow/snowplow/wiki/Java-Tracker#emitters">https://github.com/snowplow/snowplow/wiki/Java-Tracker#emitterss</a>

 */
public class TelemetryEmitter extends BatchEmitter {

    public static final String DEFAULT_COLLECTOR_PROTOCOL = "http";

    public static final String DEFAULT_COLLECTOR_HOST = "utility.terasology.org";

    public static final String DEFAULT_COLLECTOR_OWNER = "Terasology Community";

    public static final String DEFAULT_COLLECTOR_NAME = "TelemetryCollector";

    public static final int DEFAULT_COLLECTOR_PORT = 14654;

    private static final Logger logger = LoggerFactory.getLogger(TelemetryEmitter.class);

    private long closeTimeout = 5;

    public abstract static class Builder<T extends Builder<T>> extends BatchEmitter.Builder<T> {

        public TelemetryEmitter build() {

            URL url = getDefaultCollectorURL(DEFAULT_COLLECTOR_PROTOCOL, DEFAULT_COLLECTOR_HOST, DEFAULT_COLLECTOR_PORT);
            HttpClientAdapter httpClientAdapter = getDefaultAdapter(url);
            this.httpClientAdapter(httpClientAdapter);

            RequestCallback  requestCallback = getDefaultRequestCallback();
            this.requestCallback(requestCallback);

            // TODO: use the proper bufferSize, bufferSize 1 for test
            this.bufferSize(1);

            return new TelemetryEmitter(this);
        }
    }

    private static class Builder2 extends Builder<Builder2> {

        @Override
        protected Builder2 self() {
            return this;
        }
    }

    public static Builder<?> builder() {
        return new Builder2();
    }

    protected TelemetryEmitter(Builder<?> builder) {
        super(builder);
    }

    public static URL getDefaultCollectorURL(String protocol, String host, int port) {
        URL url = null;
        try {
            url = new URL(protocol, host, port, "");
        } catch (MalformedURLException e) {
            logger.error("Telemetry server URL mal formed", e);
        }
        return url;
    }

    private static HttpClientAdapter getDefaultAdapter(URL url) {

        // Make a new client with custom concurrency rules
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setDefaultMaxPerRoute(50);

        // Make the client
        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(manager)
                .build();

        // Build the adapter
        return ApacheHttpClientAdapter.builder()
                .url(url.toString())
                .httpClient(client)
                .build();
    }

    private static RequestCallback getDefaultRequestCallback() {

        RequestCallback callback = new RequestCallback() {

            public void onSuccess(int successCount) {
                logger.info("Success sent, successCount: " + successCount);
            }

            public void onFailure(int successCount, List<TrackerPayload> failedEvents) {
                logger.warn("Failure, successCount: " + successCount + "\nfailedEvent:\n" + failedEvents.toString());
            }
        };

        return callback;
    }

    public void changeUrl(URL url) {

        HttpClientAdapter httpClientAdapter = getDefaultAdapter(url);
        this.httpClientAdapter = httpClientAdapter;
    }

    // TODO: remove it if the snowplow unclosed issue is fixed
    @Override
    public void close() {
        flushBuffer();
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(closeTimeout, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(closeTimeout, TimeUnit.SECONDS)) {
                        logger.warn("Executor did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
