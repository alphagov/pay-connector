package uk.gov.pay.connector.gateway.adyen;

import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.KeyValuePair;
import org.slf4j.event.Level;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.AdyenIds;
import uk.gov.pay.connector.app.adyen.BaseUrls;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.adyen.request.json.BillingAddress;
import uk.gov.pay.connector.gateway.adyen.request.json.RefundRequestPayload;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.service.payments.commons.model.AgreementPaymentType;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.adyen.AdyenRecurringAuthTokenKeys.SHOPPER_REFERENCE;
import static uk.gov.pay.connector.gateway.adyen.AdyenRecurringAuthTokenKeys.STORED_PAYMENT_METHOD_ID;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequestFixture.aCardAuthorisationGatewayRequest;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntityFixture.aPaymentInstrumentEntity;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

class AdyenRequestFactoryTest {

    final String acceptHeader = "text/html";
    final String userAgent = "Mozilla/5.0";
    final String shopperIp = "127.0.0.1";
    final String language = "en-GB";
    final String colorDepth = "24";
    final String screenHeight = "900";
    final String screenWidth = "1440";
    final String timezoneOffset = "-60";
    final String shopperEmail = "test@example.com";

    public static final BillingAddress FULL_BILLING_ADDRESS = new BillingAddress(
            "line1",
            "line2",
            "city",
            "country",
            "postcode",
            null);
    private final ConnectorConfiguration mockConfig = mock(ConnectorConfiguration.class);
    private final LinksConfig mockedLinksConfig = mock(LinksConfig.class);
    private final AdyenRequestFactory adyenRequestFactory = new AdyenRequestFactory(mockConfig);
    public static final AdyenCredentials ADYEN_CREDENTIALS = new AdyenCredentials(
            "legal_entity_id",
            "store_id",
            "account_holder_id",
            "balance_account_id");
    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenRequestFactory.class);

    @BeforeEach
    void setUp() {
        when(mockConfig.getLinks()).thenReturn(mockedLinksConfig);
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("https://www.example.com");
        BaseUrls mockBaseUrls = mock(BaseUrls.class);
        when(mockBaseUrls.checkout()).thenReturn(new BaseUrls.CheckoutUrls("https://example.com/test/someVersion", "https://example.com/live/someVersion"));
        AdyenGatewayConfig mockAdyenGatewayConfig = mock(AdyenGatewayConfig.class);
        when(mockAdyenGatewayConfig.getMerchantAccountIds()).thenReturn(new AdyenIds("test", "live"));
        when(mockAdyenGatewayConfig.getBaseUrls()).thenReturn(mockBaseUrls);
        when(mockConfig.getAdyenGatewayConfig()).thenReturn(mockAdyenGatewayConfig);
    }

    @Test
    void should_create_PaymentRequest_with_full_billing_address() {
        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails()
                        .withAddress(makeFullBillingAddress())
                        .withCardNo("4444333322221111")
                        .withCardHolder("John Doe")
                        .withCvc("737")
                        .withEndDate(CardExpiryDate.valueOf("10/99"))
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .withAmount("6234")
                .build();

        var request = adyenRequestFactory.createPaymentRequest(authoriseRequest);

        assertThat(request.billingAddress(), is(FULL_BILLING_ADDRESS));
        assertThat(request.paymentMethod().cvc(), is("737"));
        assertThat(request.paymentMethod().number(), is("4444333322221111"));
        assertThat(request.paymentMethod().expiryMonth(), is("10"));
        assertThat(request.paymentMethod().expiryYear(), is("2099"));
        assertThat(request.paymentMethod().holderName(), is("John Doe"));
        assertThat(request.paymentMethod().type(), is("scheme"));
        assertThat(request.amount().value(), is(Long.valueOf("6234")));
        assertThat(request.amount().currency(), is("GBP"));
        assertThat(request.channel(), is("Web"));
        assertThat(request.shopperInteraction(), is("Ecommerce"));
        assertThat(request.returnUrl(), is("https://www.example.com/card_details/gov_uk_payment_id/3ds_required_in/adyen"));
        assertThat(request.reference(), is("gov_uk_payment_id"));
        assertThat(request.merchantAccount(), is("test"));
        assertThat(request.store(), is("store_id"));
        assertThat(request.additionalData().get("manualCapture"), is("true"));
    }

    @Test
    void should_create_PaymentRequest_with_partial_billing_address() {
        var partialBillingAddress = new Address(
                "line1",
                null,
                null,
                null,
                null,
                null);
        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails()
                        .withAddress(partialBillingAddress)
                        .withCardNo("4444333322221111")
                        .withCardHolder("John Doe")
                        .withCvc("737")
                        .withEndDate(CardExpiryDate.valueOf("10/99"))
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .withAmount("6234")
                .build();

        var request = adyenRequestFactory.createPaymentRequest(authoriseRequest);

        assertThat(request.billingAddress().houseNumberOrName(), is(partialBillingAddress.getLine1()));
        assertThat(request.billingAddress().street(), nullValue());
        assertThat(request.billingAddress().city(), nullValue());
        assertThat(request.billingAddress().country(), nullValue());
        assertThat(request.billingAddress().postalCode(), nullValue());
        assertThat(request.paymentMethod().cvc(), is("737"));
        assertThat(request.paymentMethod().number(), is("4444333322221111"));
        assertThat(request.paymentMethod().expiryMonth(), is("10"));
        assertThat(request.paymentMethod().expiryYear(), is("2099"));
        assertThat(request.paymentMethod().holderName(), is("John Doe"));
        assertThat(request.paymentMethod().type(), is("scheme"));
        assertThat(request.amount().value(), is(Long.valueOf("6234")));
        assertThat(request.amount().currency(), is("GBP"));
        assertThat(request.channel(), is("Web"));
        assertThat(request.shopperInteraction(), is("Ecommerce"));
        assertThat(request.returnUrl(), is("https://www.example.com/card_details/gov_uk_payment_id/3ds_required_in/adyen"));
        assertThat(request.reference(), is("gov_uk_payment_id"));
        assertThat(request.merchantAccount(), is("test"));
        assertThat(request.store(), is("store_id"));
        assertThat(request.additionalData().get("manualCapture"), is("true"));
    }

    @Test
    void should_throw_IllegalArgumentException_if_credentials_are_not_Adyen_specific() {
        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails().build())
                .withCredentials(() -> false)
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> adyenRequestFactory.createPaymentRequest(authoriseRequest));

        assertThat(exception.getMessage(), is("Expected provided GatewayCredentials to be of type AdyenCredentials"));
    }

    @Test
    void should_create_a_PaymentCancelRequest() {
        var liveMerchantAccountId = "a-live-merchant-account-id";
        var externalChargeId = "a-charge-id";
        givenMerchantAccountIds("a-test-merchant-account-id", liveMerchantAccountId);

        var cancelGatewayRequest = makeCancelGatewayRequestWithExternalChargeId(externalChargeId);
        var paymentCancelRequest = adyenRequestFactory.createPaymentCancelRequest(cancelGatewayRequest);

        assertThat(paymentCancelRequest.reference(), is(externalChargeId));
        assertThat(paymentCancelRequest.merchantAccount(), is(liveMerchantAccountId));
    }

    @Test
    void should_create_a_RefundRequestPayload() {
        var refundExternalId = "refund-external-id";
        RefundGatewayRequest refundGatewayRequest = makeRefundGatewayRequest(refundExternalId);
        RefundRequestPayload refundRequestPayload = adyenRequestFactory.createRefundRequestPayload(refundGatewayRequest);

        assertThat(refundRequestPayload.reference(), is(refundExternalId));
        assertThat(refundRequestPayload.merchantAccount(), is("test"));
        assertThat(refundRequestPayload.amount().value(), is(500L));
        assertThat(refundRequestPayload.amount().currency(), is("GBP"));
        assertThat(refundRequestPayload.storeId(), is("store-123"));
    }

    @Test
    void should_create_a_PaymentRequest_with_shopperInteraction_as_Moto_when_isMoto_is_true() {
        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails()
                        .withCardNo("4444333322221111")
                        .withCardHolder("John Doe")
                        .withCvc("737")
                        .withEndDate(CardExpiryDate.valueOf("10/99"))
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .withAmount("6234")
                .withMoto(true)
                .build();

        var request = adyenRequestFactory.createPaymentRequest(authoriseRequest);

        assertThat(request.shopperInteraction(), is("Moto"));
    }

    @Test
    void should_create_a_PaymentDetailsRequest_with_redirect_result() {
        var auth3dsResult = new Auth3dsResult();
        auth3dsResult.setRedirectResult("redirect-result-value");
        var auth3dsRequest = Auth3dsResponseGatewayRequest.valueOf(aValidChargeEntity().build(), auth3dsResult);

        var paymentDetailsRequest = adyenRequestFactory.createPaymentDetailsRequest(auth3dsRequest);

        assertThat(paymentDetailsRequest.details().redirectResult(), is("redirect-result-value"));
    }

    private static RefundGatewayRequest makeRefundGatewayRequest(String refundExternalId) {
        Charge charge = Charge.from(
                aValidChargeEntity()
                        .build()
        );
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("store_id", "store-123"))
                .withPaymentProvider(ADYEN.getName())
                .withState(ACTIVE)
                .build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();

        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withExternalId(refundExternalId)
                .withAmount(500L)
                .build();

        return RefundGatewayRequest.valueOf(charge, refundEntity,
                gatewayAccountEntity, gatewayAccountCredentialsEntity);
    }


    @Test
    void should_include_browser_info_origin_shopper_email_and_ip_for_web_payment() {
        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails()
                        .withAcceptHeader(acceptHeader)
                        .withUserAgentHeader(userAgent)
                        .withIpAddress(shopperIp)
                        .withJsNavigatorLanguage(language)
                        .withJsScreenColorDepth(colorDepth)
                        .withJsScreenHeight(screenHeight)
                        .withJsScreenWidth(screenWidth)
                        .withJsTimezoneOffsetMins(timezoneOffset)
                        .withJsEnabled(true)
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .withEmail(shopperEmail)
                .build();
        var request = adyenRequestFactory.createPaymentRequest(authoriseRequest);

        assertThat(request.browserInfo().acceptHeader(), is(acceptHeader));
        assertThat(request.browserInfo().colorDepth(), is(Integer.valueOf(colorDepth)));
        assertThat(request.browserInfo().language(), is(language));
        assertThat(request.browserInfo().screenHeight(), is(Integer.valueOf(screenHeight)));
        assertThat(request.browserInfo().screenWidth(), is(Integer.valueOf(screenWidth)));
        assertThat(request.browserInfo().timeZoneOffset(), is(Integer.valueOf(timezoneOffset)));
        assertThat(request.browserInfo().userAgent(), is(userAgent));
        assertThat(request.shopperEmail(), is(shopperEmail));
        assertThat(request.shopperIP(), is(shopperIp));
    }

    @Test
    void should_not_include_browser_info_origin_shopper_email_and_ip_for_moto_payment() {
        var authoriseRequest = aCardAuthorisationGatewayRequest().withMoto(true)
                .withAuthCardDetails(anAuthCardDetails()
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .withEmail("test@example.com")
                .build();

        var request = adyenRequestFactory.createPaymentRequest(authoriseRequest);

        assertThat(request.shopperInteraction(), is("Moto"));
        assertThat(request.browserInfo(), is(nullValue()));
        assertThat(request.origin(), is(nullValue()));
        assertThat(request.shopperEmail(), is(nullValue()));
        assertThat(request.shopperIP(), is(nullValue()));
    }

    @Test
    void should_log_warning_when_browser_info_integer_cannot_be_parsed() {
        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails()
                        .withJsScreenColorDepth("invalid")
                        .withJsScreenWidth("not-a-number")
                        .withJsTimezoneOffsetMins("bad-offset")
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .build();

        adyenRequestFactory.createPaymentRequest(authoriseRequest);

        var loggingEvents = logs.getEvents();

        assertThat(loggingEvents.size(), is(3));

        assertThat(loggingEvents, everyItem(hasProperty("level", is(Level.WARN))));
        assertThat(loggingEvents, everyItem(hasProperty("message", is("Unable to parse browser info integer"))));

        var keyValuePairs = loggingEvents.stream()
                .flatMap(event -> event.getKeyValuePairs().stream())
                .toList();

        assertThat(keyValuePairs, contains(
                new KeyValuePair("property", "js_screen_color_depth"),
                new KeyValuePair("value", "invalid"),
                new KeyValuePair("property", "js_screen_width"),
                new KeyValuePair("value", "not-a-number"),
                new KeyValuePair("property", "js_timezone_offset_min"),
                new KeyValuePair("value", "bad-offset")
        ));
    }

    @Test
    void should_create_payment_request_for_cit_recurring_setup() {
        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails()
                        .withCardNo("4444333322221111")
                        .withCardHolder("John Doe")
                        .withCvc("737")
                        .withEndDate(CardExpiryDate.valueOf("10/99"))
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .withSavePaymentInstrumentToAgreement(true)
                .withAgreementPaymentType(AgreementPaymentType.UNSCHEDULED)
                .build();

        var request = adyenRequestFactory.createPaymentRequest(authoriseRequest);

        assertThat(request.shopperReference(), is(authoriseRequest.getAgreement().get().getExternalId()));
        assertThat(request.storePaymentMethod(), is(true));
        assertThat(request.recurringProcessingModel(), is("UnscheduledCardOnFile"));
        assertThat(request.shopperInteraction(), is("Ecommerce"));
    }

    @Test
    void should_create_payment_request_for_mit_recurring_payment() {
        var paymentInstrument = aPaymentInstrumentEntity()
                .withRecurringAuthToken(Map.of(
                        STORED_PAYMENT_METHOD_ID, "stored-payment-method-id"))
                .build();
        AgreementEntity agreementEntity = anAgreementEntity()
                .withExternalId("agreement-external-id")
                .build();
        var charge = aValidChargeEntity()
                .withAgreementPaymentType(AgreementPaymentType.RECURRING)
                .withPaymentInstrument(paymentInstrument)
                .withAgreementEntity(agreementEntity)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of(
                                "legal_entity_id", "legal_entity_id",
                                "store_id", "store_id",
                                "account_holder_id", "account_holder_id",
                                "balance_account_id", "balance_account_id"))
                        .withPaymentProvider(ADYEN.getName())
                        .build())
                .build();
        var recurringRequest = RecurringPaymentAuthorisationGatewayRequest.valueOf(charge);

        var request = adyenRequestFactory.createRecurringPaymentRequest(recurringRequest);

        assertThat(request.shopperReference(), is("agreement-external-id"));
        assertThat(request.shopperInteraction(), is("ContAuth"));
        assertThat(request.recurringProcessingModel(), is("Subscription"));
        assertThat(request.storePaymentMethod(), is(nullValue()));
        assertThat(request.paymentMethod().storedPaymentMethodId(), is("stored-payment-method-id"));
        assertThat(request.paymentMethod().number(), is(nullValue()));
    }

    private static CancelGatewayRequest makeCancelGatewayRequestWithExternalChargeId(String externalChargeId) {
        var chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withGatewayAccountEntity(
                        aGatewayAccountEntity()
                                .withType(GatewayAccountType.LIVE)
                                .build())
                .build();
        return CancelGatewayRequest.valueOf(chargeEntity);
    }

    private void givenMerchantAccountIds(String test, String live) {
        var mockAdyenGatewayConfig = mock(AdyenGatewayConfig.class);
        given(mockConfig.getAdyenGatewayConfig()).willReturn(mockAdyenGatewayConfig);
        given(mockAdyenGatewayConfig.getMerchantAccountIds()).willReturn(new AdyenIds(test, live));
    }

    private static Address makeFullBillingAddress() {
        Address billingAddress = new Address();
        billingAddress.setLine1("line1");
        billingAddress.setLine2("line2");
        billingAddress.setCity("city");
        billingAddress.setCounty("county");
        billingAddress.setPostcode("postcode");
        billingAddress.setCountry("country");
        return billingAddress;
    }
}
