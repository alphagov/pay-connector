package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.RandomStringUtils;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider;
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
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.TestClientFactory;

import javax.ws.rs.client.Client;
import java.io.IOException;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userEmail;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

@Disabled("Ignoring as this test is failing in Jenkins because it's failing to locate the certificates - PP-1707")
@ExtendWith(MockitoExtension.class)
class EpdqPaymentProviderTest {

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
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;
    private EpdqPaymentProvider paymentProvider;

    @Mock
    private MetricRegistry mockMetricRegistry;

    @Mock
    private Histogram mockHistogram;

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

    @BeforeEach
    void setUp() {
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
                Clock.fixed(Instant.parse("2020-01-01T10:10:10.100Z"), ZoneOffset.UTC));
    }

    @Test
    void shouldAuthoriseSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));
    }

    @Test
    void shouldAuthoriseSuccessfullyWithNoAddressInRequest() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        AuthCardDetails authCardDetails = authCardDetailsFixture()
                .withAddress(null)
                .build();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);

        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));
    }

    @Test
    void shouldAuthoriseWith3dsOnSuccessfully() throws Exception {
        setUpFor3dsAndCheckThatEpdqIsUp();
        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixtureThatWillRequire3ds1().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(REQUIRES_3DS));
    }

    @Test
    void shouldAuthoriseWith3dsOnAndNoAddressInRequestSuccessfully() throws Exception {
        setUpFor3dsAndCheckThatEpdqIsUp();

        AuthCardDetails authCardDetails = authCardDetailsFixture()
                .withCardNo(VISA_CARD_NUMBER_RECOGNISED_AS_REQUIRING_3DS1_BY_EPDQ)
                .withAddress(null)
                .build();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(REQUIRES_3DS));
    }

    @Test
    void shouldAuthoriseWith3ds2OnSuccessfully() throws Exception {
        setUpFor3ds2AndCheckThatEpdqIsUp();
        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixtureThatWillRequire3ds2().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(REQUIRES_3DS));
    }

    @Test
    void shouldAuthoriseWith3ds2OnAndMaxLengthsExceededOnSuccessfully() throws Exception {
        String addressLine1LongerThanEpdq3ds2LimitOf35Characters = "1001 Periphrastic Circuitous Crescent";
        String addressLine2LongerThanEpdq3ds2LimitOf35Characters = "Discursive Prolix Multiloquent Wittering";
        String addressCityLongerThanEpdq3ds2LimitOf25Characters = "Prolonged Longhampton-upon-Sea";
        String acceptHeaderLongerThanEpdq3ds2LimitOf2048Characters = "application/" + RandomStringUtils.randomAlphabetic(2048);
        String userAgentHeaderLongerThanEpdq3ds2LimitOf2048Characters = RandomStringUtils.randomAlphabetic(2048) + "/1.0";
        String languageTagLongerThanEpdq3ds2LimitOf8Characters = "en-GB-cockney";

        setUpFor3ds2AndCheckThatEpdqIsUp();
        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixtureThatWillRequire3ds2()
                .withAddress(new Address(
                        addressLine1LongerThanEpdq3ds2LimitOf35Characters,
                        addressLine2LongerThanEpdq3ds2LimitOf35Characters,
                        ADDRESS_POSTCODE,
                        addressCityLongerThanEpdq3ds2LimitOf25Characters,
                        null,
                        ADDRESS_COUNTRY
                ))
                .withAcceptHeader(acceptHeaderLongerThanEpdq3ds2LimitOf2048Characters)
                .withUserAgentHeader(userAgentHeaderLongerThanEpdq3ds2LimitOf2048Characters)
                .withJsNavigatorLanguage(languageTagLongerThanEpdq3ds2LimitOf8Characters)
                .build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(REQUIRES_3DS));
    }

    @Test
    void shouldCheckAuthorisationStatusSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));

        Gateway3DSAuthorisationResponse queryResponse = paymentProvider.authorise3dsResponse(buildQueryRequest(chargeEntity, Auth3dsResult.Auth3dsResultOutcome.AUTHORISED.name()));
        assertThat(queryResponse.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
    }

    @Test
    void shouldAuthoriseSuccessfullyWhenCardholderNameContainsRightSingleQuotationMark() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        String cardholderName = "John O’Connor"; // That’s a U+2019 RIGHT SINGLE QUOTATION MARK, not a U+0027 APOSTROPHE

        AuthCardDetails authCardDetails = authCardDetailsFixture()
                .withCardHolder(cardholderName)
                .build();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));
    }

    @Test
    void shouldCaptureSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));

        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));
        CaptureGatewayRequest captureRequest = buildCaptureRequest(chargeEntity, transactionId);
        CaptureResponse captureResponse = paymentProvider.capture(captureRequest);
        assertThat(captureResponse.isSuccessful(), is(true));
        assertThat(captureResponse.state(), Is.is(CaptureResponse.ChargeState.PENDING));
    }

    @Test
    void shouldCancelSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));

        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));
        CancelGatewayRequest cancelRequest = buildCancelRequest(chargeEntity, transactionId);
        GatewayResponse<BaseCancelResponse> cancelResponse = paymentProvider.cancel(cancelRequest);
        assertThat(cancelResponse.isSuccessful(), is(true));
    }

    @Test
    void shouldRefundSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
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
    void shouldQueryPaymentStatusSuccessfully() throws Exception {
        setUpAndCheckThatEpdqIsUp();

        var request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetailsFixture().build());
        GatewayResponse<BaseAuthoriseResponse> response = paymentProvider.authorise(request, chargeEntity);
        assertThat(response.isSuccessful(), is(true));

        ChargeQueryGatewayRequest chargeQueryGatewayRequest = ChargeQueryGatewayRequest.valueOf(Charge.from(chargeEntity), chargeEntity.getGatewayAccount(), chargeEntity.getGatewayAccountCredentialsEntity());
        ChargeQueryResponse chargeQueryResponse = paymentProvider.queryPaymentStatus(chargeQueryGatewayRequest);
        assertThat(chargeQueryResponse.getMappedStatus(), is(Optional.of(ChargeStatus.AUTHORISATION_SUCCESS)));
        assertThat(chargeQueryResponse.foundCharge(), is(true));
    }

    @Test
    void shouldReturnQueryResponseWhenChargeNotFound() throws Exception {
        setUpAndCheckThatEpdqIsUp();
        ChargeQueryGatewayRequest chargeQueryGatewayRequest = ChargeQueryGatewayRequest.valueOf(Charge.from(chargeEntity), chargeEntity.getGatewayAccount(), chargeEntity.getGatewayAccountCredentialsEntity());
        ChargeQueryResponse chargeQueryResponse = paymentProvider.queryPaymentStatus(chargeQueryGatewayRequest);
        assertThat(chargeQueryResponse.getMappedStatus(), is(Optional.empty()));
        assertThat(chargeQueryResponse.foundCharge(), is(false));
    }

    private static AuthCardDetailsFixture authCardDetailsFixture() {
        return AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(new Address(
                        ADDRESS_LINE_1,
                        ADDRESS_LINE_2,
                        ADDRESS_POSTCODE,
                        ADDRESS_CITY,
                        null,
                        ADDRESS_COUNTRY
                ));
    }

    private static AuthCardDetailsFixture authCardDetailsFixtureThatWillRequire3ds1() {
        return authCardDetailsFixture().withCardNo(VISA_CARD_NUMBER_RECOGNISED_AS_REQUIRING_3DS1_BY_EPDQ);
    }

    private static AuthCardDetailsFixture authCardDetailsFixtureThatWillRequire3ds2() {
        return authCardDetailsFixture()
                .withCardNo(VISA_CARD_NUMBER_RECOGNISED_AS_REQUIRING_3DS2_BY_EPDQ)
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
            Map<String, Object> validEpdqCredentials = ImmutableMap.of(
                    "merchant_id", merchantId,
                    "username", username,
                    "password", password,
                    "sha_in_passphrase", shaInPassphrase);

            gatewayAccountEntity = new GatewayAccountEntity();
            gatewayAccountEntity.setId(123L);
            gatewayAccountEntity.setType(TEST);
            gatewayAccountEntity.getCardConfigurationEntity().setRequires3ds(require3ds);
            gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                    .withCredentials(validEpdqCredentials)
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withPaymentProvider(EPDQ.getName())
                    .build();

            gatewayAccountEntity.setGatewayAccountCredentials(
                    List.of(gatewayAccountCredentialsEntity));

            if (requires3ds2) {
                gatewayAccountEntity.getCardConfigurationEntity().setIntegrationVersion3ds(2);
                gatewayAccountEntity.getCardConfigurationEntity().setSendPayerIpAddressToGateway(true);
            }

            chargeEntity = aValidChargeEntity()
                    .withGatewayAccountEntity(gatewayAccountEntity)
                    .withTransactionId(randomUUID().toString())
                    .build();

        } catch (IOException ex) {
            fail();
        }
    }

    private static CaptureGatewayRequest buildCaptureRequest(ChargeEntity chargeEntity, String transactionId) {
        chargeEntity.setGatewayTransactionId(transactionId);
        return CaptureGatewayRequest.valueOf(chargeEntity);
    }

    private RefundGatewayRequest buildRefundRequest(ChargeEntity chargeEntity, Long refundAmount) {
        return RefundGatewayRequest.valueOf(Charge.from(chargeEntity), new RefundEntity(refundAmount, userExternalId, userEmail, chargeEntity.getExternalId()),
                gatewayAccountEntity, gatewayAccountCredentialsEntity);
    }

    private CancelGatewayRequest buildCancelRequest(ChargeEntity chargeEntity, String transactionId) {
        chargeEntity.setGatewayTransactionId(transactionId);
        return CancelGatewayRequest.valueOf(chargeEntity);
    }
}
