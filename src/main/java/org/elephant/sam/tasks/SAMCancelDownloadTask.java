package org.elephant.sam.tasks;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.concurrent.Task;

/**
 * Task to cancel a download.
 */
public class SAMCancelDownloadTask extends Task<Boolean> {

    private static final Logger logger = LoggerFactory.getLogger(SAMCancelDownloadTask.class);

    private final String serverURL;

    /**
     * Create a new task.
     * 
     * @param builder
     *            The builder.
     */
    public SAMCancelDownloadTask(Builder builder) {
        this.serverURL = builder.serverURL;
        Objects.requireNonNull(serverURL, "Server must not be null!");
    }

    @Override
    protected Boolean call() throws InterruptedException, IOException {
        final HttpClient client = HttpClient.newHttpClient();
        final HttpRequest request = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(URI.create(String.format("%sweights/cancel/", serverURL)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            updateMessage(response.body());
        } else {
            logger.error("HTTP response: {}, {}", response.statusCode(), response.body());
            return false;
        }
        return true;
    }

    /**
     * New builder for a SAMCancelDownloadTask class.
     * 
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for a SAMCancelDownloadTask class.
     */
    public static class Builder {

        private String serverURL;

        public Builder serverURL(String serverURL) {
            this.serverURL = serverURL;
            return this;
        }

        public SAMCancelDownloadTask build() {
            return new SAMCancelDownloadTask(this);
        }
    }

}
