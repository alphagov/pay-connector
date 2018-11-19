package uk.gov.pay.connector.gateway.stripe;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.mockito.Mock;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.stripe.response.StripeErrorResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_3DS_SOURCES_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_SOURCES_3DS_REQUIRED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_TOKEN_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;

public abstract class BaseStripePaymentProviderTest {

    protected GatewayClientFactory gatewayClientFactory;
    protected StripePaymentProvider provider;

    @Mock
    private Client mockClient;
    @Mock
    MetricRegistry mockMetricRegistry;
    @Mock
    Histogram mockHistogram;
    @Mock
    Counter mockCounter;
    @Mock
    ClientFactory mockClientFactory;
    @Mock
    ConnectorConfiguration configuration;
    @Mock
    StripeGatewayConfig stripeGatewayConfig;
    @Mock
    LinksConfig linksConfig;

    @Mock
    Invocation.Builder mockClientInvocationBuilder;

    @Before
    public void setup() {
        gatewayClientFactory = new GatewayClientFactory(mockClientFactory);

        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        when(stripeGatewayConfig.getUrl()).thenReturn("http://stripe.url");
        when(configuration.getStripeConfig()).thenReturn(stripeGatewayConfig);

        when(configuration.getLinks()).thenReturn(linksConfig);
        when(linksConfig.getFrontendUrl()).thenReturn("http://frontendUrl");

        StripeGatewayClient stripeGatewayClient = new StripeGatewayClient(mockClient, mockMetricRegistry);
        provider = new StripePaymentProvider(stripeGatewayClient, configuration);
    }

    String successCaptureResponse() {
        return TestTemplateResourceLoader.load(STRIPE_CAPTURE_SUCCESS_RESPONSE);
    }

    String successTokenResponse() {
        return TestTemplateResourceLoader.load(STRIPE_CREATE_TOKEN_SUCCESS_RESPONSE);
    }

    String successSourceResponseWith3dsRequired() {
        return TestTemplateResourceLoader.load(STRIPE_CREATE_SOURCES_3DS_REQUIRED_RESPONSE);
    }

    String success3dsSourceResponse() {
        return TestTemplateResourceLoader.load(STRIPE_CREATE_3DS_SOURCES_RESPONSE);
    }

    String errorCaptureResponse() {
        return TestTemplateResourceLoader.load(STRIPE_ERROR_RESPONSE);
    }

    CaptureGatewayRequest buildTestCaptureRequest() {
        return buildTestCaptureRequest(buildTestGatewayAccountEntity());
    }

    AuthorisationGatewayRequest buildTestAuthorisationRequest() {
        return buildTestAuthorisationRequest(buildTestGatewayAccountEntity());
    }

    void assertEquals(GatewayError actual, GatewayError expected) {
        assertNotNull(actual);
        assertThat(actual.getMessage(), is(expected.getMessage()));
        assertThat(actual.getErrorType(), is(expected.getErrorType()));
    }

    void mockPaymentProviderCaptureResponse(int responseHttpStatus, String responsePayload) throws IOException {
        Response response = mockResponseWithPayload(responseHttpStatus);
        Map<String, Object> responsePayloadMap = new ObjectMapper().readValue(responsePayload, HashMap.class);
        when(response.readEntity(Map.class)).thenReturn(responsePayloadMap);
    }

    @NotNull
    protected Response mockResponseWithPayload(int responseHttpStatus) {
        Response response = mockInvocationBuilder();

        Response.StatusType statusType = mock(Response.StatusType.class);
        when(response.getStatusInfo()).thenReturn(statusType);

        when(response.getStatus()).thenReturn(responseHttpStatus);
        when(statusType.getFamily()).thenReturn(Response.Status.Family.familyOf(responseHttpStatus));
        return response;
    }

    protected Response mockInvocationBuilder() {
        when(mockClientInvocationBuilder.header(anyString(), any(Object.class))).thenReturn(mockClientInvocationBuilder);

        WebTarget mockTarget = mock(WebTarget.class);
        when(mockTarget.request()).thenReturn(mockClientInvocationBuilder);
        when(mockClient.target(anyString())).thenReturn(mockTarget);

        Response response = mock(Response.class);
        when(mockClientInvocationBuilder.post(any())).thenReturn(response);
        return response;
    }

    void mockPaymentProviderErrorResponse(int responseHttpStatus, String responsePayload) throws IOException {
        Response response = mockResponseWithPayload(responseHttpStatus);
        StripeErrorResponse stripeErrorResponse = new ObjectMapper().readValue(responsePayload, StripeErrorResponse.class);
        when(response.readEntity(StripeErrorResponse.class)).thenReturn(stripeErrorResponse);
    }

    private AuthorisationGatewayRequest buildTestAuthorisationRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withGatewayAccountEntity(accountEntity)
                .build();
        return buildTestAuthorisationRequest(chargeEntity);
    }

    AuthorisationGatewayRequest buildTestAuthorisationRequest(ChargeEntity chargeEntity) {
        return new AuthorisationGatewayRequest(chargeEntity, buildTestAuthCardDetails());
    }

    private AuthCardDetails buildTestAuthCardDetails() {
        Address address = new Address("10", "Wxx", "E1 8xx", "London", null, "GB");
        return AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("Mr. Payment")
                .withCardNo("4242424242424242")
                .withCvc("111")
                .withEndDate("08/99")
                .withCardBrand("visa")
                .withAddress(address)
                .build();
    }

    private CaptureGatewayRequest buildTestCaptureRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(accountEntity)
                .withTransactionId("ch_1231231123123")
                .build();
        return buildTestCaptureRequest(chargeEntity);
    }

    private GatewayAccountEntity buildTestGatewayAccountEntity() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("stripe");
        gatewayAccount.setRequires3ds(false);
        gatewayAccount.setCredentials(ImmutableMap.of("stripe_account_id", "stripe_account_id"));
        gatewayAccount.setType(TEST);
        return gatewayAccount;
    }

    private CaptureGatewayRequest buildTestCaptureRequest(ChargeEntity chargeEntity) {
        return CaptureGatewayRequest.valueOf(chargeEntity);
    }

}
