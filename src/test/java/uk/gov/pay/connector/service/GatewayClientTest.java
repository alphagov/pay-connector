package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import fj.data.Either;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.util.AuthUtil.encode;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

@RunWith(MockitoJUnitRunner.class)
public class GatewayClientTest {

    private static final String WORLDPAY_API_ENDPOINT = "http://www.example.com/worldpay/order";
    private GatewayClient gatewayClient;

    private String orderPayload = "a-sample-payload";

    private MediaType mediaType = MediaType.APPLICATION_XML_TYPE;

    @Mock
    private Client mockClient;
    @Mock
    private Response mockResponse;
    @Mock
    private Builder mockBuilder;

    @Mock
    private MetricRegistry mockMetricRegistry;
    @Mock
    private Histogram mockHistogram;
    @Mock
    private Counter mockCounter;

    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;

    @Mock
    private GatewayOrder mockGatewayOrder;

    @Mock
    BiFunction<GatewayOrder, Builder, Builder> mockSessionIdentifier;

    @Before
    public void setup() {
        Map<String, String> urlMap = Collections.singletonMap("worldpay", WORLDPAY_API_ENDPOINT);
        Map<String, String> credentialMap = new HashMap<>();
        credentialMap.put(CREDENTIALS_USERNAME, "user");
        credentialMap.put(CREDENTIALS_PASSWORD, "password");

        gatewayClient = new GatewayClient(mockClient, urlMap,
            mockSessionIdentifier, mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        doAnswer(invocationOnMock -> null).when(mockCounter).inc();

        WebTarget mockWebTarget = mock(WebTarget.class);

        when(mockClient.target(WORLDPAY_API_ENDPOINT)).thenReturn(mockWebTarget);
        when(mockWebTarget.request()).thenReturn(mockBuilder).thenReturn(mockBuilder);
        when(mockBuilder.header(AUTHORIZATION, encode("user", "password"))).thenReturn(mockBuilder);
        when(mockSessionIdentifier.apply(mockGatewayOrder, mockBuilder)).thenReturn(mockBuilder);
        when(mockBuilder.post(Entity.entity(orderPayload, mediaType))).thenReturn(mockResponse);

        when(mockGatewayAccountEntity.getGatewayName()).thenReturn("worldpay");
        when(mockGatewayAccountEntity.getType()).thenReturn("worldpay");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialMap);

        when(mockGatewayOrder.getOrderRequestType()).thenReturn(OrderRequestType.AUTHORISE);
        when(mockGatewayOrder.getPayload()).thenReturn(orderPayload);
        when(mockGatewayOrder.getMediaType()).thenReturn(mediaType);
    }

    @Test
    public void shouldReturnAGatewayResponseWhenProviderReturnsOk() {
        when(mockResponse.getStatus()).thenReturn(200);

        Either<GatewayError, GatewayClient.Response> gatewayResponse = gatewayClient.postRequestFor(null, mockGatewayAccountEntity, mockGatewayOrder);

        assertTrue(gatewayResponse.isRight());
        assertFalse(gatewayResponse.isLeft());
        verify(mockResponse).close();
    }

    @Test
    public void shouldReturnGatewayErrorWhenProviderFails() {
        when(mockResponse.getStatus()).thenReturn(500);

        Either<GatewayError, GatewayClient.Response> gatewayResponse = gatewayClient.postRequestFor(null, mockGatewayAccountEntity, mockGatewayOrder);

        assertTrue(gatewayResponse.isLeft());
        assertFalse(gatewayResponse.isRight());
        verify(mockResponse).close();
    }

    @Test
    public void shouldReturnGatewayErrorWhenProviderFailsWithAProcessingException() {
        when(mockBuilder.post(Entity.entity(orderPayload, mediaType))).thenThrow(new ProcessingException(new SocketException("socket failed")));

        Either<GatewayError, GatewayClient.Response> gatewayResponse = gatewayClient.postRequestFor(null, mockGatewayAccountEntity, mockGatewayOrder);

        assertTrue(gatewayResponse.isLeft());
        assertFalse(gatewayResponse.isRight());
    }

    @Test
    public void shouldIncludeCookieIfSessionIdentifierAvailableInOrder() {
        when(mockResponse.getStatus()).thenReturn(200);

        gatewayClient.postRequestFor(null, mockGatewayAccountEntity, mockGatewayOrder);

        InOrder inOrder = Mockito.inOrder(mockSessionIdentifier, mockBuilder);
        inOrder.verify(mockSessionIdentifier).apply(mockGatewayOrder, mockBuilder);
        inOrder.verify(mockBuilder).post(Entity.entity(orderPayload, mediaType));
    }
}
