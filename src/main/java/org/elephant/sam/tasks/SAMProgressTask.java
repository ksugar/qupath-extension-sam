package org.elephant.sam.tasks;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import org.elephant.sam.entities.SAMProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import javafx.concurrent.Task;
import qupath.lib.io.GsonTools;

/**
 * A task to fetch SAM progress.
 * <p>
 * This task is designed to be run in a background thread.
 * <p>
 */
public class SAMProgressTask extends Task<Boolean> {

    private static final Logger logger = LoggerFactory.getLogger(SAMProgressTask.class);

    private final String serverURL;

    public SAMProgressTask(Builder builder) {
        this.serverURL = builder.serverURL;
        Objects.requireNonNull(serverURL, "Server must not be null!");
    }

    @Override
    protected Boolean call() throws InterruptedException, IOException {
        final HttpRequest request = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(URI.create(String.format("%sprogress/", serverURL)))
                .build();
        while (!isCancelled()) {
            final HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                SAMProgress progress = parseResponse(response);
                int percent = progress.getPercent();
                if (0 <= percent) {
                    updateProgress(percent, 100);
                }
                String message = progress.getMessage();
                if (message != null) {
                    updateMessage(message);
                }
            } else {
                logger.error("HTTP response: {}, {}", response.statusCode(), response.body());
                return false;
            }
            Thread.sleep(100);
        }
        return true;
    }

    private SAMProgress parseResponse(HttpResponse<String> response) {
        Gson gson = GsonTools.getInstance();
        return gson.fromJson(response.body(), SAMProgress.class);
    }

    /**
     * New builder for a SAMProgressTask class.
     * 
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String serverURL;

        public Builder serverURL(String serverURL) {
            this.serverURL = serverURL;
            return this;
        }

        public SAMProgressTask build() {
            return new SAMProgressTask(this);
        }
    }

}
