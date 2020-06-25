package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.RandomStringUtils;
import org.hamcrest.core.Is;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForCancelOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForCaptureOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForQueryOrder;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.TestClientFactory;

import javax.ws.rs.client.Client;
import java.io.IOException;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userEmail;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

@Ignore("Ignoring as this test is failing in Jenkins because it's failing to locate the certificates - PP-1707")
@RunWith(MockitoJUnitRunner.class)
public class EpdqPaymentProviderTest {

    private static final String VISA_CARD_NUMBER_RECOGNISED_AS_REQUIRING_3DS1_BY_EPDQ = "4000000000000002";
    private static final String VISA_CARD_NUMBER_RECOGNISED_AS_REQUIRING_3DS2_BY_EPDQ = "4874970686672022";
    private static final String ADDRESS_LINE_1 = "The Money Pool";
    private static final String ADDRESS_LINE_2 = "1 Gold Way";
    private static final String ADDRESS_CITY = "London";
    private static final String ADDRESS_POSTCODE = "DO11 4RS";
    private static final String ADDRESS_COUNTRY = "GB";
    private static final String IP_ADDRESS = "8.8.8.8";

    private String url = "https://mdepayments.epdq.co.uk/ncol/test";
    private String merchantId = envOrThrow("GDS_CONNECTOR_EPDQ_MERCHANT_ID");
    private String username = envOrThrow("GDS_CONNECTOR_EPDQ_USER");
    private String password = envOrThrow("GDS_CONNECTOR_EPDQ_PASSWORD");
    private String shaInPassphrase = envOrThrow("GDS_CONNECTOR_EPDQ_SHA_IN_PASSPHRASE");
    private ChargeEntity chargeEntity;
    private GatewayAccountEntity gatewayAccountEntity;
    private EpdqPaymentProvider paymentProvider;

    @Mock
    private MetricRegistry mockMetricRegistry;

    @Mock
    private Histogram mockHistogram;
    
    @Mock
    private Counter mockCounter;

    @Mock
    private Environment mockEnvironment;

    @Mock
    private LinksConfig mockLinksConfig;

    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;

    @Mock
    private GatewayConfig mockGatewayConfig;

    @Mock
    private GatewayClientFactory mockGatewayClientFactory;

    @Before
    public void setUp() {
        when(mockConnectorConfiguration.getLinks()).thenReturn(mockLinksConfig);
        when(mockConnectorConfiguration.getGatewayConfigFor(EPDQ)).thenReturn(mockGatewayConfig);
        when(mockLinksConfig.getFrontendUrl()).thenReturn("http://frontendUrl");
        when(mockGatewayConfig.getUrls()).thenReturn(Map.of(TEST.toString(), url));
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);

        Client client = TestClientFactory.createJerseyClient();
        GatewayClient gatewayClient = new GatewayClient(client,
                mockMetricRegistry);

