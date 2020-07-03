package uk.gov.pay.connector.gateway.epdq;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.ACCEPTURL_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.DECLINEURL_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.EXCEPTIONURL_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.FLAG3D_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.HTTPACCEPT_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.HTTPUSER_AGENT_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.LANGUAGE_URL;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.WIN3DS_URL;
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
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_3DS_REQUEST;

@RunWith(MockitoJUnitRunner.class)
public class EpdqPayloadDefinitionForNew3dsOrderTest {

    private static final String OPERATION_TYPE = "RES";
    private static final String ORDER_ID = "OrderId";

    private static final String CARD_NO = "4242424242424242";
    private static final String CVC = "321";
    private static final String END_DATE = "01/18";

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

    private static final String FRONTEND_URL = "http://www.frontend.example.com";
    private static final String ACCEPT_HEADER = "Test Accept Header";
    private static final String USER_AGENT_HEADER = "Test User Agent Header";

    @Mock
    private AuthCardDetails mockAuthCardDetails;

    @Mock
    private Address mockAddress;

    private final EpdqPayloadDefinitionForNew3dsOrder epdqPayloadDefinitionFor3dsNewOrder = new EpdqPayloadDefinitionForNew3dsOrder(FRONTEND_URL);

    @Before
    public void setUp() {
        epdqPayloadDefinitionFor3dsNewOrder.setPspId(PSP_ID);
        epdqPayloadDefinitionFor3dsNewOrder.setPassword(PASSWORD);
        epdqPayloadDefinitionFor3dsNewOrder.setUserId(USER_ID);
        epdqPayloadDefinitionFor3dsNewOrder.setOrderId(ORDER_ID);
        epdqPayloadDefinitionFor3dsNewOrder.setAmount(AMOUNT);

        epdqPayloadDefinitionFor3dsNewOrder.setAuthCardDetails(mockAuthCardDetails);
        when(mockAuthCardDetails.getCardNo()).thenReturn(CARD_NO);
        when(mockAuthCardDetails.getCvc()).thenReturn(CVC);
        when(mockAuthCardDetails.getEndDate()).thenReturn(END_DATE);
        when(mockAuthCardDetails.getCardHolder()).thenReturn(CARDHOLDER_NAME);
        when(mockAuthCardDetails.getAcceptHeader()).thenReturn(ACCEPT_HEADER);
        when(mockAuthCardDetails.getUserAgentHeader()).thenReturn(USER_AGENT_HEADER);

        when(mockAuthCardDetails.getAddress()).thenReturn(Optional.of(mockAddress));
        when(mockAddress.getCity()).thenReturn(CITY);
        when(mockAddress.getPostcode()).thenReturn(POSTCODE);
        when(mockAddress.getCountry()).thenReturn(COUNTRY_CODE);
    }

