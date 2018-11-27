package uk.gov.pay.connector.gateway.worldpay;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.mockito.Mock;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;

public abstract class WorldpayBasePaymentProviderTest {

    protected WorldpayPaymentProvider provider;
    private Map<String, String> urlMap = ImmutableMap.of(TEST.toString(), "http://worldpay.url");

    @Mock
    private Client mockClient;
    @Mock
    protected GatewayClient mockGatewayClient;
    @Mock
    protected GatewayAccountEntity mockGatewayAccountEntity;
    @Mock
    private MetricRegistry mockMetricRegistry;
    @Mock
    private Histogram mockHistogram;
    @Mock
    private Counter mockCounter;
    @Mock
    private ClientFactory mockClientFactory;
    @Mock
    protected ConnectorConfiguration configuration;
    @Mock
    private GatewayConfig gatewayConfig;
    @Mock
    protected Environment environment;

    @Before
    public void setup() {
        GatewayClientFactory gatewayClientFactory = new GatewayClientFactory(mockClientFactory);

        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockClientFactory.createWithDropwizardClient(
                eq(PaymentGatewayName.WORLDPAY), any(GatewayOperation.class), any(MetricRegistry.class))
        )
                .thenReturn(mockClient);

        when(configuration.getGatewayConfigFor(PaymentGatewayName.WORLDPAY)).thenReturn(gatewayConfig);
        when(gatewayConfig.getUrls()).thenReturn(urlMap);
        when(environment.metrics()).thenReturn(mockMetricRegistry);

        provider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);
    }

    protected GatewayAccountEntity aServiceAccount() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("worldpay");
        gatewayAccount.setRequires3ds(false);
        gatewayAccount.setCredentials(ImmutableMap.of(
                CREDENTIALS_MERCHANT_ID, "worlpay-merchant",
                CREDENTIALS_USERNAME, "worldpay-password",
                CREDENTIALS_PASSWORD, "password"
        ));
        gatewayAccount.setType(TEST);
        return gatewayAccount;
    }

    protected void assertEquals(GatewayError actual, GatewayError expected) {
        assertNotNull(actual);
        assertThat(actual.getMessage(), is(expected.getMessage()));
        assertThat(actual.getErrorType(), is(expected.getErrorType()));
    }

    protected void mockWorldpayErrorResponse(int httpStatus) {
        mockWorldpayResponse(httpStatus, errorResponse());
    }

    protected void mockWorldpayResponse(int httpStatus, String responsePayload) {
        WebTarget mockTarget = mock(WebTarget.class);
        when(mockClient.target(anyString())).thenReturn(mockTarget);
        Invocation.Builder mockBuilder = mock(Invocation.Builder.class);
        when(mockTarget.request()).thenReturn(mockBuilder);
        when(mockBuilder.header(anyString(), any(Object.class))).thenReturn(mockBuilder);

        Map<String, NewCookie> responseCookies =
                Collections.singletonMap(WORLDPAY_MACHINE_COOKIE_NAME, NewCookie.valueOf("value-from-worldpay"));

        Response response = mock(Response.class);
        when(response.readEntity(String.class)).thenReturn(responsePayload);
        when(mockBuilder.post(any(Entity.class))).thenReturn(response);
        when(response.getCookies()).thenReturn(responseCookies);

        when(response.getStatus()).thenReturn(httpStatus);
    }

    protected String errorResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                "    <reply>\n" +
                "        <error code=\"5\">\n" +
                "            <![CDATA[Order has already been paid]]>\n" +
                "        </error>\n" +
                "    </reply>\n" +
                "</paymentService>";
    }

}
