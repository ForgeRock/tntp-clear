/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */
package org.forgerock.am.marketplace.clear;

import static org.forgerock.am.marketplace.clear.ClearNode.ClearOutcomeProvider.CLIENT_ERROR_OUTCOME_ID;

import java.util.ResourceBundle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.forgerock.openam.auth.node.api.StaticOutcomeProvider;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.util.i18n.PreferredLocales;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.json.JsonValue;

import com.sun.identity.authentication.spi.RedirectCallback;
import com.sun.identity.sm.RequiredValueValidator;
import com.google.inject.assistedinject.Assisted;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * The Clear node lets administrators verify users using a link to CLEAR's hosted UI inside a journey.
 */
@Node.Metadata(outcomeProvider = ClearNode.ClearOutcomeProvider.class,
               configClass = ClearNode.Config.class,
               tags = {"marketplace", "trustnetwork"})
public class ClearNode extends SingleOutcomeNode {

    private static final Logger logger = LoggerFactory.getLogger(ClearNode.class);
    private static final String LOGGER_PREFIX = "[ClearNode]" + ClearPlugin.LOG_APPENDER;

    private static final String BUNDLE = ClearNode.class.getName();

    private final Config config;
    private final ClearClient client;

    /**
     * Configuration for the node.
     */
    public interface Config {
        /**
         * Shared state attribute containing Clear API Key
         *
         * @return The Clear API Key shared state attribute
         */
        @Attribute(order = 100, requiredValue = true)
        String apiKey();

        /**
         * Shared state attribute containing Clear Project ID
         *
         * @return The Clear Project ID shared state attribute
         */
        @Attribute(order = 200, validators = {RequiredValueValidator.class})
        String projectId();

        /**
         * Shared state attribute containing the Redirect URL
         *
         * @return The Redirect URL shared state attribute    redirectUrl
         */
        @Attribute(order = 300)
        String redirectUrl();
    }

    /**
     * The Clear node constructor.
     *
     * @param config               the node configuration.
     * @param client               the {@link ClearClient} instance.
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

        try {
            // Check if verification session id is set in sharedState
            String sharedStateSessionId = nodeState.isDefined("sessionId")
                    ? nodeState.get("sessionId").asString()
                    : null;

            logger.error("SHARED STATE SESSION ID: {}", sharedStateSessionId);

            if (StringUtils.isBlank(sharedStateSessionId)) {
                logger.error("<----------------- CREATING VERIFICATION SESSION ----------------->");
                // API call to create verification session
                JsonValue verificationSessionResponse = client.createVerificationSession(
                        config.apiKey(),
                        config.projectId(),
                        config.redirectUrl()
                );

                // Clear verification session id for the GET request after we redirect
                String verificationSessionId = verificationSessionResponse.get("id").asString();
                logger.error("VERIFICATION SESSION ID: {}", verificationSessionId);
                nodeState.putShared("sessionId", verificationSessionId);
                logger.error("SESSION ID IN SHARED STATE: {}", nodeState.get("sessionId").asString());

                // Retrieves the verification session token for CLEAR redirect
                String sessionToken = verificationSessionResponse.get("token").asString();

                // Redirect URL
                RedirectCallback redirectCallback = new RedirectCallback(
                        "https://verified.clearme.com/verify?token=" + sessionToken,
                        null,
                        "GET"
                );

                redirectCallback.setTrackingCookie(true);
                return Action.send(redirectCallback).build();

            } else {
                logger.error("<----------------- RETRIEVING VERIFICATION RESULTS ----------------->");
                // API Call to retrieve user verification results
                JsonValue verificationResultsResponse = client.getUserVerificationResults(
                        config.apiKey(),
                        sharedStateSessionId
                );

                // Retrieve the API response
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

    @Override
    public InputState[] getInputs() {
        return new InputState[] {
                new InputState(config.apiKey(), false),
                new InputState(config.projectId(), false),
                new InputState(config.redirectUrl(), false)
        };
    }

    @Override
    public OutputState[] getOutputs() {
        return new OutputState[]{
            new OutputState("decision")
        };
    }

    public static class ClearOutcomeProvider implements StaticOutcomeProvider {

        static final String CONTINUE_OUTCOME_ID = "continue";
        static final String CLIENT_ERROR_OUTCOME_ID = "clientError";

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, ClearOutcomeProvider.class.getClassLoader());

            ArrayList<Outcome> outcomes = new ArrayList<>();

            outcomes.add(new Outcome(CONTINUE_OUTCOME_ID, bundle.getString(CONTINUE_OUTCOME_ID)));
            outcomes.add(new Outcome(CLIENT_ERROR_OUTCOME_ID, bundle.getString(CLIENT_ERROR_OUTCOME_ID)));

            return outcomes;
        }
    }
}
