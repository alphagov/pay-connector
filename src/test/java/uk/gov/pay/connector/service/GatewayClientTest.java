package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

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
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

@RunWith(MockitoJUnitRunner.class)
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
    private Histogram mockHistogram;
    @Mock
    private Counter mockCounter;

    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;

    @Mock
    private GatewayOrder mockGatewayOrder;

    @Before
    public void setup() {
        gatewayClient = new GatewayClient(mockClient,
                mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        doAnswer(invocationOnMock -> null).when(mockCounter).inc();

        WebTarget mockWebTarget = mock(WebTarget.class);

        when(mockClient.target(WORLDPAY_API_ENDPOINT)).thenReturn(mockWebTarget);
        when(mockWebTarget.request()).thenReturn(mockBuilder).thenReturn(mockBuilder);
        when(mockBuilder.post(Entity.entity(orderPayload, mediaType))).thenReturn(mockResponse);

        when(mockGatewayAccountEntity.getGatewayName()).thenReturn("worldpay");
        when(mockGatewayAccountEntity.getType()).thenReturn("worldpay");

        when(mockGatewayOrder.getOrderRequestType()).thenReturn(OrderRequestType.AUTHORISE);
        when(mockGatewayOrder.getPayload()).thenReturn(orderPayload);
        when(mockGatewayOrder.getMediaType()).thenReturn(mediaType);
    }

    @Test(expected = GatewayErrorException.GatewayConnectionErrorException.class)
    public void shouldReturnGatewayErrorWhenProviderFails() throws Exception {
        when(mockResponse.getStatus()).thenReturn(500);
        gatewayClient.postRequestFor(WORLDPAY_API_ENDPOINT, mockGatewayAccountEntity, mockGatewayOrder, emptyMap());
        verify(mockResponse).close();
    }

    @Test(expected = GatewayErrorException.GenericGatewayErrorException.class)
    public void shouldReturnGatewayErrorWhenProviderFailsWithAProcessingException() throws Exception {
        when(mockBuilder.post(Entity.entity(orderPayload, mediaType))).thenThrow(new ProcessingException(new SocketException("socket failed")));
        gatewayClient.postRequestFor(WORLDPAY_API_ENDPOINT, mockGatewayAccountEntity, mockGatewayOrder, emptyMap());
    }

    @Test
    public void shouldIncludeCookieIfSessionIdentifierAvailableInOrder() throws Exception {
        when(mockResponse.getStatus()).thenReturn(200);

        gatewayClient.postRequestFor(WORLDPAY_API_ENDPOINT, mockGatewayAccountEntity, mockGatewayOrder, 
                ImmutableList.of(new HttpCookie("machine", "value")), emptyMap());

        InOrder inOrder = Mockito.inOrder(mockBuilder);
        inOrder.verify(mockBuilder).cookie("machine", "value");
        inOrder.verify(mockBuilder).post(Entity.entity(orderPayload, mediaType));
    }
}