        when(mockGatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class),
                any(GatewayOperation.class),
                any())).thenReturn(gatewayClient);

        paymentProvider = new EpdqPaymentProvider(
                mockConnectorConfiguration, 
                mockGatewayClientFactory, 
                mockEnvironment,
                new EpdqPayloadDefinitionForCancelOrder(),
                new EpdqPayloadDefinitionForCaptureOrder(),
                new EpdqPayloadDefinitionForNewOrder(),
                new EpdqPayloadDefinitionForQueryOrder(),
                Clock.fixed(Instant.parse("2020-01-01T10:10:10.100Z"), ZoneOffset.UTC));
    }

    @Test
    public void shouldAuthoriseSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
    }

    @Test
    public void shouldAuthoriseSuccessfullyWithNoAddressInRequest() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(null)
                .build();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);

        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
    }

    @Test
    public void shouldAuthoriseWith3dsOnSuccessfully() throws Exception {
        setUpFor3dsAndCheckThatEpdqIsUp();
        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixtureThatWillRequire3ds1().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(REQUIRES_3DS));
    }

    @Test
    public void shouldAuthoriseWith3dsOnAndNoAddressInRequestSuccessfully() throws Exception {
        setUpFor3dsAndCheckThatEpdqIsUp();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardNo(VISA_CARD_NUMBER_RECOGNISED_AS_REQUIRING_3DS1_BY_EPDQ)
                .withAddress(null)
                .build();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(REQUIRES_3DS));
    }

    @Test
    public void shouldAuthoriseWith3ds2OnSuccessfully() throws Exception {
        setUpFor3ds2AndCheckThatEpdqIsUp();
        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixtureThatWillRequire3ds2().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(REQUIRES_3DS));
    }

    @Test
    public void shouldAuthoriseWith3ds2OnAndMaxLengthsExceededOnSuccessfully() throws Exception {
        String acceptHeaderLongerThanEpdq3ds2LimitOf2048Characters = "application/" + RandomStringUtils.randomAlphabetic(2048);
        String userAgentHeaderLongerThanEpdq3ds2LimitOf2048Characters = RandomStringUtils.randomAlphabetic(2048) + "/1.0";
        String languageTagLongerThanEpdq3ds2LimitOf8Characters = "en-GB-cockney";

        setUpFor3ds2AndCheckThatEpdqIsUp();
        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixtureThatWillRequire3ds2()
                .withAcceptHeader(acceptHeaderLongerThanEpdq3ds2LimitOf2048Characters)
                .withUserAgentHeader(userAgentHeaderLongerThanEpdq3ds2LimitOf2048Characters)
                .withJsNavigatorLanguage(languageTagLongerThanEpdq3ds2LimitOf8Characters)
                .build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(REQUIRES_3DS));
    }

    @Test
    public void shouldCheckAuthorisationStatusSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));

        Gateway3DSAuthorisationResponse queryResponse = paymentProvider.authorise3dsResponse(buildQueryRequest(chargeEntity, Auth3dsResult.Auth3dsResultOutcome.AUTHORISED.name()));
        assertThat(queryResponse.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
    }

    @Test
    public void shouldAuthoriseSuccessfullyWhenCardholderNameContainsRightSingleQuotationMark() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        String cardholderName = "John O’Connor"; // That’s a U+2019 RIGHT SINGLE QUOTATION MARK, not a U+0027 APOSTROPHE

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder(cardholderName)
                .build();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
    }

    @Test
    public void shouldCaptureSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));

        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));
        CaptureGatewayRequest captureRequest = buildCaptureRequest(chargeEntity, transactionId);
        CaptureResponse captureResponse = paymentProvider.capture(captureRequest);
        assertThat(captureResponse.isSuccessful(), is(true));
        assertThat(captureResponse.state(), Is.is(CaptureResponse.ChargeState.PENDING));
    }

    @Test
    public void shouldCancelSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));

        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));
        CancelGatewayRequest cancelRequest = buildCancelRequest(chargeEntity, transactionId);
        GatewayResponse<BaseCancelResponse> cancelResponse = paymentProvider.cancel(cancelRequest);
        assertThat(cancelResponse.isSuccessful(), is(true));
    }

    @Test
    public void shouldRefundSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));

        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));
        CaptureGatewayRequest captureRequest = buildCaptureRequest(chargeEntity, transactionId);
        CaptureResponse captureResponse = paymentProvider.capture(captureRequest);
        assertThat(captureResponse.isSuccessful(), is(true));

        RefundGatewayRequest refundGatewayRequest = buildRefundRequest(chargeEntity, (chargeEntity.getAmount() - 100));
        GatewayRefundResponse refundResponse = paymentProvider.refund(refundGatewayRequest);
        assertThat(refundResponse.isSuccessful(), is(true));
    }

    @Test
    public void shouldQueryPaymentStatusSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));

        ChargeQueryResponse chargeQueryResponse = paymentProvider.queryPaymentStatus(chargeEntity);
        assertThat(chargeQueryResponse.getMappedStatus(), is(Optional.of(ChargeStatus.AUTHORISATION_SUCCESS)));
        assertThat(chargeQueryResponse.foundCharge(), is(true));
    }

    @Test
    public void shouldReturnQueryResponseWhenChargeNotFound() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        ChargeQueryResponse chargeQueryResponse = paymentProvider.queryPaymentStatus(chargeEntity);
        assertThat(chargeQueryResponse.getMappedStatus(), is(Optional.empty()));
        assertThat(chargeQueryResponse.foundCharge(), is(false));
    }

    private static AuthCardDetailsFixture authCardDetailsFixture() {
        return AuthCardDetailsFixture.anAuthCardDetails();
    }

    private static AuthCardDetailsFixture authCardDetailsFixtureThatWillRequire3ds1() {
        return authCardDetailsFixture().withCardNo(VISA_CARD_NUMBER_RECOGNISED_AS_REQUIRING_3DS1_BY_EPDQ);
    }

    private static AuthCardDetailsFixture authCardDetailsFixtureThatWillRequire3ds2() {
        return authCardDetailsFixture()
                .withCardNo(VISA_CARD_NUMBER_RECOGNISED_AS_REQUIRING_3DS2_BY_EPDQ)
                .withAddress(new Address(
                        ADDRESS_LINE_1,
                        ADDRESS_LINE_2,
                        ADDRESS_POSTCODE,
                        ADDRESS_CITY,
                        null,
                        ADDRESS_COUNTRY
                ))
                .withIpAddress(IP_ADDRESS);
    }

    private static Auth3dsResponseGatewayRequest buildQueryRequest(ChargeEntity chargeEntity, String auth3DResult) {
        Auth3dsResult auth3DsResult = new Auth3dsResult();
        auth3DsResult.setAuth3dsResult(auth3DResult);
        return new Auth3dsResponseGatewayRequest(chargeEntity, auth3DsResult);
    }

    private void setUpFor3ds2AndCheckThatEpdqIsUp() {
        epdqSetupWithStatusCheck(true, true);
    }
    
    private void setUpFor3dsAndCheckThatEpdqIsUp() {
        epdqSetupWithStatusCheck(true, false);
    }
    
    private void setUpAndCheckThatEpdqIsUp() {
        epdqSetupWithStatusCheck(false, false);
    }

    private void epdqSetupWithStatusCheck(boolean require3ds, boolean requires3ds2) {
        try {
            new URL(url).openConnection().connect();
            Map<String, String> validEpdqCredentials = ImmutableMap.of(
                    "merchant_id", merchantId,
                    "username", username,
                    "password", password,
                    "sha_in_passphrase", shaInPassphrase);
            
            gatewayAccountEntity = new GatewayAccountEntity();
            gatewayAccountEntity.setId(123L);
            gatewayAccountEntity.setGatewayName("epdq");
            gatewayAccountEntity.setCredentials(validEpdqCredentials);
            gatewayAccountEntity.setType(TEST);
            gatewayAccountEntity.setRequires3ds(require3ds);
            
            if (requires3ds2) {
                gatewayAccountEntity.setIntegrationVersion3ds(2);
                gatewayAccountEntity.setSendPayerIpAddressToGateway(true);
            }

            chargeEntity = aValidChargeEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withTransactionId(randomUUID().toString())
                    .build();

        } catch (IOException ex) {
            Assume.assumeTrue(false);
        }
    }

    private static CaptureGatewayRequest buildCaptureRequest(ChargeEntity chargeEntity, String transactionId) {
        chargeEntity.setGatewayTransactionId(transactionId);
        return CaptureGatewayRequest.valueOf(chargeEntity);
    }

    private RefundGatewayRequest buildRefundRequest(ChargeEntity chargeEntity, Long refundAmount) {
        return RefundGatewayRequest.valueOf(Charge.from(chargeEntity), new RefundEntity(refundAmount, userExternalId, userEmail, chargeEntity.getExternalId()),
                gatewayAccountEntity);
    }

    private CancelGatewayRequest buildCancelRequest(ChargeEntity chargeEntity, String transactionId) {
        chargeEntity.setGatewayTransactionId(transactionId);
        return CancelGatewayRequest.valueOf(chargeEntity);
    }
}
