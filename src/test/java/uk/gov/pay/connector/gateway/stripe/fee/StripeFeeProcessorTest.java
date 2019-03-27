package uk.gov.pay.connector.gateway.stripe.fee;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.request.RecoupFeeRequest;
import uk.gov.pay.connector.gateway.model.response.RecoupFeeResponse;
import uk.gov.pay.connector.gateway.stripe.DownstreamException;
import uk.gov.pay.connector.gateway.stripe.GatewayClientException;
import uk.gov.pay.connector.gateway.stripe.GatewayException;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClient;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClientResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

@RunWith(MockitoJUnitRunner.class)
public class StripeFeeProcessorTest {
    @Mock
    private ConnectorConfiguration connectorConfiguration;
    @Mock
    private GatewayClientFactory gatewayClientFactory;
    @Mock
    private StripeGatewayClient stripeGatewayClient;
    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    @Mock
    private StripeGatewayClientResponse response;
    
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());
    @Mock
    protected Environment environment;
    
    @Mock
    protected MetricRegistry metrics;
    
    private StripeFeeProcessor stripeFeeProcessor;
    
    private double feePercentage = 0.08;
    private URI feeUri;
    private ChargeEntity charge;
    
    @Before
    public void setUp() {
        when(stripeGatewayConfig.getFeePercentage()).thenReturn(feePercentage);
        when(stripeGatewayConfig.getUrl()).thenReturn("stripeUrl");
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(mock(StripeAuthTokens.class));
        when(connectorConfiguration.getStripeConfig()).thenReturn(stripeGatewayConfig);
        
        when(environment.metrics()).thenReturn(metrics);
        
        when(gatewayClientFactory.createStripeGatewayClient(
                eq(PaymentGatewayName.STRIPE),
                eq(GatewayOperation.RECOUP_FEE),
                eq(metrics))).thenReturn(stripeGatewayClient);
        
        stripeFeeProcessor = new StripeFeeProcessor(gatewayClientFactory, connectorConfiguration, objectMapper, environment);
        feeUri = URI.create(stripeGatewayConfig.getUrl() + "/v1/transfers");
        GatewayAccountEntity gatewayAccount = buildGatewayAccountEntity();

        charge = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withAmount(10000L)
                .build();

    }
    
    @Test
    public void calculateFee_PercentageOfChargeAmountIsWhole() throws Exception {
        assertThat(stripeFeeProcessor.calculateFee(10000L, 10L), is(18L));
    }

    @Test
    public void calculateFee_PercentageOfChargeAmountIsNotWhole() throws Exception {
        assertThat(stripeFeeProcessor.calculateFee(10001L, 10L), is(19L));
    }

    @Test
    public void calculateFee_PercentageOfChargeAmountIsLessThan1() throws Exception {
        assertThat(stripeFeeProcessor.calculateFee(1000L, 10L), is(11L));
    }

    @Test
    public void calculateFee_PercentageOfChargeAmountIs0() throws Exception {
        assertThat(stripeFeeProcessor.calculateFee(0L, 10L), is(10L));
    }
    
    @Test
    public void recoupFee_responseShouldRecordAmountCollected() throws  Exception{
        long amountCollected = 50L;
        final String jsonResponse = new Gson().toJson(ImmutableMap.of("amount", amountCollected));
        when(stripeGatewayClient.postRequest(eq(feeUri), anyString(), any(Map.class), any(MediaType.class), anyString())).thenReturn(jsonResponse);
        
        RecoupFeeResponse response = stripeFeeProcessor.recoupFee(RecoupFeeRequest.of(charge, new FeeEntity(charge, amountCollected)));
        
        assertThat(response.getAmountCollected(), is(amountCollected));
        assertTrue(response.isSuccessful());
    }

    @Test
    public void recoupFee_responseShouldNotBeSuccessfulAndShouldRecordErrorIfGatewayExeption() throws  Exception{
        long amountCollected = 50L;
        when(stripeGatewayClient.postRequest(
                eq(feeUri),
                anyString(),
                any(Map.class),
                any(MediaType.class),
                anyString())).thenThrow(new GatewayException("url", new Exception("message")));

        RecoupFeeResponse response = stripeFeeProcessor.recoupFee(RecoupFeeRequest.of(charge, new FeeEntity(charge, amountCollected)));

        assertFalse(response.isSuccessful());
        assertThat(response.getErrorMessage(), is("java.lang.Exception: message"));
    }

    @Test
    public void recoupFee_responseShouldNotBeSuccessfulAndShouldRecordErrorIfConnectionExeption() throws  Exception{
        long amountCollected = 50L;
        when(stripeGatewayClient.postRequest(
                eq(feeUri),
                anyString(),
                any(Map.class),
                any(MediaType.class),
                anyString())).thenThrow(new DownstreamException(200, "message"));

        RecoupFeeResponse response = stripeFeeProcessor.recoupFee(RecoupFeeRequest.of(charge, new FeeEntity(charge, amountCollected)));

        assertFalse(response.isSuccessful());
        assertThat(response.getErrorMessage(), is("message"));
    }

    @Test
    public void recoupFee_responseShouldNotBeSuccessfulAndShouldRecordErrorIfGatewayClientExeption() throws  Exception{
        long amountCollected = 50L;
        when(stripeGatewayClient.postRequest(
                eq(feeUri),
                anyString(),
                any(Map.class),
                any(MediaType.class),
                anyString())).thenThrow(new GatewayClientException("message", response));


        RecoupFeeResponse response = stripeFeeProcessor.recoupFee(RecoupFeeRequest.of(charge, new FeeEntity(charge, amountCollected)));

        assertFalse(response.isSuccessful());
        assertThat(response.getErrorMessage(), is("message"));
    }


    private GatewayAccountEntity buildGatewayAccountEntity() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("stripe");
        gatewayAccount.setRequires3ds(false);
        gatewayAccount.setCredentials(ImmutableMap.of("stripe_account_id", "stripe_account_id"));
        gatewayAccount.setType(TEST);
        return gatewayAccount;
    }
}
