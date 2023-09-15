package org.elephant.sam.tasks;

import com.google.gson.Gson;
import javafx.concurrent.Task;

import org.elephant.sam.entities.SAMWeights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.io.GsonTools;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

/**
 * A task to register SAM weights.
 * <p>
 * This task is designed to be run in a background thread.
 * <p>
 */
public class SAMRegisterWeightsTask extends Task<String> {

    private static final Logger logger = LoggerFactory.getLogger(SAMRegisterWeightsTask.class);

    private final String serverURL;

    private final String samType;

    private final String name;

    private final String url;

    private SAMRegisterWeightsTask(Builder builder) {
        this.serverURL = builder.serverURL;
        Objects.requireNonNull(serverURL, "Server must not be null!");

        this.samType = builder.samType;
        Objects.requireNonNull(samType, "Model type must not be null!");

        this.name = builder.name;
        Objects.requireNonNull(name, "Name must not be null!");

        this.url = builder.url;
        Objects.requireNonNull(url, "URL must not be null!");
    }

    @Override
    protected String call() throws Exception {
        try {
            if (isCancelled())
                return "Registration task cancelled";

            HttpResponse<String> response = sendRequest(serverURL, new SAMWeights(samType, name, url));

            if (isCancelled())
                return "Registration task cancelled";

            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                return response.body();
            } else {
                logger.error("HTTP response: {}, {}", response.statusCode(), response.body());
                return "Registration request failed";
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while registering URLs", e);
            return "Registration task cancelled";
        }
    }

    private static HttpResponse<String> sendRequest(String serverURL, SAMWeights samWeights)
            throws IOException, InterruptedException {
        final Gson gson = GsonTools.getInstance();
        final String body = gson.toJson(samWeights);
        final HttpRequest request = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(URI.create(String.format("%sweights/", serverURL)))
                .header("accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * New builder for a SAMGetWeightURLs class.
     * 
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String serverURL;
        private String samType;
        private String name;
        private String url;

        /**
         * Specify the server URL (required).
         * 
         * @param serverURL
         * @return this builder
         */
        public Builder serverURL(final String serverURL) {
            this.serverURL = serverURL;
            return this;
        }

        /**
         * Specify the SAM type to use.
         * 
         * @param samType
         * @return this builder
         */
        public Builder samType(final String samType) {
            this.samType = samType;
            return this;
        }

        /**
         * Specify the SAM weights name.
         * 
         * @param name
         * @return this builder
         */
        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        /**
         * Specify the SAM weights URL.
         * 
         * @param url
         * @return this builder
         */
        public Builder url(final String url) {
            this.url = url;
            return this;
        }

        /**
         * Build the registration task.
         * 
         * @return
         */
        public SAMRegisterWeightsTask build() {
            return new SAMRegisterWeightsTask(this);
        }

    }

}
