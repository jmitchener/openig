/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */
package org.forgerock.openig.filter.oauth2.client;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.json.fluent.JsonValue.*;
import static org.forgerock.openig.el.Expression.*;
import static org.forgerock.openig.http.HttpClient.*;
import static org.forgerock.openig.io.TemporaryStorage.*;
import static org.forgerock.openig.log.LogSink.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpClient;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.ConsoleLogSink;
import org.forgerock.util.time.TimeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Unit tests for the OAuth2Provider class.
 */
@SuppressWarnings("javadoc")
public class OAuth2ProviderTest {

    private AtomicReference<Request> capturedRequest;
    private Exchange exchange;
    private HeapImpl heap;
    private OAuth2Session session;
    private static final String AUTHORIZE_ENDPOINT = "/openam/oauth2/authorize";
    private static final String TOKEN_ENDPOINT = "/openam/oauth2/access_token";
    private static final String USER_INFO_ENDPOINT = "/openam/oauth2/userinfo";
    private static final String SAMPLE_URI = "http://www.example.com:8089";

    @Mock
    private Handler providerHandler;

    @Mock
    private TimeService time;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        exchange = new Exchange(new URI("path"));
        session = OAuth2Session.stateNew(time);
        capturedRequest = new AtomicReference<Request>();
        heap = buildDefaultHeap();
    }

    @DataProvider
    private Object[][] errorResponseStatus() {
        return new Object[][] {
            { 400 },
            { 502 } };
    }

    @DataProvider
    private Object[][] missingRequiredAttributes() {
        return new Object[][] {
            /* Missing clientId. */
            { json(object(
                    field("clientSecret", "password"),
                    field("authorizeEndpoint", "http://www.example.com:8081/openam/oauth2/authorize"),
                    field("tokenEndpoint", "http://www.example.com:8081/openam/oauth2/access_token"),
                    field("userInfoEndpoint", "http://www.example.com:8081/openam/oauth2/userinfo"))) },
            /* Missing clientSecret. */
            { json(object(
                    field("clientId", "OpenIG"),
                    field("authorizeEndpoint", "http://www.example.com:8081/openam/oauth2/authorize"),
                    field("tokenEndpoint", "http://www.example.com:8081/openam/oauth2/access_token"),
                    field("userInfoEndpoint", "http://www.example.com:8081/openam/oauth2/userinfo"))) },
            /* Missing authorizeEndpoint. */
            { json(object(
                    field("clientId", "OpenIG"),
                    field("clientSecret", "password"),
                    field("tokenEndpoint", "http://www.example.com:8081/openam/oauth2/access_token"),
                    field("userInfoEndpoint", "http://www.example.com:8081/openam/oauth2/userinfo"))) } };
    }

    @Test(dataProvider = "missingRequiredAttributes", expectedExceptions = JsonValueException.class)
    public void shouldFailToCreateHeapObject(final JsonValue config) throws Exception {
        final OAuth2Provider.Heaplet heaplet = new OAuth2Provider.Heaplet();
        heaplet.create(Name.of("openam"), config, heap);
    }

    @Test
    public void shouldSucceedToCreateHeapObject() throws Exception {
        final JsonValue config = json(object(
                        field("clientId", "OpenIG"),
                        field("clientSecret", "password"),
                        field("scopes", array("openid")),
                        field("authorizeEndpoint", "http://www.example.com:8081/openam/oauth2/authorize"),
                        field("tokenEndpoint", "http://www.example.com:8081/openam/oauth2/access_token"),
                        field("userInfoEndpoint", "http://www.example.com:8081/openam/oauth2/userinfo")));

        final OAuth2Provider.Heaplet heaplet = new OAuth2Provider.Heaplet();
        final OAuth2Provider provider = (OAuth2Provider) heaplet.create(Name.of("openam"), config, heap);
        assertThat(provider.getClientId(null)).isEqualTo("OpenIG");
        assertThat(provider.getScopes(null).get(0)).isEqualTo("openid");
        assertThat(provider.getName()).isEqualTo("openam");
        assertThat(provider.hasUserInfoEndpoint()).isTrue();
    }

    @Test
    public void shouldGetRefreshToken() throws Exception {
        // given
        final OAuth2Provider provider = getOAuth2Provider();
        respondWith(200).when(providerHandler).handle(exchange);
        // when
        provider.getRefreshToken(exchange, session);
        // then
        verify(providerHandler).handle(exchange);
        assertThat(capturedRequest.get().getMethod()).isEqualTo("POST");
        assertThat(capturedRequest.get().getUri().toASCIIString()).isEqualTo(SAMPLE_URI + TOKEN_ENDPOINT);
        assertThat(capturedRequest.get().getEntity().getString()).contains("grant_type=refresh_token");
    }

    @Test
    public void shouldGetAccessToken() throws Exception {
        // given
        final String code = "sampleAuthorizationCodeForTestOnly";
        final String callbackUri = "shouldBeACallbackUri";
        final OAuth2Provider provider = getOAuth2Provider();
        respondWith(200).when(providerHandler).handle(exchange);
        // when
        provider.getAccessToken(exchange, code, callbackUri);
        // then
        verify(providerHandler).handle(exchange);
        assertThat(capturedRequest.get().getMethod()).isEqualTo("POST");
        assertThat(capturedRequest.get().getUri().toASCIIString()).isEqualTo(SAMPLE_URI + TOKEN_ENDPOINT);
        assertThat(capturedRequest.get().getEntity().getString()).contains("grant_type=authorization_code",
                "redirect_uri=" + callbackUri, "code=" + code);
    }

    @Test
    public void shouldGetUserInfo() throws Exception {
        // given
        final OAuth2Provider provider = getOAuth2Provider();
        respondWith(200).when(providerHandler).handle(exchange);
        // when
        provider.getUserInfo(exchange, session);
        // then
        verify(providerHandler).handle(exchange);
        assertThat(capturedRequest.get().getMethod()).isEqualTo("GET");
        assertThat(capturedRequest.get().getUri().toASCIIString()).isEqualTo(SAMPLE_URI + USER_INFO_ENDPOINT);
        assertThat(capturedRequest.get().getHeaders().get("Authorization")).isNotEmpty();
    }

    @Test(dataProvider = "errorResponseStatus", expectedExceptions = OAuth2ErrorException.class)
    public void shouldFailToGetRefreshTokenWhenReceiveErrorResponse(final int errorResponseStatus) throws Exception {

        respondWith(errorResponseStatus, setErrorEntity()).when(providerHandler).handle(exchange);

        getOAuth2Provider().getRefreshToken(exchange, session);
    }

    @Test(dataProvider = "errorResponseStatus", expectedExceptions = OAuth2ErrorException.class)
    public void shouldFailToGetAccessTokenWhenReceiveErrorResponse(final int errorResponseStatus) throws Exception {

        respondWith(errorResponseStatus, setErrorEntity()).when(providerHandler).handle(exchange);

        getOAuth2Provider().getAccessToken(exchange, "code", "callbackUri");
    }

    @Test(expectedExceptions = OAuth2ErrorException.class,
            expectedExceptionsMessageRegExp = "error=\"server_error\"")
    public void shouldFailToGetUserInfoWhenReceiveErrorResponse() throws Exception {

        respondWith(418, setErrorEntity()).when(providerHandler).handle(exchange);

        getOAuth2Provider().getUserInfo(exchange, session);
    }

    private Stubber respondWith(final int status) {
        return respondWith(status, null);
    }

    private Stubber respondWith(final int status,
                                final Object entity) {
        return doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) throws IOException {
                capturedRequest.set(exchange.request);
                exchange.response = new Response();
                exchange.response.setStatus(status);
                exchange.response.setEntity(entity);
                return null;
            }
        });
    }

    private OAuth2Provider getOAuth2Provider() throws Exception {
        final OAuth2Provider provider = new OAuth2Provider("openam");
        provider.setClientId(valueOf("OpenIG", String.class));
        provider.setClientSecret(valueOf("password", String.class));
        final List<Expression<String>> myScopes = new ArrayList<Expression<String>>();
        myScopes.add(valueOf("openid", String.class));
        myScopes.add(valueOf("profile", String.class));
        provider.setScopes(myScopes);
        provider.setAuthorizeEndpoint(valueOf(SAMPLE_URI + AUTHORIZE_ENDPOINT, String.class));
        provider.setTokenEndpoint(valueOf(SAMPLE_URI + TOKEN_ENDPOINT, String.class));
        provider.setUserInfoEndpoint(valueOf(SAMPLE_URI + USER_INFO_ENDPOINT, String.class));
        provider.setProviderHandler(providerHandler);
        return provider;
    }

    private HeapImpl buildDefaultHeap() throws GeneralSecurityException {
        final HeapImpl heap = new HeapImpl(Name.of("myHeap"));
        heap.put(TEMPORARY_STORAGE_HEAP_KEY, new TemporaryStorage());
        heap.put(LOGSINK_HEAP_KEY, new ConsoleLogSink());
        heap.put(HTTP_CLIENT_HEAP_KEY, new HttpClient(new TemporaryStorage()));
        return heap;
    }

    private JsonValue setErrorEntity() {
        return json(object(field("error", "Generated by tests")));
    }
}