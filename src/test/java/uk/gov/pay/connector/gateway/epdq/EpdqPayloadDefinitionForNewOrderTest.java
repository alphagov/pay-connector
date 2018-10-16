package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
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

@RunWith(MockitoJUnitRunner.class)
public class EpdqPayloadDefinitionForNewOrderTest {

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

    @Mock
    private EpdqOrderRequestBuilder.EpdqTemplateData mockTemplateData;

    @Mock
    private AuthCardDetails mockAuthCardDetails;

    @Mock
    private Address mockAddress;

    private final EpdqPayloadDefinitionForNewOrder epdqPayloadDefinitionForNewOrder = new EpdqPayloadDefinitionForNewOrder();

    @Before
    public void setUp() {
        when(mockTemplateData.getMerchantCode()).thenReturn(PSP_ID);
        when(mockTemplateData.getPassword()).thenReturn(PASSWORD);
        when(mockTemplateData.getUserId()).thenReturn(USER_ID);
        when(mockTemplateData.getOperationType()).thenReturn(OPERATION_TYPE);
        when(mockTemplateData.getOrderId()).thenReturn(ORDER_ID);
        when(mockTemplateData.getAmount()).thenReturn(AMOUNT);

        when(mockTemplateData.getAuthCardDetails()).thenReturn(mockAuthCardDetails);
        when(mockAuthCardDetails.getCardNo()).thenReturn(CARD_NO);
        when(mockAuthCardDetails.getCvc()).thenReturn(CVC);
        when(mockAuthCardDetails.getEndDate()).thenReturn(END_DATE);
        when(mockAuthCardDetails.getCardHolder()).thenReturn(CARDHOLDER_NAME);

        when(mockAuthCardDetails.getAddress()).thenReturn(mockAddress);
        when(mockAddress.getCity()).thenReturn(CITY);
        when(mockAddress.getPostcode()).thenReturn(POSTCODE);
        when(mockAddress.getCountry()).thenReturn(COUNTRY_CODE);
    }

    @Test
    public void shouldExtractParametersFromTemplateWithOneLineStreetAddress() {
        when(mockAddress.getLine1()).thenReturn(ADDRESS_LINE_1);

        ImmutableList<NameValuePair> result = epdqPayloadDefinitionForNewOrder.extract(mockTemplateData);

        assertThat(result, is(ImmutableList.builder().add(
                new BasicNameValuePair(AMOUNT_KEY, AMOUNT),
                new BasicNameValuePair(CARD_NO_KEY, CARD_NO),
                new BasicNameValuePair(CARDHOLDER_NAME_KEY, CARDHOLDER_NAME),
                new BasicNameValuePair(CURRENCY_KEY, CURRENCY),
                new BasicNameValuePair(CVC_KEY, CVC),
                new BasicNameValuePair(EXPIRY_DATE_KEY, END_DATE),
                new BasicNameValuePair(OPERATION_KEY, OPERATION_TYPE),
                new BasicNameValuePair(ORDER_ID_KEY, ORDER_ID),
                new BasicNameValuePair(OWNER_ADDRESS_KEY, ADDRESS_LINE_1),
                new BasicNameValuePair(OWNER_COUNTRY_CODE_KEY, COUNTRY_CODE),
                new BasicNameValuePair(OWNER_TOWN_KEY, CITY),
                new BasicNameValuePair(OWNER_ZIP_KEY, POSTCODE),
                new BasicNameValuePair(PSPID_KEY, PSP_ID),
                new BasicNameValuePair(PSWD_KEY, PASSWORD),
                new BasicNameValuePair(USERID_KEY, USER_ID))
                .build()));
    }

    @Test
    public void shouldExtractParametersFromTemplateWithTwoLineStreetAddress() {
        when(mockAddress.getLine1()).thenReturn(ADDRESS_LINE_1);
        when(mockAddress.getLine2()).thenReturn(ADDRESS_LINE_2);

        ImmutableList<NameValuePair> result = epdqPayloadDefinitionForNewOrder.extract(mockTemplateData);

        assertThat(result, is(ImmutableList.builder().add(
                new BasicNameValuePair(AMOUNT_KEY, AMOUNT),
                new BasicNameValuePair(CARD_NO_KEY, CARD_NO),
                new BasicNameValuePair(CARDHOLDER_NAME_KEY, CARDHOLDER_NAME),
                new BasicNameValuePair(CURRENCY_KEY, CURRENCY),
                new BasicNameValuePair(CVC_KEY, CVC),
                new BasicNameValuePair(EXPIRY_DATE_KEY, END_DATE),
                new BasicNameValuePair(OPERATION_KEY, OPERATION_TYPE),
                new BasicNameValuePair(ORDER_ID_KEY, ORDER_ID),
                new BasicNameValuePair(OWNER_ADDRESS_KEY, ADDRESS_LINE_1 + ", " + ADDRESS_LINE_2),
                new BasicNameValuePair(OWNER_COUNTRY_CODE_KEY, COUNTRY_CODE),
                new BasicNameValuePair(OWNER_TOWN_KEY, CITY),
                new BasicNameValuePair(OWNER_ZIP_KEY, POSTCODE),
                new BasicNameValuePair(PSPID_KEY, PSP_ID),
                new BasicNameValuePair(PSWD_KEY, PASSWORD),
                new BasicNameValuePair(USERID_KEY, USER_ID))
                .build()));
    }

}
