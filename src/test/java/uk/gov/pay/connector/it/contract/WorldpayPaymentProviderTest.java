package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.DeleteStoredPaymentDetailsGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gateway.worldpay.WorldpayAuthoriseHandler;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCaptureHandler;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.WorldpayRefundHandler;
import uk.gov.pay.connector.gateway.worldpay.wallets.WorldpayWalletAuthorisationHandler;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.logging.AuthorisationLogger;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentprocessor.service.AuthorisationService;
import uk.gov.pay.connector.paymentprocessor.service.CardExecutorService;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;
import uk.gov.pay.connector.wallets.applepay.ApplePayDecrypter;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import javax.ws.rs.client.ClientBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_MERCHANT_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity.Worldpay3dsFlexCredentialsEntityBuilder.aWorldpay3dsFlexCredentialsEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userEmail;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntityFixture.aPaymentInstrumentEntity;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

@Disabled("Ignoring as this test is failing in Jenkins because it's failing to locate the certificates - PP-1707")
class WorldpayPaymentProviderTest {

    private static final String MAGIC_CARDHOLDER_NAME_THAT_MAKES_WORLDPAY_TEST_REQUIRE_3DS = "3D";
    private static final String MAGIC_CARDHOLDER_NAME_FOR_3DS_FLEX_CHALLENGE_REQUIRED_RESPONSE = "3DS_V2_CHALLENGE_IDENTIFIED";

    private static final String VISA_CARD_NUMBER = "4444333322221111";

    private GatewayAccountEntity validGatewayAccount;
    private GatewayAccountEntity validGatewayAccountFor3ds;
    private Map<String, Object> validCredentials;
    private Map<String, Object> validCredentialsFor3ds;
    private GatewayAccountCredentialsEntity validGatewayAccountCredentialsEntity;
    private GatewayAccountCredentialsEntity validGatewayAccountCredentialsEntityFor3ds;
    private ChargeEntity chargeEntity;
    private MetricRegistry mockMetricRegistry;
    private Histogram mockHistogram;
    private Counter mockCounter;
    private Environment mockEnvironment;
    private CardExecutorService mockCardExecutorService = mock(CardExecutorService.class);
    private ConnectorConfiguration mockConnectorConfiguration = mock(ConnectorConfiguration.class);
    private AuthorisationConfig mockAuthorisationConfig = mock(AuthorisationConfig.class);
    private ApplePayDecrypter mockApplePayDecrypter = mock(ApplePayDecrypter.class);

    @BeforeEach
    void checkThatWorldpayIsUp() throws IOException {
        try {
            var validWorldpayMerchantCodeCredentials = Map.of(
                    CREDENTIALS_MERCHANT_CODE, envOrThrow("GDS_CONNECTOR_WORLDPAY_MERCHANT_ID"),
                    CREDENTIALS_USERNAME, envOrThrow("GDS_CONNECTOR_WORLDPAY_USER"),
                    CREDENTIALS_PASSWORD, envOrThrow("GDS_CONNECTOR_WORLDPAY_PASSWORD"));

            validCredentials = Map.of(
                    ONE_OFF_CUSTOMER_INITIATED, validWorldpayMerchantCodeCredentials,
                    RECURRING_CUSTOMER_INITIATED, validWorldpayMerchantCodeCredentials,
                    RECURRING_MERCHANT_INITIATED, validWorldpayMerchantCodeCredentials);

            validCredentialsFor3ds = Map.of(
                    ONE_OFF_CUSTOMER_INITIATED, Map.of(
                            CREDENTIALS_MERCHANT_CODE, envOrThrow("GDS_CONNECTOR_WORLDPAY_MERCHANT_ID_3DS"),
                            CREDENTIALS_USERNAME, envOrThrow("GDS_CONNECTOR_WORLDPAY_USER_3DS"),
                            CREDENTIALS_PASSWORD, envOrThrow("GDS_CONNECTOR_WORLDPAY_PASSWORD_3DS")));
        } catch (IllegalStateException ex) {
            assumeTrue(false, "Ignoring test since credentials not configured");
        }

        new URL(gatewayUrlMap().get(TEST.toString()).toString()).openConnection().connect();


        validGatewayAccount = new GatewayAccountEntity();
        validGatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(validCredentials)
                .withGatewayAccountEntity(validGatewayAccount)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build();
        validGatewayAccount.setId(1234L);
        validGatewayAccount.setType(TEST);
        validGatewayAccount.setGatewayAccountCredentials(List.of(validGatewayAccountCredentialsEntity));

        validGatewayAccountFor3ds = new GatewayAccountEntity();
        validGatewayAccountCredentialsEntityFor3ds = aGatewayAccountCredentialsEntity()
                .withCredentials(validCredentialsFor3ds)
                .withGatewayAccountEntity(validGatewayAccountFor3ds)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build();
        validGatewayAccountFor3ds.setId(1234L);
        validGatewayAccountFor3ds.setType(TEST);
        validGatewayAccountFor3ds.getCardConfigurationEntity().setRequires3ds(true);
        validGatewayAccountFor3ds.setGatewayAccountCredentials(List.of(validGatewayAccountCredentialsEntityFor3ds));

        mockMetricRegistry = mock(MetricRegistry.class);
        mockHistogram = mock(Histogram.class);
        mockCounter = mock(Counter.class);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        mockEnvironment = mock(Environment.class);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockConnectorConfiguration.getAuthorisationConfig()).thenReturn(mockAuthorisationConfig);
        when(mockAuthorisationConfig.getAsynchronousAuthTimeoutInMilliseconds()).thenReturn(1000);

