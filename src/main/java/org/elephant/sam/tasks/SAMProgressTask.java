package org.elephant.sam.tasks;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.Objects;

import org.elephant.sam.entities.SAMProgress;
import org.elephant.sam.http.HttpUtils;
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

    private final boolean verifySSL;

    public SAMProgressTask(Builder builder) {
        this.serverURL = builder.serverURL;
        this.verifySSL = builder.verifySSL;
        Objects.requireNonNull(serverURL, "Server must not be null!");
    }

    @Override
    protected Boolean call() throws InterruptedException, IOException {
        final String endpointURL = String.format("%sprogress/", serverURL);
        while (!isCancelled()) {
            HttpResponse<String> response = HttpUtils.getRequest(endpointURL, verifySSL);
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

        private boolean verifySSL;

        public Builder serverURL(String serverURL) {
            this.serverURL = serverURL;
            return this;
        }

        /**
         * Specify whether to verify SSL (required).
         * 
         * @param verifySSL
         * @return this builder
         */
        public Builder verifySSL(final boolean verifySSL) {
            this.verifySSL = verifySSL;
            return this;
        }

        public SAMProgressTask build() {
            return new SAMProgressTask(this);
        }
    }

}