    @Test
    public void assert_payload_and_order_request_type_are_as_expected() {
        epdqPayloadDefinitionFor3dsNewOrder.setOrderId("mq4ht90j2oir6am585afk58kml");
        epdqPayloadDefinitionFor3dsNewOrder.setPassword("password");
        epdqPayloadDefinitionFor3dsNewOrder.setUserId("username");
        epdqPayloadDefinitionFor3dsNewOrder.setPspId("merchant-id");
        epdqPayloadDefinitionFor3dsNewOrder.setAmount("500");
        epdqPayloadDefinitionFor3dsNewOrder.setAuthCardDetails(aValidEpdqAuthCardDetails());
        epdqPayloadDefinitionFor3dsNewOrder.setShaInPassphrase("sha-passphrase");
        GatewayOrder gatewayOrder = epdqPayloadDefinitionFor3dsNewOrder.createGatewayOrder();

        assertEquals(TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_3DS_REQUEST), gatewayOrder.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_3DS, gatewayOrder.getOrderRequestType());
    }

    private AuthCardDetails aValidEpdqAuthCardDetails() {
        Address address = new Address("41", "Scala Street", "EC2A 1AE", "London", "London", "GB");

        return AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("Mr. Payment")
                .withCardNo("5555444433331111")
                .withCvc("737")
                .withEndDate("08/18")
                .withCardBrand("visa")
                .withAddress(address)
                .build();
    }

    @Test
    public void shouldExtractParametersWithOneLineStreetAddress() {
        when(mockAddress.getLine1()).thenReturn(ADDRESS_LINE_1);

        List<NameValuePair> result = epdqPayloadDefinitionFor3dsNewOrder.extract();

        String expectedFrontend3dsIncomingUrl = "http://www.frontend.example.com/card_details/OrderId/3ds_required_in/epdq";

        assertThat(result, is(List.of(
                new BasicNameValuePair(ACCEPTURL_KEY, expectedFrontend3dsIncomingUrl),
                new BasicNameValuePair(AMOUNT_KEY, AMOUNT),
                new BasicNameValuePair(CARD_NO_KEY, CARD_NO),
                new BasicNameValuePair(CARDHOLDER_NAME_KEY, CARDHOLDER_NAME),
                new BasicNameValuePair(CURRENCY_KEY, CURRENCY),
                new BasicNameValuePair(CVC_KEY, CVC),
                new BasicNameValuePair(DECLINEURL_KEY, expectedFrontend3dsIncomingUrl + "?status=declined"),
                new BasicNameValuePair(EXPIRY_DATE_KEY, END_DATE),
                new BasicNameValuePair(EXCEPTIONURL_KEY, expectedFrontend3dsIncomingUrl + "?status=error"),
                new BasicNameValuePair(FLAG3D_KEY, "Y"),
                new BasicNameValuePair(HTTPACCEPT_KEY, ACCEPT_HEADER),
                new BasicNameValuePair(HTTPUSER_AGENT_KEY, USER_AGENT_HEADER),
                new BasicNameValuePair(LANGUAGE_URL, "en_GB"),
                new BasicNameValuePair(OPERATION_KEY, OPERATION_TYPE),
                new BasicNameValuePair(ORDER_ID_KEY, ORDER_ID),
                new BasicNameValuePair(OWNER_ADDRESS_KEY, ADDRESS_LINE_1),
                new BasicNameValuePair(OWNER_COUNTRY_CODE_KEY, COUNTRY_CODE),
                new BasicNameValuePair(OWNER_TOWN_KEY, CITY),
                new BasicNameValuePair(OWNER_ZIP_KEY, POSTCODE),
                new BasicNameValuePair(PSPID_KEY, PSP_ID),
                new BasicNameValuePair(PSWD_KEY, PASSWORD),
                new BasicNameValuePair(USERID_KEY, USER_ID),
                new BasicNameValuePair(WIN3DS_URL, "MAINW"))
                ));
    }

    @Test
    public void shouldExtractParametersWithTwoLineStreetAddress() {
        when(mockAddress.getLine1()).thenReturn(ADDRESS_LINE_1);
        when(mockAddress.getLine2()).thenReturn(ADDRESS_LINE_2);

        List<NameValuePair> result = epdqPayloadDefinitionFor3dsNewOrder.extract();

        String expectedFrontend3dsIncomingUrl = "http://www.frontend.example.com/card_details/OrderId/3ds_required_in/epdq";

        assertThat(result, is(List.of(
                new BasicNameValuePair(ACCEPTURL_KEY, expectedFrontend3dsIncomingUrl),
                new BasicNameValuePair(AMOUNT_KEY, AMOUNT),
                new BasicNameValuePair(CARD_NO_KEY, CARD_NO),
                new BasicNameValuePair(CARDHOLDER_NAME_KEY, CARDHOLDER_NAME),
                new BasicNameValuePair(CURRENCY_KEY, CURRENCY),
                new BasicNameValuePair(CVC_KEY, CVC),
                new BasicNameValuePair(DECLINEURL_KEY, expectedFrontend3dsIncomingUrl + "?status=declined"),
                new BasicNameValuePair(EXPIRY_DATE_KEY, END_DATE),
                new BasicNameValuePair(EXCEPTIONURL_KEY, expectedFrontend3dsIncomingUrl + "?status=error"),
                new BasicNameValuePair(FLAG3D_KEY, "Y"),
                new BasicNameValuePair(HTTPACCEPT_KEY, ACCEPT_HEADER),
                new BasicNameValuePair(HTTPUSER_AGENT_KEY, USER_AGENT_HEADER),
                new BasicNameValuePair(LANGUAGE_URL, "en_GB"),
                new BasicNameValuePair(OPERATION_KEY, OPERATION_TYPE),
                new BasicNameValuePair(ORDER_ID_KEY, ORDER_ID),
                new BasicNameValuePair(OWNER_ADDRESS_KEY, ADDRESS_LINE_1 + ", " + ADDRESS_LINE_2),
                new BasicNameValuePair(OWNER_COUNTRY_CODE_KEY, COUNTRY_CODE),
                new BasicNameValuePair(OWNER_TOWN_KEY, CITY),
                new BasicNameValuePair(OWNER_ZIP_KEY, POSTCODE),
                new BasicNameValuePair(PSPID_KEY, PSP_ID),
                new BasicNameValuePair(PSWD_KEY, PASSWORD),
                new BasicNameValuePair(USERID_KEY, USER_ID),
                new BasicNameValuePair(WIN3DS_URL, "MAINW"))
                ));
    }

    @Test
    public void shouldOmitAddressWhenInputAddressIsNotPresent() {
        when(mockAuthCardDetails.getAddress()).thenReturn(Optional.empty());

        List<NameValuePair> result = epdqPayloadDefinitionFor3dsNewOrder.extract();

        String expectedFrontend3dsIncomingUrl = "http://www.frontend.example.com/card_details/OrderId/3ds_required_in/epdq";

        assertThat(result, is(List.of(
                new BasicNameValuePair(ACCEPTURL_KEY, expectedFrontend3dsIncomingUrl),
                new BasicNameValuePair(AMOUNT_KEY, AMOUNT),
                new BasicNameValuePair(CARD_NO_KEY, CARD_NO),
                new BasicNameValuePair(CARDHOLDER_NAME_KEY, CARDHOLDER_NAME),
                new BasicNameValuePair(CURRENCY_KEY, CURRENCY),
                new BasicNameValuePair(CVC_KEY, CVC),
                new BasicNameValuePair(DECLINEURL_KEY, expectedFrontend3dsIncomingUrl + "?status=declined"),
                new BasicNameValuePair(EXPIRY_DATE_KEY, END_DATE),
                new BasicNameValuePair(EXCEPTIONURL_KEY, expectedFrontend3dsIncomingUrl + "?status=error"),
                new BasicNameValuePair(FLAG3D_KEY, "Y"),
                new BasicNameValuePair(HTTPACCEPT_KEY, ACCEPT_HEADER),
                new BasicNameValuePair(HTTPUSER_AGENT_KEY, USER_AGENT_HEADER),
                new BasicNameValuePair(LANGUAGE_URL, "en_GB"),
                new BasicNameValuePair(OPERATION_KEY, OPERATION_TYPE),
                new BasicNameValuePair(ORDER_ID_KEY, ORDER_ID),
                new BasicNameValuePair(PSPID_KEY, PSP_ID),
                new BasicNameValuePair(PSWD_KEY, PASSWORD),
                new BasicNameValuePair(USERID_KEY, USER_ID),
                new BasicNameValuePair(WIN3DS_URL, "MAINW"))
                ));
    }
}