        chargeEntity = aValidChargeEntity()
                .withTransactionId(randomUUID().toString())
                .withGatewayAccountEntity(validGatewayAccount)
                .withGatewayAccountCredentialsEntity(validGatewayAccountCredentialsEntity)
                .build();
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbers")
    void submitAuthForSoftDecline(String cardBrand, String cardNumber) {
        validGatewayAccount.getCardConfigurationEntity().setRequires3ds(true);
        validGatewayAccount.getCardConfigurationEntity().setIntegrationVersion3ds(2);
        validGatewayAccount.setWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build());

        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        // card holder of "EE.REJECTED_ISSUER_REJECTED.SOFT_DECLINED" elicits a soft decline response, see https://developer.worldpay.com/docs/wpg/scaexemptionservices/exemptionengine#testing-exemption-engine
        var authCardDetails = anAuthCardDetails()
                .withCardNo(cardNumber)
                .withCardHolder("EE.REJECTED_ISSUER_REJECTED.SOFT_DECLINED")
                .withWorldpay3dsFlexDdcResult(UUID.randomUUID().toString())
                .build();
        ChargeEntity charge = createChargeEntity();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);
        assertTrue(response.getBaseResponse().isPresent());
        assertTrue(response.getBaseResponse().get().getLastEvent().isPresent());
        assertEquals(response.getBaseResponse().get().getLastEvent().get(), "AUTHORISED");
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbers")
    void submitAuthRequestWithExemptionEngineFlag(String cardBrand, String cardNumber) {
        validGatewayAccount.getCardConfigurationEntity().setRequires3ds(true);
        validGatewayAccount.getCardConfigurationEntity().setIntegrationVersion3ds(2);
        validGatewayAccount.setWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build());

        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        // card holder of "EE.HONOURED_ISSUER_HONOURED.AUTHORISED" elicits an authorised response, see https://developer.worldpay.com/docs/wpg/scaexemptionservices/exemptionengine#testing-exemption-engine
        var authCardDetails = anAuthCardDetails()
                .withCardNo(cardNumber)
                .withCardHolder("EE.HONOURED_ISSUER_HONOURED.AUTHORISED")
                .withWorldpay3dsFlexDdcResult(UUID.randomUUID().toString())
                .build();
        ChargeEntity charge = createChargeEntity();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);
        assertTrue(response.getBaseResponse().isPresent());
        assertTrue(response.getBaseResponse().get().getLastEvent().isPresent());
        assertEquals(response.getBaseResponse().get().getLastEvent().get(), "AUTHORISED");
        assertTrue(response.getBaseResponse().get().getExemptionResponseResult().isPresent());
        assertEquals(response.getBaseResponse().get().getExemptionResponseResult().get(), "HONOURED");
        assertEquals(response.getBaseResponse().get().getExemptionResponseReason(), "ISSUER_HONOURED");
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbers")
    void submit3DS2FlexAuthRequest(String cardBrand, String cardNumber) {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        var authCardDetails = anAuthCardDetails()
                .withCardNo(cardNumber)
                .withWorldpay3dsFlexDdcResult(UUID.randomUUID().toString())
                .build();
        ChargeEntity charge = createChargeEntity();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);
        assertTrue(response.getBaseResponse().isPresent());
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbersThatRequire3ds")
    void submit3DS2FlexAuthRequest_requiresChallenge(String cardBrand, String cardNumber) {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails()
                .withCardNo(cardNumber)
                .withCardHolder(MAGIC_CARDHOLDER_NAME_FOR_3DS_FLEX_CHALLENGE_REQUIRED_RESPONSE)
                .withWorldpay3dsFlexDdcResult(UUID.randomUUID().toString())
                .build();
        ChargeEntity charge = createChargeEntity();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);
        assertTrue(response.getBaseResponse().isPresent());
        response.getBaseResponse().ifPresent(res -> {
            assertThat(res.getGatewayParamsFor3ds().isPresent(), is(true));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getWorldpayChallengeAcsUrl(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getWorldpayChallengeTransactionId(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getWorldpayChallengePayload(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getThreeDsVersion(), is(notNullValue()));
        });
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbers")
    void shouldBeAbleToSendAuthorisationRequestForMerchant(String cardBrand, String cardNumber) {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails().withCardNo(cardNumber).build();
        ChargeEntity charge = createChargeEntity();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);
        assertTrue(response.getBaseResponse().isPresent());
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbers")
    void shouldBeAbleToSendAuthorisationRequestForMerchantWithoutAddress(String cardBrand, String cardNumber) {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails().withCardNo(cardNumber).withAddress(null).build();
        ChargeEntity charge = createChargeEntity();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);
        assertTrue(response.getBaseResponse().isPresent());
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbers")
    void shouldBeAbleToSendAuthorisationRequestForMerchantWithUsAddress(String cardBrand, String cardNumber) {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        Address usAddress = new Address();
        usAddress.setLine1("125 Kingsway");
        usAddress.setLine2("Aviation House");
        usAddress.setPostcode("90210");
        usAddress.setCity("Washington D.C.");
        usAddress.setCountry("US");
        AuthCardDetails authCardDetails = anAuthCardDetails().withCardNo(cardNumber).withAddress(usAddress).build();
        ChargeEntity charge = createChargeEntity();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);
        assertTrue(response.getBaseResponse().isPresent());
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbers")
    void shouldBeAbleToSendAuthorisationRequestForMerchantWithCanadaAddress(String cardBrand, String cardNumber) {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        Address canadaAddress = new Address();
        canadaAddress.setLine1("125 Kingsway");
        canadaAddress.setLine2("Aviation House");
        canadaAddress.setPostcode("X0A0A0");
        canadaAddress.setCity("Arctic Bay");
        canadaAddress.setCountry("CA");
        AuthCardDetails authCardDetails = anAuthCardDetails().withCardNo(cardNumber).withAddress(canadaAddress).build();
        ChargeEntity charge = createChargeEntity();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);
        assertTrue(response.getBaseResponse().isPresent());
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbersThatRequire3ds")
    void shouldBeAbleToSendAuthorisationRequestForMerchantUsing3ds(String cardBrand, String cardNumber) {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails()
                .withCardNo(cardNumber)
                .withCardHolder(MAGIC_CARDHOLDER_NAME_THAT_MAKES_WORLDPAY_TEST_REQUIRE_3DS)
                .build();

        ChargeEntity charge = createChargeWithRequires3ds();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);

        assertTrue(response.getBaseResponse().isPresent());
        assertTrue(response.getSessionIdentifier().isPresent());
        response.getBaseResponse().ifPresent(res -> {
            assertThat(res.getGatewayParamsFor3ds().isPresent(), is(true));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getPaRequest(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getIssuerUrl(), is(notNullValue()));
        });
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbersForAgreements")
    void shouldBeAbleToSendSetUpAgreementRequestForMerchant(String cardBrand, String cardNumber) {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails().withCardNo(cardNumber).build();
        ChargeEntity charge = createChargeEntity();
        charge.setSavePaymentInstrumentToAgreement(true);
        charge.setAgreementEntity(anAgreementEntity().build());
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);
        assertTrue(response.getBaseResponse().isPresent());
        assertTrue(response.getBaseResponse().get().getGatewayRecurringAuthToken().isPresent());
        assertThat(response.getBaseResponse().get().getGatewayRecurringAuthToken().get(), hasKey(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY));
    }

    @Test
    void shouldBeAbleToTakeRecurringPaymentUsingStoredToken() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AgreementEntity agreement = anAgreementEntity().build();
        PaymentInstrumentEntity paymentInstrument = setUpAgreement(paymentProvider, agreement);

        ChargeEntity recurringCharge = createChargeEntity();
        recurringCharge.setAuthorisationMode(AuthorisationMode.AGREEMENT);
        recurringCharge.setAgreementEntity(agreement);
        recurringCharge.setPaymentInstrument(paymentInstrument);

        var gatewayRequest = RecurringPaymentAuthorisationGatewayRequest.valueOf(recurringCharge);
        GatewayResponse authoriseUserNotPresentResponse = paymentProvider.authoriseUserNotPresent(gatewayRequest);

        assertTrue(authoriseUserNotPresentResponse.getBaseResponse().isPresent());
    }

    @Test
    void shouldBeAbleToSendDeleteTokenRequestAndNotBeAbleToTakeFurtherPaymentsWithToken() throws GatewayException {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AgreementEntity agreement = anAgreementEntity().withGatewayAccount(validGatewayAccount).build();
        PaymentInstrumentEntity paymentInstrument = setUpAgreement(paymentProvider, agreement);

        var gatewayDeleteTokenRequest = DeleteStoredPaymentDetailsGatewayRequest.from(agreement, paymentInstrument);
        paymentProvider.deleteStoredPaymentDetails(gatewayDeleteTokenRequest);
        assertDoesNotThrow(() -> paymentProvider.deleteStoredPaymentDetails(gatewayDeleteTokenRequest));

        ChargeEntity recurringCharge = createChargeEntity();
        recurringCharge.setAuthorisationMode(AuthorisationMode.AGREEMENT);
        recurringCharge.setAgreementEntity(agreement);
        recurringCharge.setPaymentInstrument(paymentInstrument);

        var gatewayPaymentRequest = RecurringPaymentAuthorisationGatewayRequest.valueOf(recurringCharge);
        GatewayResponse authoriseAnotherUserNotPresentResponse = paymentProvider.authoriseUserNotPresent(gatewayPaymentRequest);
        assertTrue(authoriseAnotherUserNotPresentResponse.getGatewayError().isPresent());
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbersThatRequire3ds")
    void shouldBeAbleToSendAuthorisationRequestForMerchantUsing3dsWithoutAddress(String cardBrand, String cardNumber) {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails()
                .withCardNo(cardNumber)
                .withCardHolder(MAGIC_CARDHOLDER_NAME_THAT_MAKES_WORLDPAY_TEST_REQUIRE_3DS)
                .withAddress(null)
                .build();

        ChargeEntity charge = createChargeWithRequires3ds();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);

        assertTrue(response.getBaseResponse().isPresent());
        assertTrue(response.getSessionIdentifier().isPresent());
        response.getBaseResponse().ifPresent(res -> {
            assertThat(res.getGatewayParamsFor3ds().isPresent(), is(true));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getPaRequest(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getIssuerUrl(), is(notNullValue()));
        });
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbersThatRequire3ds")
    void shouldBeAbleToSendAuthorisationRequestForMerchantUsing3dsWithPayerEmail(String cardBrand, String cardNumber) {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();

        validGatewayAccountFor3ds.getCardConfigurationEntity().setSendPayerEmailToGateway(true);

        ChargeEntity charge = aValidChargeEntity()
                .withTransactionId(randomUUID().toString())
                .withEmail("payer@email.test")
                .withGatewayAccountEntity(validGatewayAccountFor3ds)
                .withGatewayAccountCredentialsEntity(validGatewayAccountCredentialsEntityFor3ds)
                .build();

        AuthCardDetails authCardDetails = anAuthCardDetails()
                .withCardNo(cardNumber)
                .withCardHolder(MAGIC_CARDHOLDER_NAME_THAT_MAKES_WORLDPAY_TEST_REQUIRE_3DS)
                .build();

        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);

        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);

        assertTrue(response.getBaseResponse().isPresent());
        assertTrue(response.getSessionIdentifier().isPresent());
        response.getBaseResponse().ifPresent(res -> {
            assertThat(res.getGatewayParamsFor3ds().isPresent(), is(true));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getPaRequest(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getIssuerUrl(), is(notNullValue()));
        });
    }

    @ParameterizedTest
    @MethodSource("worldpayTestCardNumbersThatRequire3ds")
    void shouldBeAbleToSendAuthorisationRequestForMerchantUsing3dsFlexWithNonJs(String cardBrand, String cardNumber) {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();

        validGatewayAccount.getCardConfigurationEntity().setRequires3ds(true);
        validGatewayAccount.getCardConfigurationEntity().setSendPayerEmailToGateway(true);
        validGatewayAccount.getCardConfigurationEntity().setIntegrationVersion3ds(2);

        ChargeEntity charge = aValidChargeEntity()
                .withTransactionId(randomUUID().toString())
                .withEmail("payer@email.test")
                .withGatewayAccountEntity(validGatewayAccount)
                .withGatewayAccountCredentialsEntity(validGatewayAccountCredentialsEntity)
                .build();

        AuthCardDetails authCardDetails = anAuthCardDetails()
                .withCardNo(cardNumber)
                .withCardHolder(MAGIC_CARDHOLDER_NAME_FOR_3DS_FLEX_CHALLENGE_REQUIRED_RESPONSE)
                .withWorldpay3dsFlexDdcResult(null)
                .build();

        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);

        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);

        assertTrue(response.getSessionIdentifier().isPresent());
        response.getBaseResponse().ifPresent(res -> {
            assertThat(res.getGatewayParamsFor3ds().isPresent(), is(true));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getWorldpayChallengeAcsUrl(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getWorldpayChallengeTransactionId(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getWorldpayChallengePayload(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getThreeDsVersion(), startsWith("2."));

        });
    }

    /**
     * Worldpay does not care about a successful authorization reference to make a capture request.
     * It simply accepts anything as long as the request is well formed. (And ignores it silently)
     */
    @Test
    void shouldBeAbleToSendCaptureRequestForMerchant() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        CaptureResponse response = paymentProvider.capture(CaptureGatewayRequest.valueOf(chargeEntity));
        assertTrue(response.isSuccessful());
    }

    @Test
    void shouldBeAbleToSubmitAPartialRefundAfterACaptureHasBeenSubmitted() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails().build();

        ChargeEntity charge = createChargeEntity();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);
        String transactionId = response.getBaseResponse().get().getTransactionId();

        assertThat(response.getBaseResponse().isPresent(), is(true));
        assertThat(response.getBaseResponse().isPresent(), is(true));
        assertThat(transactionId, is(not(nullValue())));

        chargeEntity.setGatewayTransactionId(transactionId);
        CaptureResponse captureResponse = paymentProvider.capture(CaptureGatewayRequest.valueOf(chargeEntity));

        assertThat(captureResponse.isSuccessful(), is(true));

        RefundEntity refundEntity = new RefundEntity(1L, userExternalId, userEmail, chargeEntity.getExternalId());

        GatewayRefundResponse refundResponse = paymentProvider.refund(RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, validGatewayAccount, validGatewayAccountCredentialsEntity));

        assertTrue(refundResponse.isSuccessful());
    }

    @Test
    void shouldBeAbleToSendCancelRequestForMerchant() throws Exception {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails().build();
        ChargeEntity charge = createChargeEntity();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request, charge);

        assertThat(response.getBaseResponse().isPresent(), is(true));
        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));

        chargeEntity.setGatewayTransactionId(transactionId);

        CancelGatewayRequest cancelGatewayRequest = CancelGatewayRequest.valueOf(chargeEntity);
        GatewayResponse cancelResponse = paymentProvider.cancel(cancelGatewayRequest);

        assertThat(cancelResponse.getBaseResponse().isPresent(), is(true));
    }

    @Test
    void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() {

        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();

        Long gatewayAccountId = 112233L;
        Map<String, Object> credentials = Map.of(
                "merchant_id", "non-existent-id",
                "username", "non-existent-username",
                "password", "non-existent-password"
        );

        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity(TEST);
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(credentials)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        gatewayAccountEntity.setId(gatewayAccountId);

        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .build();
        AuthCardDetails authCardDetails = anAuthCardDetails().build();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        assertFalse(paymentProvider.authorise(request, charge).getBaseResponse().isPresent());
    }

    // https://developerengine.fisglobal.com/apis/wpg/reference/testvalues#test-card-numbers
    // China UnionPay does not seem to be enabled on at least some Worldpay test accounts
    static Stream<Arguments> worldpayTestCardNumbers() {
        return Stream.of(
                arguments("American Express", "343434343434343"),
                arguments("Diners Club", "36700102000000"),
                arguments("Diners Club", "36148900647913"),
                arguments("Discover", "6011000400000000"),
                arguments("JCB", "3528000700000000"),
                arguments("Maestro", "6759649826438453"),
                arguments("Mastercard", "5555555555554444"),
                arguments("Mastercard", "5454545454545454"),
                arguments("Mastercard", "2221000000000009"),
                arguments("Mastercard Debit", "5163613613613613"),
                arguments("Visa", "4444333322221111"),
                arguments("Visa", "4911830000000"),
                arguments("Visa", "4917610000000000"),
                arguments("Visa Debit", "4462030000000000"),
                arguments("Visa Debit", "4917610000000000003"),
                arguments("Visa Electron", "4917300800000000"),
                arguments("Visa Purchasing", "4484070000000000")
        );
    }

    // The Visa Purchasing test card does not currently create a token when setting up an agreement.
    // This is being checked with Worldpay.
    static Stream<Arguments> worldpayTestCardNumbersForAgreements() {
        return worldpayTestCardNumbers()
                .filter(arguments -> !("Visa Purchasing".equals(arguments.get()[0])));
    }

    // For at least some Worldpay test accounts, Diners Club and Discover
    // cards never seem to require 3DS and instead authorise immediately
    static Stream<Arguments> worldpayTestCardNumbersThatRequire3ds() {
        return worldpayTestCardNumbers()
                .filter(arguments -> !("Diners Club".equals(arguments.get()[0])))
                .filter(arguments -> !("Discover".equals(arguments.get()[0])));
    }

    private ChargeEntity createChargeEntity() {
        return aValidChargeEntity()
                .withTransactionId(randomUUID().toString())
                .withGatewayAccountEntity(validGatewayAccount)
                .withGatewayAccountCredentialsEntity(validGatewayAccountCredentialsEntity)
                .build();
    }

    private ChargeEntity createChargeWithRequires3ds() {
        return aValidChargeEntity()
                .withTransactionId(randomUUID().toString())
                .withGatewayAccountEntity(validGatewayAccountFor3ds)
                .withGatewayAccountCredentialsEntity(validGatewayAccountCredentialsEntityFor3ds)
                .build();
    }

    private WorldpayPaymentProvider getValidWorldpayPaymentProvider() {
        GatewayClient gatewayClient = new GatewayClient(ClientBuilder.newClient(), mockMetricRegistry);

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(
                any(PaymentGatewayName.class),
                any(GatewayOperation.class),
                any(MetricRegistry.class))).thenReturn(gatewayClient);

        return new WorldpayPaymentProvider(
                gatewayUrlMap(),
                gatewayClient,
                gatewayClient,
                gatewayClient,
                gatewayClient,
                new WorldpayWalletAuthorisationHandler(gatewayClient, gatewayUrlMap(), mockApplePayDecrypter),
                new WorldpayAuthoriseHandler(gatewayClient, gatewayUrlMap(), new AcceptLanguageHeaderParser()),
                new WorldpayCaptureHandler(gatewayClient, gatewayUrlMap()),
                new WorldpayRefundHandler(gatewayClient, gatewayUrlMap()),
                new AuthorisationService(mockCardExecutorService, mockEnvironment, mockConnectorConfiguration),
                new AuthorisationLogger(new AuthorisationRequestSummaryStringifier(), new AuthorisationRequestSummaryStructuredLogging()),
                mock(ChargeDao.class),
                mock(EventService.class));
    }

    private Map<String, URI> gatewayUrlMap() {
        return Map.of(TEST.toString(), URI.create("https://secure-test.worldpay.com/jsp/merchant/xml/paymentService.jsp"));
    }

    private PaymentInstrumentEntity setUpAgreement(WorldpayPaymentProvider paymentProvider, AgreementEntity agreement) {
        AuthCardDetails authCardDetails = anAuthCardDetails().withCardNo(VISA_CARD_NUMBER).build();
        ChargeEntity setUpAgreementCharge = createChargeEntity();
        setUpAgreementCharge.setSavePaymentInstrumentToAgreement(true);
        setUpAgreementCharge.setAgreementEntity(agreement);
        var request = new CardAuthorisationGatewayRequest(setUpAgreementCharge, authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> setUpAgreementResponse = paymentProvider.authorise(request, setUpAgreementCharge);

        Map<String, String> recurringAuthToken = setUpAgreementResponse.getBaseResponse().get().getGatewayRecurringAuthToken().get();
        PaymentInstrumentEntity paymentInstrument = aPaymentInstrumentEntity()
                .withRecurringAuthToken(recurringAuthToken)
                .build();
        return paymentInstrument;
    }
}
