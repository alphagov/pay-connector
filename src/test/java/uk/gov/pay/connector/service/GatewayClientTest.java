package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import fj.data.Either;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.OrderRequestType;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.util.AuthUtil.encode;

@RunWith(MockitoJUnitRunner.class)
public class GatewayClientTest {

    public static final String WORLDPAY_API_ENDPOINT = "http://www.example.com/worldpay/order";
    GatewayClient gatewayClient;

    @Mock
    Client mockClient;

    @Mock
    MetricRegistry mockMetricRegistry;

    @Mock
    Histogram mockHistogram;

    @Mock
    Counter mockCounter;

    @Before
    public void setup() {
        Map<String, String> urlMap = Collections.singletonMap("worldpay", WORLDPAY_API_ENDPOINT);
        gatewayClient = GatewayClient.createGatewayClient(mockClient, urlMap, MediaType.APPLICATION_XML_TYPE,
                WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME, mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        doAnswer(invocationOnMock -> null).when(mockHistogram).update(anyInt());
        doAnswer(invocationOnMock -> null).when(mockCounter).inc();
    }

    @Test
    public void shouldReturnAGatewayResponseWhenProviderReturnsOk() {
        String orderPayload = "a-sample-payload";
        Map<String, String> credentialMap = new HashMap<>();
        credentialMap.put(CREDENTIALS_USERNAME, "user");
        credentialMap.put(CREDENTIALS_PASSWORD, "password");

        WebTarget mockWebTarget = mock(WebTarget.class);
        Builder mockBuilder = mock(Builder.class);
        Response mockResponse = mock(Response.class);
        GatewayAccountEntity mockGatewayAccountEntity = mock(GatewayAccountEntity.class);

        GatewayOrder mockGatewayOrder = mock(GatewayOrder.class);


        when(mockGatewayAccountEntity.getGatewayName()).thenReturn("worldpay");
        when(mockGatewayAccountEntity.getType()).thenReturn("worldpay");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialMap);
        when(mockGatewayOrder.getOrderRequestType()).thenReturn(OrderRequestType.AUTHORISE);
        when(mockGatewayOrder.getPayload()).thenReturn(orderPayload);
        when(mockGatewayOrder.getProviderSessionId()).thenReturn(Optional.empty());

        when(mockClient.target(WORLDPAY_API_ENDPOINT)).thenReturn(mockWebTarget);
        when(mockWebTarget.request()).thenReturn(mockBuilder).thenReturn(mockBuilder);
        when(mockBuilder.header(AUTHORIZATION, encode("user", "password"))).thenReturn(mockBuilder);
        when(mockBuilder.cookie(any(Cookie.class))).thenReturn(mockBuilder);
        when(mockBuilder.post(Entity.entity(orderPayload, MediaType.APPLICATION_XML_TYPE))).thenReturn(mockResponse);
        when(mockResponse.getStatus()).thenReturn(200);

        Either<GatewayError, GatewayClient.Response> gatewayResponse = gatewayClient.postRequestFor(null, mockGatewayAccountEntity, mockGatewayOrder);

        assertTrue(gatewayResponse.isRight());
        assertFalse(gatewayResponse.isLeft());
        verify(mockResponse).close();
    }

    @Test
    public void shouldReturnGatewayErrorWhenProviderFails() {
        String orderPayload = "a-sample-payload";
        Map<String, String> credentialMap = new HashMap<>();
        credentialMap.put(CREDENTIALS_USERNAME, "user");
        credentialMap.put(CREDENTIALS_PASSWORD, "password");

        WebTarget mockWebTarget = mock(WebTarget.class);
        Builder mockBuilder = mock(Builder.class);
        Response mockResponse = mock(Response.class);
        GatewayAccountEntity mockGatewayAccountEntity = mock(GatewayAccountEntity.class);

        GatewayOrder mockGatewayOrder = mock(GatewayOrder.class);


        when(mockGatewayAccountEntity.getGatewayName()).thenReturn("worldpay");
        when(mockGatewayAccountEntity.getType()).thenReturn("worldpay");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialMap);
        when(mockGatewayOrder.getOrderRequestType()).thenReturn(OrderRequestType.AUTHORISE);
        when(mockGatewayOrder.getPayload()).thenReturn(orderPayload);
        when(mockGatewayOrder.getProviderSessionId()).thenReturn(Optional.empty());

