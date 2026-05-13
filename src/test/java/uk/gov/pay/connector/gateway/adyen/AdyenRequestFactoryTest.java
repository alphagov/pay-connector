package uk.gov.pay.connector.gateway.adyen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.AdyenIds;
import uk.gov.pay.connector.app.adyen.BaseUrls;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.adyen.model.json.BillingAddress;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequestFixture.aCardAuthorisationGatewayRequest;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

class AdyenRequestFactoryTest {
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

    @BeforeEach
    void setUp() {
        when(mockConfig.getLinks()).thenReturn(mockedLinksConfig);
        when(mockedLinksConfig.getFrontendUrl()).thenReturn("https://www.example.com");

        BaseUrls mockBaseUrls = mock(BaseUrls.class);
        when(mockBaseUrls.checkout()).thenReturn(new BaseUrls.CheckoutUrls("https://example.com/test/v71", "https://example.com/live/v71"));
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
        assertThat(request.returnUrl(), is("https://www.example.com"));
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
        assertThat(request.returnUrl(), is("https://www.example.com"));
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
