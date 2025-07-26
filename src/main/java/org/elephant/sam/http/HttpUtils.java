package org.elephant.sam.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class HttpUtils {

    static class ContentResponseHandler extends AbstractHttpClientResponseHandler<HttpResponse<String>> {

        private HttpResponse<String> createHttpResponse(final int statusCode, final String body) {
            return new HttpResponse<String>() {
                @Override
                public int statusCode() {
                    return statusCode;
                }

                @Override
                public HttpHeaders headers() {
                    return null;
                }

                @Override
                public String body() {
                    return body;
                }

                @Override
                public Optional<HttpResponse<String>> previousResponse() {
                    return null;
                }

                @Override
                public HttpRequest request() {
                    return null;
                }

                @Override
                public Optional<SSLSession> sslSession() {
                    return Optional.empty();
                }

                @Override
                public URI uri() {
                    return null;
                }

                @Override
                public Version version() {
                    return null;
                }
            };
        }

        @Override
        public HttpResponse<String> handleEntity(HttpEntity entity) throws IOException {
            try {
                return createHttpResponse(HttpStatus.SC_OK, EntityUtils.toString(entity));
            } catch (ParseException e) {
                throw new IOException(e);
            }
        }

        /**
         * Handles a successful response (2xx status code) and returns the response entity as a {@link Content} object.
         * If no response entity exists, {@link Content#NO_CONTENT} is returned.
         *
         * @param response
         *            the HTTP response.
         * @return a {@link Content} object that encapsulates the response body, or {@link Content#NO_CONTENT} if the
         *         response body is {@code null} or has zero length.
         * @throws HttpResponseException
         *             if the response was unsuccessful (status code greater than 300).
         * @throws IOException
         *             if an I/O error occurs.
         */
        @Override
        public HttpResponse<String> handleResponse(final ClassicHttpResponse response)
                throws IOException {
            final int statusCode = response.getCode();
            final HttpEntity entity = response.getEntity();
            try {
                return createHttpResponse(statusCode, EntityUtils.toString(entity));
            } catch (ParseException e) {
                throw new IOException(e);
            }
        }
    }

    private static ContentResponseHandler responseHandler = new ContentResponseHandler();

    private static SSLContext sslContext;

    private static HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;

    private static DefaultClientTlsStrategy sslSocketFactory = new DefaultClientTlsStrategy(
            getSSLContextWithoutCertificateValidation(),
            hostnameVerifier);

    /**
     * Get an SSL context without certificate validation.
     *
     * @return the SSL context
     */
    public static SSLContext getSSLContextWithoutCertificateValidation() {
        if (sslContext == null) {
            TrustStrategy trustStrategy = new TrustAllStrategy();
            try {
                sslContext = SSLContexts
                        .custom()
                        .loadTrustMaterial(trustStrategy)
                        .build();
            } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
                e.printStackTrace();
            }
        }
        return sslContext;
    }

    /**
     * Create a new HTTP client.
     *
     * @param verifySSL
     *            whether to verify SSL certificates
     * @return the HTTP client
     */
    public static CloseableHttpClient newHttpClient(boolean verifySSL) {
        if (verifySSL) {
            return HttpClients.createDefault();
        } else {
            final HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder
                    .create()
                    .setTlsSocketStrategy(sslSocketFactory)
                    .build();
            return HttpClients
                    .custom()
                    .setConnectionManager(connectionManager)
                    .evictExpiredConnections()
                    .build();
        }
    }

    /**
     * Send a GET request.
     *
     * @param endpointURL
     *            the URL to send the request to
     * @param verifySSL
     *            whether to verify SSL certificates
     * @return the response
     * @throws IOException
     *             if an I/O error occurs
     * @throws InterruptedException
     *             if the operation is interrupted
     */
    public static HttpResponse<String> getRequest(String endpointURL, boolean verifySSL)
            throws IOException, InterruptedException {
        try (CloseableHttpClient httpClient = newHttpClient(verifySSL)) {
            ClassicHttpRequest request = new HttpGet(endpointURL);
            return httpClient.execute(request, responseHandler);
        }
    }

    /**
     * Send a POST request.
     *
     * @param endpointURL
     *            the URL to send the request to
     * @param verifySSL
     *            whether to verify SSL certificates
     * @param body
     *            the body of the request
     * @return the response
     * @throws IOException
     *             if an I/O error occurs
     */
    public static HttpResponse<String> postRequest(String endpointURL, boolean verifySSL, String body)
            throws IOException {
        try (CloseableHttpClient httpClient = newHttpClient(verifySSL)) {
            HttpPost request = new HttpPost(endpointURL);
            request.addHeader("accept", "application/json");
            request.addHeader("Content-Type", "application/json; charset=utf-8");
            HttpEntity entity = new StringEntity(body, ContentType.APPLICATION_JSON);
            request.setEntity(entity);
            return httpClient.execute(request, responseHandler);
        }
    }

    /**
     * Send a POST request with a multipart entity.
     *
     * @param endpointURL
     *            the URL to send the request to
     * @param verifySSL
     *            whether to verify SSL certificates
     * @param httpEntityBuilder
     *            the builder for the multipart entity
     * @return the response
     * @throws IOException
     *             if an I/O error occurs
     */
    public static HttpResponse<String> postMultipartRequest(String endpointURL, boolean verifySSL,
            MultipartEntityBuilder httpEntityBuilder)
            throws IOException {
        try (CloseableHttpClient httpClient = newHttpClient(verifySSL)) {
            final String boundary = "----------------" + System.currentTimeMillis();
            HttpPost request = new HttpPost(endpointURL);
            request.addHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
            httpEntityBuilder.setBoundary(boundary);
            request.setEntity(httpEntityBuilder.build());
            return httpClient.execute(request, responseHandler);
        }
    }
}
