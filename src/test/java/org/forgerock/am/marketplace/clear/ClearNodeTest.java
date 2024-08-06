/*
 * Copyright 2024 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */

package org.forgerock.am.marketplace.clear;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.am.marketplace.clear.ClearNode.STATEMENTCODESATTR;
import static org.forgerock.am.marketplace.clear.ClearNode.USECONTINUEATTR;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.am.marketplace.clear.ClearNode.OutcomeProvider.CLIENT_ERROR_OUTCOME_ID;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import javax.security.auth.callback.Callback;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.forgerock.json.JsonValue;
import org.forgerock.oauth2.core.AccessToken;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.integration.pingone.PingOneWorkerConfig;
import org.forgerock.openam.integration.pingone.PingOneWorkerException;
import org.forgerock.openam.integration.pingone.PingOneWorkerService;
import org.forgerock.openam.test.extensions.LoggerExtension;
import org.forgerock.util.i18n.PreferredLocales;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ClearNodeTest {

    @RegisterExtension
    public LoggerExtension loggerExtension = new LoggerExtension(ClearNode.class);

    @Mock
    ClearNode.Config config;

    @Mock
    PingOneWorkerService pingOneWorkerService;

    @Mock
    AccessToken accessToken;

    @Mock
    PingOneWorkerConfig.Worker worker;

    @Mock
    Realm realm;

    @Mock
    ClearClient client;

    ClearNode node;

    private static final String USER = "testUser";
    public static final String PINGONE_AUTHORIZE_ATTRIBUTE = "some-attribute-key";

    @BeforeEach
    public void setup() throws Exception {
        given(pingOneWorkerService.getWorker(any(), anyString())).willReturn(Optional.of(worker));

        given(pingOneWorkerService.getAccessToken(any(), any())).willReturn(accessToken);

        node = new ClearNode(config, client);
    }

    @Test
    public void testPingOneUserIdNotFoundInSharedState() throws Exception {
        // Given
        JsonValue sharedState = json(object(field(USERNAME, USER), field(REALM, "/realm")));
        JsonValue transientState = json(object());

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(CLIENT_ERROR_OUTCOME_ID);
    }

    @ParameterizedTest
    @CsvSource({
            "PERMIT,permit",
            "DENY,deny",
            "INDETERMINATE,indeterminate",
    })
    public void testReturnOutcomePingOneAuthorize(String decision, String expectedOutcome) throws Exception {
        // Given
        JsonValue sharedState = json(object(
                field(REALM, "/realm")));

        given(config.apiKey()).willReturn("some-api-key");
        given(config.projectId()).willReturn("some-project-id");
        given(config.redirectUrl()).willReturn("some-redirect-url");

        JsonValue response = null;

        if (decision.equals("PERMIT")) {
            response = json(object(
                    field("decision", decision)));
        } else if (decision.equals("DENY")) {
            response = json(object(
                    field("decision", decision)));
        } else if (decision.equals("INDETERMINATE")) {
            response = json(object(
                    field("decision", decision)));
        }

        when(client.createVerificationSession(any(), any(), any())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(expectedOutcome);
    }

    @Test
    public void testPingOneCommunicationFailed() throws Exception {
        // Given
        given(pingOneWorkerService.getAccessToken(any(), any())).willReturn(null);
        given(pingOneWorkerService.getAccessToken(realm, worker)).willThrow(new PingOneWorkerException(""));
        JsonValue sharedState = json(object(
                field(USERNAME, USER),
                field(REALM, "/realm"),
                field(PINGONE_AUTHORIZE_ATTRIBUTE, "some-attribute-value")
                                           ));
        JsonValue transientState = json(object());

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(CLIENT_ERROR_OUTCOME_ID);
    }

    @Test
    public void testGetInputs() {
        given(config.apiKey()).willReturn("");
        given(config.projectId()).willReturn("");
        given(config.redirectUrl()).willReturn("");

        InputState[] inputs = node.getInputs();

        assertThat(inputs[0].name).isEqualTo("");
        assertThat(inputs[0].required).isEqualTo(false);

        assertThat(inputs[1].name).isEqualTo("");
        assertThat(inputs[1].required).isEqualTo(false);
    }

    @Test
    public void testGetOutputs() {
        OutputState[] outputs = node.getOutputs();
        assertThat(outputs[0].name).isEqualTo("decision");
    }

    @Test
    public void testContinueGetOutcomes() throws Exception {
        ClearNode.OutcomeProvider outcomeProvider = new ClearNode.OutcomeProvider();

        JsonValue nodeAttributes = json(object(
            field(USECONTINUEATTR, true)));

        PreferredLocales locales = new PreferredLocales();
        List<OutcomeProvider.Outcome> outcomes = outcomeProvider.getOutcomes(locales, nodeAttributes);

        assertThat(outcomes.get(0).id).isEqualTo("continue");
        assertThat(outcomes.get(0).displayName).isEqualTo("Continue");

        assertThat(outcomes.get(1).id).isEqualTo("clientError");
        assertThat(outcomes.get(1).displayName).isEqualTo("Error");
    }

    @Test
    public void testWithoutContinueGetOutcomes() throws Exception {
        ClearNode.OutcomeProvider outcomeProvider = new ClearNode.OutcomeProvider();

        List<String> statementCodes = new ArrayList<>();
        statementCodes.add("approved");
        statementCodes.add("denied");

        JsonValue nodeAttributes = json(object(
            field(USECONTINUEATTR, false),
            field(STATEMENTCODESATTR, statementCodes)));

        PreferredLocales locales = new PreferredLocales();
        List<OutcomeProvider.Outcome> outcomes = outcomeProvider.getOutcomes(locales, nodeAttributes);

        assertThat(outcomes.get(0).id).isEqualTo("permit");
        assertThat(outcomes.get(0).displayName).isEqualTo("Permit");

        assertThat(outcomes.get(1).id).isEqualTo("deny");
        assertThat(outcomes.get(1).displayName).isEqualTo("Deny");

        assertThat(outcomes.get(2).id).isEqualTo("indeterminate");
        assertThat(outcomes.get(2).displayName).isEqualTo("Indeterminate");

        assertThat(outcomes.get(3).id).isEqualTo("approved");
        assertThat(outcomes.get(3).displayName).isEqualTo("approved");

        assertThat(outcomes.get(4).id).isEqualTo("denied");
        assertThat(outcomes.get(4).displayName).isEqualTo("denied");

        assertThat(outcomes.get(5).id).isEqualTo("clientError");
        assertThat(outcomes.get(5).displayName).isEqualTo("Error");
    }

    private TreeContext getContext(JsonValue sharedState, JsonValue transientState,
                                   List<? extends Callback> callbacks) {
        return new TreeContext(sharedState, transientState, new ExternalRequestContext.Builder().build(), callbacks,
                               Optional.empty());
    }
}