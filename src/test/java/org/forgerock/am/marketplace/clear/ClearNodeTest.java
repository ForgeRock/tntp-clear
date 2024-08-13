///*
// * Copyright 2024 ForgeRock AS. All Rights Reserved
// *
// * Use of this code requires a commercial software license with ForgeRock AS.
// * or with one of its affiliates. All use shall be exclusively subject
// * to such license between the licensee and ForgeRock AS.
// */
//
//package org.forgerock.am.marketplace.clear;
//
//import static java.util.Collections.emptyList;
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.forgerock.json.JsonValue.field;
//import static org.forgerock.json.JsonValue.json;
//import static org.forgerock.json.JsonValue.object;
//import static org.forgerock.am.marketplace.clear.ClearNode.ClearOutcomeProvider.CLIENT_ERROR_OUTCOME_ID;
//import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.when;
//
//import javax.security.auth.callback.Callback;
//import java.util.List;
//import java.util.Optional;
//
//import org.forgerock.json.JsonValue;
//import org.forgerock.openam.auth.node.api.*;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.CsvSource;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.mockito.junit.jupiter.MockitoSettings;
//import org.mockito.quality.Strictness;
//
//@ExtendWith(MockitoExtension.class)
//@MockitoSettings(strictness = Strictness.LENIENT)
//public class ClearNodeTest {
//
//    @Mock
//    ClearNode.Config config;
//
//    @Mock
//    ClearClient client;
//
//    @Mock
//    ClearNode node;
//
//    @BeforeEach
//    public void setup() throws Exception {
//
//        node = new ClearNode(config, client);
//    }
//
//    @Test
//    public void testReturnOutcomeClear() throws Exception {
//        // Given
//        JsonValue sharedState = json(object(1));
//        sharedState.put(REALM, "/realm");
//
//        given(config.apiKey()).willReturn("some-api-key");
//        given(config.projectId()).willReturn("some-project-id");
//        given(config.redirectUrl()).willReturn("some-redirect-url");
//        given(config.secureEndpointToggle()).willReturn(true);
//
//        JsonValue response = json(object(1));
//
//        String sessionId = "some-session-id";
//        String sessionToken = "some-session-token";
//
//        response.put("id", sessionId);
//        response.put("token", sessionToken);
//
//        when(client.createVerificationSession(any(), any(), any(), any())).thenReturn(response);
//
//        // When
//        Action result = node.process(getContext(sharedState, json(object()), emptyList()));
//
//        // Then
//        assertThat(result.outcome).isEqualTo("continue");
//    }
//
//    @Test
//    public void testGetInputs() {
//        InputState[] inputs = node.getInputs();
//
//        assertThat(inputs[0].name).isEqualTo("id");
//        assertThat(inputs[0].required).isEqualTo(false);
//
//        assertThat(inputs[1].name).isEqualTo("token");
//        assertThat(inputs[1].required).isEqualTo(false);
//    }
//
//    @Test
//    public void testGetOutputs() {
//        OutputState[] outputs = node.getOutputs();
//        assertThat(outputs[0].name).isEqualTo("decision");
//    }
//
//    private TreeContext getContext(JsonValue sharedState, JsonValue transientState,
//                                   List<? extends Callback> callbacks) {
//        return new TreeContext(sharedState, transientState, new ExternalRequestContext.Builder().build(), callbacks,
//                               Optional.empty());
//    }
//}