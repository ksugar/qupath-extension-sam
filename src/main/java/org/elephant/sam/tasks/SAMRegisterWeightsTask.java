package org.elephant.sam.tasks;

import org.elephant.sam.Utils;
import org.elephant.sam.entities.SAMWeights;
import org.elephant.sam.http.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.concurrent.Task;
import qupath.lib.io.GsonTools;
import java.net.HttpURLConnection;
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

    private final boolean verifySSL;

    private final String samType;

    private final String name;

    private final String url;

    private SAMRegisterWeightsTask(Builder builder) {
        this.serverURL = builder.serverURL;
        Objects.requireNonNull(serverURL, "Server must not be null!");

        this.verifySSL = builder.verifySSL;
        Objects.requireNonNull(verifySSL, "Verify SSL must not be null!");

        this.samType = builder.samType;
        Objects.requireNonNull(samType, "Model type must not be null!");

        this.name = builder.name;
        Objects.requireNonNull(name, "Name must not be null!");

        this.url = builder.url;
        Objects.requireNonNull(url, "URL must not be null!");
    }

    @Override
    protected String call() throws Exception {
        if (isCancelled())
            return "Registration task cancelled";

        final String endpointURL = String.format("%sweights/", Utils.ensureTrailingSlash(serverURL));
        final SAMWeights samWeights = new SAMWeights(samType, name, url);
        HttpResponse<String> response = HttpUtils.postRequest(endpointURL, verifySSL,
                GsonTools.getInstance().toJson(samWeights));

        if (isCancelled())
            return "Registration task cancelled";

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            return response.body();
        } else {
            logger.error("HTTP response: {}, {}", response.statusCode(), response.body());
            return "Registration request failed";
        }
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
        private boolean verifySSL;
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
         * Specify whether to verify SSL (required).
         * 
         * @param verifySSL
         * @return this builder
         */
        public Builder verifySSL(final boolean verifySSL) {
            this.verifySSL = verifySSL;
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
