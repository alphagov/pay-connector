package uk.gov.pay.connector.gateway.epdq;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.AMOUNT_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.CARDHOLDER_NAME_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.CARD_NO_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.CURRENCY_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.CVC_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.EXPIRY_DATE_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.OPERATION_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.ORDER_ID_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.OWNER_ADDRESS_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.OWNER_COUNTRY_CODE_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.OWNER_TOWN_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.OWNER_ZIP_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.PSPID_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.PSWD_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.USERID_KEY;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_REQUEST;

@ExtendWith(MockitoExtension.class)
class EpdqPayloadDefinitionForNewOrderTest {

    private static final String OPERATION_TYPE = "RES";
    private static final String ORDER_ID = "OrderId";

    private static final String CARD_NO = "4242424242424242";
    private static final String CVC = "321";
    private static final CardExpiryDate END_DATE = CardExpiryDate.valueOf("01/18");

    private static final String AMOUNT = "500";
    private static final String CURRENCY = "GBP";

    private static final String CARDHOLDER_NAME = "Ms Making A Payment";
    private static final String ADDRESS_LINE_1 = "The White Chapel Building";
    private static final String ADDRESS_LINE_2 = "10 White Chapel High Street";
    private static final String CITY = "London";
    private static final String POSTCODE = "E1 8QS";
    private static final String COUNTRY_CODE = "GB";

    private static final String PSP_ID = "PspId";
    private static final String PASSWORD = "password";
    private static final String USER_ID = "User";

    @Mock
    private AuthCardDetails mockAuthCardDetails;

    @Mock
    private Address mockAddress;

    private final EpdqPayloadDefinitionForNewOrder epdqPayloadDefinitionForNewOrder = new EpdqPayloadDefinitionForNewOrder();

    @BeforeEach
    void setUp() {
        epdqPayloadDefinitionForNewOrder.setPspId(PSP_ID);
        epdqPayloadDefinitionForNewOrder.setPassword(PASSWORD);
        epdqPayloadDefinitionForNewOrder.setUserId(USER_ID);
        epdqPayloadDefinitionForNewOrder.setOrderId(ORDER_ID);
        epdqPayloadDefinitionForNewOrder.setAmount(AMOUNT);

        epdqPayloadDefinitionForNewOrder.setAuthCardDetails(mockAuthCardDetails);
    }

