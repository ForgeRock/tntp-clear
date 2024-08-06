/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */
package org.forgerock.am.marketplace.clear;

import static org.forgerock.am.marketplace.clear.ClearNode.OutcomeProvider.CLIENT_ERROR_OUTCOME_ID;
import static org.forgerock.am.marketplace.clear.ClearNode.OutcomeProvider.DENY_OUTCOME_ID;
import static org.forgerock.am.marketplace.clear.ClearNode.OutcomeProvider.INDETERMINATE_OUTCOME_ID;
import static org.forgerock.am.marketplace.clear.ClearNode.OutcomeProvider.PERMIT_OUTCOME_ID;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.integration.pingone.PingOneWorkerService;
import org.forgerock.util.i18n.PreferredLocales;
import org.forgerock.openam.core.realms.Realm;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Clear node lets administrators verify users using a link to CLEAR's hosted UI inside a journey.
 */
@Node.Metadata(outcomeProvider = ClearNode.OutcomeProvider.class,
               configClass = ClearNode.Config.class,
               tags = {"marketplace", "trustnetwork"})
public class ClearNode extends SingleOutcomeNode {

    private static final Logger logger = LoggerFactory.getLogger(ClearNode.class);
    private static final String LOGGER_PREFIX = "[PingOneAuthorizeNode]" + ClearPlugin.LOG_APPENDER;

    private static final String BUNDLE = ClearNode.class.getName();

    // Attribute keys
    public static final String STATEMENTCODESATTR = "statementCodes";
    public static final String USECONTINUEATTR = "useContinue";

    // Outcomes
    private static final String PERMIT = "PERMIT";
    private static final String DENY = "DENY";
    private static final String INDETERMINATE = "INDETERMINATE";

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
         * @return The Redirect URL shared state attribute
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
            // API call to create verification session
            JsonValue verificationSessionResponse = client.createVerificationSession(
                    config.apiKey(),
                    config.projectId(),
                    config.redirectUrl()
            );

            /* TO-DO:
                Create Redirect
             */

//            if (responseCode == 200) {
//                JSONObject jo = new JSONObject(streamToString);
//                redirectURL = (String) jo.getJSONArray("tokens").getJSONObject(0).getString("accessUrl");
//
//
//                RedirectCallback redirectCallback = new RedirectCallback(redirectURL, null, "GET");
//                redirectCallback.setTrackingCookie(true);
//                return Action.send(redirectCallback).build();
//            }

            // Create and send API call
            JsonValue verificationResultsResponse = client.retrieveUserVerificationResults(
                    config.apiKey(),
                    config.projectId()
            );

            // The API response's "decision" value will determine which outcome is executed
            String decision = "";
            switch (decision) {
                case PERMIT:
                    return Action.goTo(PERMIT_OUTCOME_ID).build();
                case DENY:
                    return Action.goTo(DENY_OUTCOME_ID).build();
                case INDETERMINATE:
                    return Action.goTo(INDETERMINATE_OUTCOME_ID).build();
                default:
                    return Action.goTo(CLIENT_ERROR_OUTCOME_ID).build();
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

    public static class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {

        static final String PERMIT_OUTCOME_ID = "permit";
        static final String DENY_OUTCOME_ID = "deny";
        static final String INDETERMINATE_OUTCOME_ID = "indeterminate";
        static final String CONTINUE_OUTCOME_ID = "continue";
        static final String CLIENT_ERROR_OUTCOME_ID = "clientError";

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) throws NodeProcessException {

            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());

            ArrayList<Outcome> outcomes = new ArrayList<>();

            // Retrieves the current state of the continue button
            String useContinue = nodeAttributes.get(USECONTINUEATTR).toString();

            // Do not render other outcomes if button = "true"
            if (useContinue.contains("true")) {
                outcomes.add(new Outcome(CONTINUE_OUTCOME_ID, bundle.getString(CONTINUE_OUTCOME_ID)));
            } else {
                outcomes.add(new Outcome(PERMIT_OUTCOME_ID, bundle.getString(PERMIT_OUTCOME_ID)));
                outcomes.add(new Outcome(DENY_OUTCOME_ID, bundle.getString(DENY_OUTCOME_ID)));
                outcomes.add(new Outcome(INDETERMINATE_OUTCOME_ID, bundle.getString(INDETERMINATE_OUTCOME_ID)));
                if (nodeAttributes.isNotNull()) {
                    // nodeAttributes is null when the node is created
                    nodeAttributes.get(STATEMENTCODESATTR).required()
                                  .asList(String.class)
                                  .stream()
                                  .map(outcome -> new Outcome(outcome, outcome))
                                  .forEach(outcomes::add);
                }
            }
            outcomes.add(new Outcome(CLIENT_ERROR_OUTCOME_ID, bundle.getString(CLIENT_ERROR_OUTCOME_ID)));

            return outcomes;
        }
    }
}