        when(mockClient.target(WORLDPAY_API_ENDPOINT)).thenReturn(mockWebTarget);
        when(mockWebTarget.request()).thenReturn(mockBuilder).thenReturn(mockBuilder);
        when(mockBuilder.header(AUTHORIZATION, encode("user", "password"))).thenReturn(mockBuilder);
        when(mockBuilder.cookie(any(Cookie.class))).thenReturn(mockBuilder);
        when(mockBuilder.post(Entity.entity(orderPayload, MediaType.APPLICATION_XML_TYPE))).thenReturn(mockResponse);
        when(mockResponse.getStatus()).thenReturn(500);

        Either<GatewayError, GatewayClient.Response> gatewayResponse = gatewayClient.postRequestFor(null, mockGatewayAccountEntity, mockGatewayOrder);

        assertTrue(gatewayResponse.isLeft());
        assertFalse(gatewayResponse.isRight());
        verify(mockResponse).close();
    }

    @Test
    public void shouldReturnGatewayErrorWhenProviderFailsWithAProcessingException() {
        String orderPayload = "a-sample-payload";
        Map<String, String> credentialMap = new HashMap<>();
        credentialMap.put(CREDENTIALS_USERNAME, "user");
        credentialMap.put(CREDENTIALS_PASSWORD, "password");

        WebTarget mockWebTarget = mock(WebTarget.class);
        Builder mockBuilder = mock(Builder.class);
        GatewayAccountEntity mockGatewayAccountEntity = mock(GatewayAccountEntity.class);

        GatewayOrder mockGatewayOrder = mock(GatewayOrder.class);


        when(mockGatewayAccountEntity.getGatewayName()).thenReturn("worldpay");
        when(mockGatewayAccountEntity.getType()).thenReturn("worldpay");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialMap);
        when(mockGatewayOrder.getOrderRequestType()).thenReturn(OrderRequestType.AUTHORISE);
        when(mockGatewayOrder.getPayload()).thenReturn(orderPayload);

        when(mockClient.target(WORLDPAY_API_ENDPOINT)).thenReturn(mockWebTarget);
        when(mockWebTarget.request()).thenReturn(mockBuilder).thenReturn(mockBuilder);
        when(mockBuilder.header(AUTHORIZATION, encode("user", "password"))).thenReturn(mockBuilder);
        when(mockBuilder.post(Entity.entity(orderPayload, MediaType.APPLICATION_XML_TYPE))).thenThrow(new ProcessingException(new SocketException("socket failed")));

        Either<GatewayError, GatewayClient.Response> gatewayResponse = gatewayClient.postRequestFor(null, mockGatewayAccountEntity, mockGatewayOrder);

        assertTrue(gatewayResponse.isLeft());
        assertFalse(gatewayResponse.isRight());
    }

    @Test
    public void shouldIncludeCookieIfSessionIdentifierAvailableInOrder() {
        String orderPayload = "a-sample-payload";
        String providerSessionid = "provider-session-id";

        Map<String, String> credentialMap = new HashMap<>();
        credentialMap.put(CREDENTIALS_USERNAME, "user");
        credentialMap.put(CREDENTIALS_PASSWORD, "password");

        WebTarget mockWebTarget = mock(WebTarget.class);
        Builder mockBuilder = mock(Builder.class);
        Response mockResponse = mock(Response.class);
        GatewayAccountEntity mockGatewayAccountEntity = mock(GatewayAccountEntity.class);

        GatewayOrder mockGatewayOrder = mock(GatewayOrder.class);


        when(mockGatewayAccountEntity.getGatewayName()).thenReturn("worldpay");
        when(mockGatewayAccountEntity.getType()).thenReturn("worldpay");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialMap);

        when(mockGatewayOrder.getOrderRequestType()).thenReturn(OrderRequestType.AUTHORISE);
        when(mockGatewayOrder.getPayload()).thenReturn(orderPayload);
        when(mockGatewayOrder.getProviderSessionId()).thenReturn(Optional.of(providerSessionid));

        when(mockClient.target(WORLDPAY_API_ENDPOINT)).thenReturn(mockWebTarget);
        when(mockWebTarget.request()).thenReturn(mockBuilder).thenReturn(mockBuilder);
        when(mockBuilder.header(AUTHORIZATION, encode("user", "password"))).thenReturn(mockBuilder);
        when(mockBuilder.cookie(any(Cookie.class))).thenReturn(mockBuilder);
        when(mockBuilder.post(Entity.entity(orderPayload, MediaType.APPLICATION_XML_TYPE))).thenReturn(mockResponse);
        when(mockResponse.getStatus()).thenReturn(200);

        gatewayClient.postRequestFor(null, mockGatewayAccountEntity, mockGatewayOrder);

        verify(mockBuilder).cookie(WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME, providerSessionid);
    }

}
