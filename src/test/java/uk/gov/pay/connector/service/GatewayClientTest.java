package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.HttpCookie;
import java.net.SocketException;
import java.net.URI;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;

@ExtendWith(MockitoExtension.class)
public class GatewayClientTest {

    private static final URI WORLDPAY_API_ENDPOINT = URI.create("http://www.example.com/worldpay/order");
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
    private Histogram mockResponseTimeHistogram;
    @Mock
    private Counter mockFailureCounter;
    @Mock
    private GatewayOrder mockGatewayOrder;
    @Mock
    private WebTarget mockWebTarget;

    @BeforeEach
    public void setup() {
        gatewayClient = new GatewayClient(mockClient,
                mockMetricRegistry);

        when(mockClient.target(WORLDPAY_API_ENDPOINT)).thenReturn(mockWebTarget);
    }

    @Test
    public void shouldReturnGatewayErrorWhenProviderFails() {
        setupPostRequestMocks();
        when(mockResponse.getStatus()).thenReturn(500);
        when(mockMetricRegistry.counter("gateway-operations.worldpay.test.authorise.failures")).thenReturn(mockFailureCounter);
        
        assertThrows(GatewayException.GatewayErrorException.class,
                () -> gatewayClient.postRequestFor(WORLDPAY_API_ENDPOINT, WORLDPAY, "test", mockGatewayOrder, emptyMap()));
        verify(mockResponse).close();
        verify(mockFailureCounter).inc();
    }

    @Test
    public void shouldReturnGatewayErrorWhenProviderFailsWithAProcessingException() {
        setupPostRequestMocks();
        when(mockBuilder.post(Entity.entity(orderPayload, mediaType))).thenThrow(new ProcessingException(new SocketException("socket failed")));
        when(mockMetricRegistry.counter("gateway-operations.worldpay.test.authorise.failures")).thenReturn(mockFailureCounter);
        doAnswer(invocationOnMock -> null).when(mockFailureCounter).inc();
        
        assertThrows(GatewayException.GenericGatewayException.class,
                () -> gatewayClient.postRequestFor(WORLDPAY_API_ENDPOINT, WORLDPAY, "test", mockGatewayOrder, emptyMap()));
        verify(mockFailureCounter).inc();
    }

    @Test
    public void shouldIncludeCookieIfSessionIdentifierAvailableInOrder() throws Exception {
        setupPostRequestMocks();
        when(mockResponse.getStatus()).thenReturn(200);

        gatewayClient.postRequestFor(WORLDPAY_API_ENDPOINT, WORLDPAY, "test", mockGatewayOrder,
                ImmutableList.of(new HttpCookie("machine", "value")), emptyMap());

        InOrder inOrder = Mockito.inOrder(mockBuilder);
        inOrder.verify(mockBuilder).header("Cookie", "machine=value");
        inOrder.verify(mockBuilder).post(Entity.entity(orderPayload, mediaType));
        verify(mockResponseTimeHistogram).update(anyLong());
    }

    @Test
    public void getRequestShouldReturnGatewayErrorWhenProviderFails() {
        setupGetRequestMocks();
        when(mockResponse.getStatus()).thenReturn(500);
        doAnswer(invocationOnMock -> null).when(mockFailureCounter).inc();
        when(mockMetricRegistry.counter("gateway-operations.get.worldpay.test.query.failures")).thenReturn(mockFailureCounter);
        
        assertThrows(GatewayException.GatewayErrorException.class,
                () -> gatewayClient.getRequestFor(WORLDPAY_API_ENDPOINT, WORLDPAY, "test", OrderRequestType.QUERY, emptyList(), emptyMap()));
        verify(mockResponse).close();
        verify(mockFailureCounter).inc();
    }

    @Test
    public void getRequestShouldReturnGatewayErrorWhenProviderFailsWithAProcessingException() {
        setupGetRequestMocks();
        when(mockMetricRegistry.counter("gateway-operations.get.worldpay.test.query.failures")).thenReturn(mockFailureCounter);
        when(mockBuilder.get()).thenThrow(new ProcessingException(new SocketException("socket failed")));
        
        assertThrows(GatewayException.GenericGatewayException.class,
                () -> gatewayClient.getRequestFor(WORLDPAY_API_ENDPOINT, WORLDPAY, "test", OrderRequestType.QUERY, emptyList(), emptyMap()));
        verify(mockFailureCounter).inc();
    }

    @Test
    public void getRequestShouldIncludeCookieIfSessionIdentifierAvailableInOrder() throws Exception {
        setupGetRequestMocks();
        when(mockResponse.getStatus()).thenReturn(200);

        gatewayClient.getRequestFor(WORLDPAY_API_ENDPOINT, WORLDPAY, "test", OrderRequestType.QUERY,
                ImmutableList.of(new HttpCookie("machine", "value")), emptyMap());

        InOrder inOrder = Mockito.inOrder(mockBuilder);
        inOrder.verify(mockBuilder).header("Cookie", "machine=value");
        inOrder.verify(mockBuilder).get();
        verify(mockResponseTimeHistogram).update(anyLong());
    }

    private void setupPostRequestMocks() {
        when(mockMetricRegistry.histogram("gateway-operations.worldpay.test.authorise.response_time")).thenReturn(mockResponseTimeHistogram);

        when(mockWebTarget.request()).thenReturn(mockBuilder).thenReturn(mockBuilder);
        when(mockBuilder.post(Entity.entity(orderPayload, mediaType))).thenReturn(mockResponse);

        when(mockGatewayOrder.getOrderRequestType()).thenReturn(OrderRequestType.AUTHORISE);
        when(mockGatewayOrder.getPayload()).thenReturn(orderPayload);
        when(mockGatewayOrder.getMediaType()).thenReturn(mediaType);
    }

    private void setupGetRequestMocks() {
        when(mockMetricRegistry.histogram("gateway-operations.get.worldpay.test.query.response_time")).thenReturn(mockResponseTimeHistogram);

        when(mockWebTarget.request()).thenReturn(mockBuilder).thenReturn(mockBuilder);
        when(mockBuilder.get()).thenReturn(mockResponse);
    }
}
