package org.elephant.sam.tasks;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import javafx.concurrent.Task;

import org.elephant.sam.entities.SAMType;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A task to fetch SAM weights.
 * <p>
 * This task is designed to be run in a background thread, and will return a list of SAM weights.
 * <p>
 */
public class SAMFetchWeightsTask extends Task<List<SAMWeights>> {

    private static final Logger logger = LoggerFactory.getLogger(SAMFetchWeightsTask.class);

    private final String serverURL;

    private final SAMType samType;

    private SAMFetchWeightsTask(Builder builder) {
        this.serverURL = builder.serverURL;
        Objects.requireNonNull(serverURL, "Server must not be null!");

        this.samType = builder.samType;
        Objects.requireNonNull(samType, "Model must not be null!");
    }

    @Override
    protected List<SAMWeights> call() throws Exception {
        try {
            List<SAMWeights> weights = getWeights();
            return weights;
        } catch (InterruptedException e) {
            logger.warn("Interrupted while fetching URLs", e);
            return Collections.emptyList();
        }
    }

    private List<SAMWeights> getWeights() throws InterruptedException, IOException {

        if (isCancelled())
            return Collections.emptyList();

        HttpResponse<String> response = sendRequest(serverURL, samType);

        if (isCancelled())
            return Collections.emptyList();

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            return parseResponse(response);
        } else {
            logger.error("HTTP response: {}, {}", response.statusCode(), response.body());
            return Collections.emptyList();
        }
    }

    private static HttpResponse<String> sendRequest(String serverURL, SAMType model)
            throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(URI.create(String.format("%sweights/?type=%s", serverURL, model.modelName())))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private List<SAMWeights> parseResponse(HttpResponse<String> response) {
        Gson gson = GsonTools.getInstance();
        JsonElement element = gson.fromJson(response.body(), JsonElement.class);
        return element.getAsJsonArray().asList().stream()
                .map(e -> gson.fromJson(e, SAMWeights.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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
        private SAMType samType;

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
        public Builder samType(final SAMType samType) {
            this.samType = samType;
            return this;
        }

        /**
         * Build the fetch task.
         * 
         * @return
         */
        public SAMFetchWeightsTask build() {
            return new SAMFetchWeightsTask(this);
        }

    }

}
