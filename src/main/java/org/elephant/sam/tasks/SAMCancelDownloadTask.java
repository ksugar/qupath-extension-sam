package org.elephant.sam.tasks;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.Objects;

import org.elephant.sam.Utils;
import org.elephant.sam.http.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.concurrent.Task;

/**
 * Task to cancel a download.
 */
public class SAMCancelDownloadTask extends Task<Boolean> {

    private static final Logger logger = LoggerFactory.getLogger(SAMCancelDownloadTask.class);

    private final String serverURL;

    private final boolean verifySSL;

    /**
     * Create a new task.
     * 
     * @param builder
     *            The builder.
     */
    public SAMCancelDownloadTask(Builder builder) {
        this.serverURL = builder.serverURL;
        this.verifySSL = builder.verifySSL;
        Objects.requireNonNull(serverURL, "Server must not be null!");
    }

    @Override
    protected Boolean call() throws InterruptedException, IOException {
        final String endpointURL = String.format("%sweights/cancel/", Utils.ensureTrailingSlash(serverURL));
        HttpResponse<String> response = HttpUtils.getRequest(endpointURL, verifySSL);
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

        public SAMCancelDownloadTask build() {
            return new SAMCancelDownloadTask(this);
        }
    }

}
