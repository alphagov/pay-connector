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
import uk.gov.pay.connector.gateway.GatewayException;
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

import static java.util.Collections.emptyMap;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;

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
    private Histogram mockResponseTimeHistogram;
    @Mock
    private Counter mockFailureCounter;

    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;

    @Mock
    private GatewayOrder mockGatewayOrder;

    @Before
    public void setup() {
        gatewayClient = new GatewayClient(mockClient,
                mockMetricRegistry);
        when(mockMetricRegistry.histogram("gateway-operations.worldpay.test.authorise.response_time")).thenReturn(mockResponseTimeHistogram);
        when(mockMetricRegistry.counter("gateway-operations.worldpay.test.authorise.failures")).thenReturn(mockFailureCounter);
        doAnswer(invocationOnMock -> null).when(mockFailureCounter).inc();

        WebTarget mockWebTarget = mock(WebTarget.class);

        when(mockClient.target(WORLDPAY_API_ENDPOINT)).thenReturn(mockWebTarget);
        when(mockWebTarget.request()).thenReturn(mockBuilder).thenReturn(mockBuilder);
        when(mockBuilder.post(Entity.entity(orderPayload, mediaType))).thenReturn(mockResponse);

        when(mockGatewayAccountEntity.getGatewayName()).thenReturn("worldpay");
        when(mockGatewayAccountEntity.getType()).thenReturn("test");

        when(mockGatewayOrder.getOrderRequestType()).thenReturn(OrderRequestType.AUTHORISE);
        when(mockGatewayOrder.getPayload()).thenReturn(orderPayload);
        when(mockGatewayOrder.getMediaType()).thenReturn(mediaType);
    }

    @Test(expected = GatewayException.GatewayErrorException.class)
    public void shouldReturnGatewayErrorWhenProviderFails() throws Exception {
        when(mockResponse.getStatus()).thenReturn(500);
        gatewayClient.postRequestFor(WORLDPAY_API_ENDPOINT, WORLDPAY, "test", mockGatewayOrder, emptyMap());
        verify(mockResponse).close();
        verify(mockFailureCounter).inc();
    }

    @Test(expected = GatewayException.GenericGatewayException.class)
    public void shouldReturnGatewayErrorWhenProviderFailsWithAProcessingException() throws Exception {
        when(mockBuilder.post(Entity.entity(orderPayload, mediaType))).thenThrow(new ProcessingException(new SocketException("socket failed")));
        gatewayClient.postRequestFor(WORLDPAY_API_ENDPOINT, WORLDPAY, "test", mockGatewayOrder, emptyMap());
        verify(mockFailureCounter).inc();
    }

    @Test
    public void shouldIncludeCookieIfSessionIdentifierAvailableInOrder() throws Exception {
        when(mockResponse.getStatus()).thenReturn(200);

        gatewayClient.postRequestFor(WORLDPAY_API_ENDPOINT, mockGatewayAccountEntity, mockGatewayOrder, 
                ImmutableList.of(new HttpCookie("machine", "value")), emptyMap());

        InOrder inOrder = Mockito.inOrder(mockBuilder);
        inOrder.verify(mockBuilder).header("Cookie", "machine=value");
        inOrder.verify(mockBuilder).post(Entity.entity(orderPayload, mediaType));
        verify(mockResponseTimeHistogram).update(anyLong());
    }
}
