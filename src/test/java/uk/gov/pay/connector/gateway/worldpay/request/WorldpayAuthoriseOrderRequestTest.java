package uk.gov.pay.connector.gateway.worldpay.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.List;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_SPECIAL_CHAR_VALID_AUTHORISE_WORLDPAY_REQUEST_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_3DS_REQUEST_INCLUDING_STATE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_3DS_REQUEST_MIN_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_FULL_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_STATE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_WITHOUT_ADDRESS;

class WorldpayAuthoriseOrderRequestTest {

    private GatewayAccountCredentialsEntity credentialsEntity;
    private AcceptLanguageHeaderParser acceptLanguageHeaderParser = new AcceptLanguageHeaderParser();

    @BeforeEach
    void setUp() {
        WorldpayCredentials worldpayCredentials = new WorldpayCredentials();
        worldpayCredentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials("MERCHANTCODE", "foo", "bar"));
        credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(WORLDPAY.getName())
                .withCredentialsObject(worldpayCredentials)
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build();
    }

    @Test
    void shouldGenerateValidAuthoriseOrderRequestForAddressWithMinimumFieldsWhen3dsEnabled() throws Exception {

        Address minAddress = new Address("123 My Street", null, "SW8URR", "London", null, "GB");

        AuthCardDetails authCardDetails = getValidTestCard(minAddress);

        GatewayOrder actualRequest = buildGatewayOrder(authCardDetails, true);

        String expectedPayload = TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_3DS_REQUEST_MIN_ADDRESS);
        assertXMLEqual(expectedPayload, actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }
    
    @Test
    void shouldGenerateValidAuthoriseOrderRequestForAddressWithState() throws Exception {

        Address usAddress = new Address("10 WCB", null, "20500", "Washington D.C.", null, "US");

        AuthCardDetails authCardDetails = getValidTestCard(usAddress);

        GatewayOrder actualRequest = buildGatewayOrder(authCardDetails, false);

        String expectedPayload = TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_STATE);
        assertXMLEqual(expectedPayload, actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseOrderRequestForAddressWithStateWhen3dsEnabled() throws Exception {

        Address usAddress = new Address("10 WCB", null, "20500", "Washington D.C.", null, "US");

        AuthCardDetails authCardDetails = getValidTestCard(usAddress);

        GatewayOrder actualRequest = buildGatewayOrder(authCardDetails, true);

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_3DS_REQUEST_INCLUDING_STATE), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseOrderRequestForAddressWithAllFields() throws Exception {

        Address fullAddress = new Address("123 My Street", "This road", "SW8URR", "London", "London county", "GB");

        AuthCardDetails authCardDetails = getValidTestCard(fullAddress);

        GatewayOrder actualRequest = buildGatewayOrder(authCardDetails, false);

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_FULL_ADDRESS), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseOrderRequestWhenSpecialCharactersInUserInput() throws Exception {

        Address address = new Address("123 & My Street", "This road -->", "SW8 > URR", "London !>", null, "GB");

        AuthCardDetails authCardDetails = getValidTestCard(address);

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withRequires3ds(false)
                .withGatewayAccountCredentials(List.of(credentialsEntity))
                .build();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withGatewayAccountCredentialsEntity(credentialsEntity)
                .withExternalId("uniqueSessionId")
                .withGatewayTransactionId("MyUniqueTransactionId!")
                .withDescription("This is the description with <!-- ")
                .withAmount(500l)
                .build();

        GatewayOrder actualRequest = buildGatewayOrder(authCardDetails, chargeEntity);

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_SPECIAL_CHAR_VALID_AUTHORISE_WORLDPAY_REQUEST_ADDRESS), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseOrderRequestWhenAddressIsMissing() throws Exception {
        AuthCardDetails authCardDetails = getValidTestCard(null);

        GatewayOrder actualRequest = buildGatewayOrder(authCardDetails, false);

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_WITHOUT_ADDRESS), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    private GatewayOrder buildGatewayOrder(AuthCardDetails authCardDetails, boolean requires3ds) {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withRequires3ds(requires3ds)
                .withGatewayAccountCredentials(List.of(credentialsEntity))
                .build();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withGatewayAccountCredentialsEntity(credentialsEntity)
                .withExternalId("uniqueSessionId")
                .withGatewayTransactionId("MyUniqueTransactionId!")
                .withDescription("This is the description")
                .withAmount(500l)
                .build();

        return buildGatewayOrder(authCardDetails, chargeEntity);
    }

    private GatewayOrder buildGatewayOrder(AuthCardDetails authCardDetails, ChargeEntity chargeEntity) {
        var cardAuthorisationGatewayRequest = CardAuthorisationGatewayRequest.valueOf(chargeEntity, authCardDetails);
        var worldpayAuthoriseOrderRequest =  WorldpayAuthoriseOrderRequest.createAuthoriseOrderRequest(
                cardAuthorisationGatewayRequest, acceptLanguageHeaderParser, false);
        return worldpayAuthoriseOrderRequest.buildGatewayOrder();
    }


    private AuthCardDetails getValidTestCard(Address address) {
        return AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("Mr. Payment")
                .withCardNo("4111111111111111")
                .withCvc("123")
                .withEndDate(CardExpiryDate.valueOf("12/15"))
                .withCardBrand("visa")
                .withAddress(address)
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .build();
    }
}