    @Test
    void assert_payload_and_order_request_type_are_as_expected() {
        epdqPayloadDefinitionForNewOrder.setOrderId("mq4ht90j2oir6am585afk58kml");
        epdqPayloadDefinitionForNewOrder.setPassword("password");
        epdqPayloadDefinitionForNewOrder.setUserId("username");
        epdqPayloadDefinitionForNewOrder.setPspId("merchant-id");
        epdqPayloadDefinitionForNewOrder.setAmount("500");
        epdqPayloadDefinitionForNewOrder.setAuthCardDetails(aValidEpdqAuthCardDetails());
        epdqPayloadDefinitionForNewOrder.setShaInPassphrase("sha-passphrase");
        GatewayOrder gatewayOrder = epdqPayloadDefinitionForNewOrder.createGatewayOrder();

        assertEquals(TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_REQUEST), gatewayOrder.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, gatewayOrder.getOrderRequestType());
    }

    private AuthCardDetails aValidEpdqAuthCardDetails() {
        Address address = new Address("41", "Scala Street", "EC2A 1AE", "London", "London", "GB");

        return AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("Mr. Payment")
                .withCardNo("5555444433331111")
                .withCvc("737")
                .withEndDate(CardExpiryDate.valueOf("08/18"))
                .withCardBrand("visa")
                .withAddress(address)
                .build();
    }

    @Test
    void shouldExtractParametersWithOneLineStreetAddress() {
        mockCardAndAddressDetails();
        when(mockAddress.getLine1()).thenReturn(ADDRESS_LINE_1);

        List<NameValuePair> result = epdqPayloadDefinitionForNewOrder.extract();

        assertThat(result, is(List.of(
                new BasicNameValuePair(AMOUNT_KEY, AMOUNT),
                new BasicNameValuePair(CARD_NO_KEY, CARD_NO),
                new BasicNameValuePair(CARDHOLDER_NAME_KEY, CARDHOLDER_NAME),
                new BasicNameValuePair(CURRENCY_KEY, CURRENCY),
                new BasicNameValuePair(CVC_KEY, CVC),
                new BasicNameValuePair(EXPIRY_DATE_KEY, END_DATE.toString()),
                new BasicNameValuePair(OPERATION_KEY, OPERATION_TYPE),
                new BasicNameValuePair(ORDER_ID_KEY, ORDER_ID),
                new BasicNameValuePair(OWNER_ADDRESS_KEY, ADDRESS_LINE_1),
                new BasicNameValuePair(OWNER_COUNTRY_CODE_KEY, COUNTRY_CODE),
                new BasicNameValuePair(OWNER_TOWN_KEY, CITY),
                new BasicNameValuePair(OWNER_ZIP_KEY, POSTCODE),
                new BasicNameValuePair(PSPID_KEY, PSP_ID),
                new BasicNameValuePair(PSWD_KEY, PASSWORD),
                new BasicNameValuePair(USERID_KEY, USER_ID))
        ));
    }

    @Test
    void shouldExtractParametersWithTwoLineStreetAddress() {
        mockCardAndAddressDetails();

        when(mockAddress.getLine1()).thenReturn(ADDRESS_LINE_1);
        when(mockAddress.getLine2()).thenReturn(ADDRESS_LINE_2);

        List<NameValuePair> result = epdqPayloadDefinitionForNewOrder.extract();

        assertThat(result, is(List.of(
                new BasicNameValuePair(AMOUNT_KEY, AMOUNT),
                new BasicNameValuePair(CARD_NO_KEY, CARD_NO),
                new BasicNameValuePair(CARDHOLDER_NAME_KEY, CARDHOLDER_NAME),
                new BasicNameValuePair(CURRENCY_KEY, CURRENCY),
                new BasicNameValuePair(CVC_KEY, CVC),
                new BasicNameValuePair(EXPIRY_DATE_KEY, END_DATE.toString()),
                new BasicNameValuePair(OPERATION_KEY, OPERATION_TYPE),
                new BasicNameValuePair(ORDER_ID_KEY, ORDER_ID),
                new BasicNameValuePair(OWNER_ADDRESS_KEY, ADDRESS_LINE_1 + ", " + ADDRESS_LINE_2),
                new BasicNameValuePair(OWNER_COUNTRY_CODE_KEY, COUNTRY_CODE),
                new BasicNameValuePair(OWNER_TOWN_KEY, CITY),
                new BasicNameValuePair(OWNER_ZIP_KEY, POSTCODE),
                new BasicNameValuePair(PSPID_KEY, PSP_ID),
                new BasicNameValuePair(PSWD_KEY, PASSWORD),
                new BasicNameValuePair(USERID_KEY, USER_ID))
        ));
    }

    @Test
    void shouldOmitAddressWhenInputAddressIsNotPresent() {
        when(mockAuthCardDetails.getCardNo()).thenReturn(CARD_NO);
        when(mockAuthCardDetails.getCvc()).thenReturn(CVC);
        when(mockAuthCardDetails.getEndDate()).thenReturn(END_DATE);
        when(mockAuthCardDetails.getCardHolder()).thenReturn(CARDHOLDER_NAME);

        when(mockAuthCardDetails.getAddress()).thenReturn(Optional.empty());

        List<NameValuePair> result = epdqPayloadDefinitionForNewOrder.extract();

        assertThat(result, is(List.of(
                new BasicNameValuePair(AMOUNT_KEY, AMOUNT),
                new BasicNameValuePair(CARD_NO_KEY, CARD_NO),
                new BasicNameValuePair(CARDHOLDER_NAME_KEY, CARDHOLDER_NAME),
                new BasicNameValuePair(CURRENCY_KEY, CURRENCY),
                new BasicNameValuePair(CVC_KEY, CVC),
                new BasicNameValuePair(EXPIRY_DATE_KEY, END_DATE.toString()),
                new BasicNameValuePair(OPERATION_KEY, OPERATION_TYPE),
                new BasicNameValuePair(ORDER_ID_KEY, ORDER_ID),
                new BasicNameValuePair(PSPID_KEY, PSP_ID),
                new BasicNameValuePair(PSWD_KEY, PASSWORD),
                new BasicNameValuePair(USERID_KEY, USER_ID))
        ));
    }

    private void mockCardAndAddressDetails() {
        when(mockAuthCardDetails.getCardNo()).thenReturn(CARD_NO);
        when(mockAuthCardDetails.getCvc()).thenReturn(CVC);
        when(mockAuthCardDetails.getEndDate()).thenReturn(END_DATE);
        when(mockAuthCardDetails.getCardHolder()).thenReturn(CARDHOLDER_NAME);

        when(mockAuthCardDetails.getAddress()).thenReturn(Optional.of(mockAddress));
        when(mockAddress.getCity()).thenReturn(CITY);
        when(mockAddress.getPostcode()).thenReturn(POSTCODE);
        when(mockAddress.getCountry()).thenReturn(COUNTRY_CODE);
    }
}
