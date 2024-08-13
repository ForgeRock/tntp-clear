/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */
package org.forgerock.am.marketplace.clear;

import static org.forgerock.am.marketplace.clear.ClearNode.ClearOutcomeProvider.CLIENT_ERROR_OUTCOME_ID;

import java.security.SecureRandom;
import java.math.BigInteger;
import java.util.*;

import javax.inject.Inject;


import org.apache.commons.lang.exception.ExceptionUtils;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.util.i18n.PreferredLocales;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.OutcomeProvider;

import com.sun.identity.authentication.spi.RedirectCallback;
import com.sun.identity.sm.RequiredValueValidator;
import com.google.inject.assistedinject.Assisted;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * The Clear node lets administrators verify users using a link to CLEAR's hosted UI inside a journey.
 */
@Node.Metadata(outcomeProvider = ClearNode.ClearOutcomeProvider.class,
               configClass = ClearNode.Config.class,
               tags = {"marketplace", "trustnetwork"})
public class ClearNode implements Node {

    private static final Logger logger = LoggerFactory.getLogger(ClearNode.class);
    private static final String LOGGER_PREFIX = "[ClearNode]" + ClearPlugin.LOG_APPENDER;

    private static final String VERIFICATION_SESSION_ID = "id";
    private static final String VERIFICATION_SESSION_TOKEN = "token";
    private static final String SESSION_ID = "sessionId";
    private static final String NONCE = "nonce";

    private static final String BUNDLE = ClearNode.class.getName();

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Config config;
    private final ClearClient client;

    /**
     * Configuration for the CLEAR node.
     */
    public interface Config {
        /**
         * Shared state attribute containing CLEAR API Key
         *
         * @return The CLEAR API Key shared state attribute
         */
        @Attribute(order = 100, requiredValue = true)
        String apiKey();

        /**
         * Shared state attribute containing CLEAR Project ID
         *
         * @return The CLEAR Project ID shared state attribute
         */
        @Attribute(order = 200, validators = {RequiredValueValidator.class})
        String projectId();

        /**
         * Shared state attribute containing the Redirect URL
         *
         * @return The Redirect URL shared state attribute
         */
        @Attribute(order = 300)
        String redirectUrl();

        /**
         * Toggle attribute that determines the endpoint of the final HTTP GET request
         *
         * @return True if the toggle is set to the Secure Endpoint, otherwise false
         */
        @Attribute(order = 400)
        default boolean secureEndpointToggle(){
            return true;
        }
    }

    /**
     * The CLEAR node constructor.
     *
     * @param config the node configuration.
     * @param client the {@link ClearClient} instance.
     */
    @Inject
    public ClearNode(@Assisted Config config, ClearClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    public Action process(TreeContext context) {

        // Create the flow input based on the node state
        NodeState nodeState = context.getStateFor(this);

        // Store parameters of the API request
        Map<String, List<String>> parameters = context.request.parameters;

        try {
            // Check if verification session id is set in sharedState
            String sharedStateSessionId = nodeState.isDefined(SESSION_ID)
                    ? nodeState.get(SESSION_ID).asString()
                    : null;

            // Checks if NONCE value exists in the API request parameters.
            // If false, create the verification session
            if (!parameters.containsKey(NONCE)) {

                // Create nonce value to include with the API Call's redirect URL
                String nonce = generateNonce();

                // API call to create verification session
                JsonValue verificationSessionResponse = client.createVerificationSession(
                        config.apiKey(),
                        config.projectId(),
                        config.redirectUrl(),
                        nonce
                );

                // Store the `verification_session.id` from the response
                // This will be used to identify which session the GET request will return data for
                String verificationSessionId = verificationSessionResponse.get(VERIFICATION_SESSION_ID).asString();

                // Add `verification_session.id` and the newly generated nonce to node shared state
                nodeState.putShared(SESSION_ID, verificationSessionId);
                nodeState.putShared(NONCE, nonce);

                // Store the `verification_session.token` for the redirect to CLEAR's verification UI
                String sessionToken = verificationSessionResponse.get(VERIFICATION_SESSION_TOKEN).asString();

                // Building and executing the CLEAR redirect URL
                RedirectCallback redirectCallback = new RedirectCallback(
                        "https://verified.clearme.com/verify?token=" + sessionToken,
                        null,
                        "GET"
                );
                redirectCallback.setTrackingCookie(true);
                return Action.send(redirectCallback).build();

            } else {

                // Retrieve the nonce values from authentication tree context
                String nonce = parameters.get(NONCE).get(0);
                String nonceState = nodeState.get(NONCE).asString();

                // Security comparison for nonce values
                if (!nonceState.equals(nonce)) {
                    logger.error("Mismatched nonce value exiting out of journey.");
                    return Action.goTo(CLIENT_ERROR_OUTCOME_ID).build();
                }

                // Check the value of the toggle button for desired endpoint
                String verificationResultsEndpoint;
                if (config.secureEndpointToggle()) {
                    verificationResultsEndpoint = "https://secure.verified.clearme.com";
                } else {
                    verificationResultsEndpoint = "https://verified.clearme.com";
                }

                // API call to check user's authentication status
                JsonValue verificationResultsResponse = client.getUserVerificationResults(
                        verificationResultsEndpoint,
                        config.apiKey(),
                        sharedStateSessionId
                );

                // Store the user's verification results
                nodeState.putTransient("verificationResults", verificationResultsResponse);

                return Action.goTo(ClearOutcomeProvider.CONTINUE_OUTCOME_ID).build();
            }

        } catch (Exception ex) {
            String stackTrace = ExceptionUtils.getStackTrace(ex);
            logger.error(LOGGER_PREFIX + "Exception occurred: ", ex);
            context.getStateFor(this).putTransient(LOGGER_PREFIX + "Exception", new Date() + ": " + ex.getMessage());
            context.getStateFor(this).putTransient(LOGGER_PREFIX + "StackTrace", new Date() + ": " + stackTrace);
            return Action.goTo(CLIENT_ERROR_OUTCOME_ID).build();
        }
    }

    private static String generateNonce() {
        return new BigInteger(160, SECURE_RANDOM).toString(Character.MAX_RADIX);
    }

    @Override
    public InputState[] getInputs() {
        return new InputState[] {
                new InputState(SESSION_ID, false),
                new InputState(NONCE, false)
        };
    }

    @Override
    public OutputState[] getOutputs() {
        return new OutputState[]{
            new OutputState("verificationResults")
        };
    }

    public static class ClearOutcomeProvider implements OutcomeProvider {

        static final String CONTINUE_OUTCOME_ID = "continue";
        static final String CLIENT_ERROR_OUTCOME_ID = "clientError";

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue jsonValue) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, ClearOutcomeProvider.class.getClassLoader());

            ArrayList<Outcome> outcomes = new ArrayList<>();

            outcomes.add(new Outcome(CONTINUE_OUTCOME_ID, bundle.getString(CONTINUE_OUTCOME_ID)));
            outcomes.add(new Outcome(CLIENT_ERROR_OUTCOME_ID, bundle.getString(CLIENT_ERROR_OUTCOME_ID)));

            return outcomes;
        }
    }
}
