/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */
package org.forgerock.am.marketplace.clear;

import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import java.io.IOException;
import java.net.URI;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.forgerock.http.header.MalformedHeaderException;
import org.forgerock.http.header.authorization.BearerToken;
import org.forgerock.http.header.AuthorizationHeader;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.Handler;
import org.forgerock.json.JsonValue;
import org.forgerock.services.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service to integrate with CLEARs API and UI.
 */
@Singleton
public class ClearClient {

    private static final Logger logger = LoggerFactory.getLogger(ClearClient.class);

    private static final String VERSION_PATH = "/v1/";
    private static final String VERIFICATION_SESSION_PATH = "/verification_sessions/";

    private final Handler handler;

    /**
     * Creates a new instance that will close the underlying HTTP client upon shutdown.
     */
    @Inject
    public ClearClient(@Named("CloseableHttpClientHandler") org.forgerock.http.Handler handler) {
        this.handler = handler;
    }

    /**
     * the POST {{apiPath}}/v1/verification_sessions creates a verification_session on your server.
     *
     * @param apiKey The Clear API Key
     * @param projectId The project_id of the desired Clear project
     * @param redirectUrl The Redirect URL for after the verification session
     * @return Json containing the response from the operation
     * @throws ClearServiceException When API response != 200
     */
    public JsonValue createVerificationSession(
        String apiKey,
        String projectId,
        String redirectUrl) throws ClearServiceException {

        // Create the request url
        Request request;

        URI uri = URI.create(
            "https://verified.clearme.com" +
                    "/v1/" +
                    "/verification_sessions/");

        // Create the request body
        JsonValue parameters = json(object(1));
        parameters.put("project_id", projectId);
        parameters.put("redirect_url", redirectUrl);

        try {
            request = new Request().setUri(uri).setMethod("POST");
            request.getEntity().setJson(parameters);
            addAuthorizationHeader(request, apiKey);
            Response response = handler.handle(new RootContext(), request).getOrThrow();
            if (response.getStatus() == Status.CREATED || response.getStatus() == Status.OK) {
                return json(response.getEntity().getJson());
            } else {
                throw new ClearServiceException("Clear API response with error."
                                                        + response.getStatus()
                                                        + "-" + response.getEntity().getString());
            }
        } catch (MalformedHeaderException | InterruptedException | IOException e) {
            throw new ClearServiceException("Failed to process client authorization" + e);
        }
    }

    /**
     * the POST {{apiPath}}/v1/verification_sessions/{verification_session_id} retrieves data
     * from a specific verification session using the Verification Session ID.
     *
     * @param apiKey The Clear API Key
     * @param verificationSessionId The Clear Verification Session ID
     * @return Json containing the response from the operation
     * @throws ClearServiceException When API response != 200
     */
    public JsonValue retrieveUserVerificationResults(
            String apiKey,
            String verificationSessionId) throws ClearServiceException {

        // Create the request url
        Request request;

        URI uri = URI.create(
                "https://verified.clearme.com" +
                        "/v1/" +
                        "/verification_sessions/" +
                        verificationSessionId);

        try {
            request = new Request().setUri(uri).setMethod("GET");
            addAuthorizationHeader(request, apiKey);
            Response response = handler.handle(new RootContext(), request).getOrThrow();
            if (response.getStatus() == Status.CREATED || response.getStatus() == Status.OK) {
                return json(response.getEntity().getJson());
            } else {
                throw new ClearServiceException("Clear API response with error."
                        + response.getStatus()
                        + "-" + response.getEntity().getString());
            }
        } catch (MalformedHeaderException | InterruptedException | IOException e) {
            throw new ClearServiceException("Failed to process client authorization" + e);
        }
    }

    /**
     * Add the Authorization header to the request.
     *
     * @param request The request to add the header
     * @param accessToken The accessToken to add the header
     * @throws MalformedHeaderException When failed to add the header
     */
    private void addAuthorizationHeader(Request request, String accessToken) throws MalformedHeaderException {
        AuthorizationHeader header = new AuthorizationHeader();
        BearerToken bearerToken = new BearerToken(accessToken);
        header.setRawValue(BearerToken.NAME + " " + bearerToken);
        request.addHeaders(header);
    }
}
