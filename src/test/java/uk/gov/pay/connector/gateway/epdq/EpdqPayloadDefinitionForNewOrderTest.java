package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

import java.util.List;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.AMOUNT_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.BROWSER_COLOR_DEPTH;
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

    private EpdqTemplateData epdqTemplateData;
    private AuthCardDetails authCardDetails;
    private Address address;

    private final EpdqPayloadDefinitionForNewOrder epdqPayloadDefinitionForNewOrder = new EpdqPayloadDefinitionForNewOrder();

    @Before
    public void setUp() {
        epdqTemplateData = new EpdqTemplateData();
        authCardDetails = new AuthCardDetails();
        address = new Address();
        
        epdqTemplateData.setMerchantCode(PSP_ID);
        epdqTemplateData.setPassword(PASSWORD);
        epdqTemplateData.setUserId(USER_ID);
        epdqTemplateData.setOperationType(OPERATION_TYPE);
        epdqTemplateData.setOrderId(ORDER_ID);
        epdqTemplateData.setAmount(AMOUNT);
        epdqTemplateData.setAuthCardDetails(authCardDetails);
        
        authCardDetails.setCardNo(CARD_NO);
        authCardDetails.setCvc(CVC);
        authCardDetails.setEndDate(END_DATE);
        authCardDetails.setCardHolder(CARDHOLDER_NAME);
        authCardDetails.setAddress(address);
        
        address.setCity(CITY);
        address.setPostcode(POSTCODE);
        address.setCountry(COUNTRY_CODE);
    }
    
    @Test
    public void shouldExtract_js_screen_color_depth_ifPresent() {
        authCardDetails.setJsScreenColorDepth("1");
        List<NameValuePair> result = epdqPayloadDefinitionForNewOrder.extract(epdqTemplateData);
        assertThat(result, hasItem(new BasicNameValuePair(BROWSER_COLOR_DEPTH, "1")));
    }

    @Test
    public void shouldNotExtract_js_screen_color_depth_ifNotPresent() {
        List<NameValuePair> result = epdqPayloadDefinitionForNewOrder.extract(epdqTemplateData);
        assertEquals(0, result.stream().filter(nameValuePair -> nameValuePair.getName().equals(BROWSER_COLOR_DEPTH)).count());
    }

    @Test
    public void shouldExtractParametersFromTemplateWithOneLineStreetAddress() {
        address.setLine1(ADDRESS_LINE_1);
        List<NameValuePair> result = epdqPayloadDefinitionForNewOrder.extract(epdqTemplateData);
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
        address.setLine1(ADDRESS_LINE_1);
        address.setLine2(ADDRESS_LINE_2);
        List<NameValuePair> result = epdqPayloadDefinitionForNewOrder.extract(epdqTemplateData);
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

    @Test
    public void shouldOmitAddressWhenInputAddressIsNotPresent() {
        authCardDetails.setAddress(null);
        List<NameValuePair> result = epdqPayloadDefinitionForNewOrder.extract(epdqTemplateData);
        assertThat(result, is(ImmutableList.builder().add(
                new BasicNameValuePair(AMOUNT_KEY, AMOUNT),
                new BasicNameValuePair(CARD_NO_KEY, CARD_NO),
                new BasicNameValuePair(CARDHOLDER_NAME_KEY, CARDHOLDER_NAME),
                new BasicNameValuePair(CURRENCY_KEY, CURRENCY),
                new BasicNameValuePair(CVC_KEY, CVC),
                new BasicNameValuePair(EXPIRY_DATE_KEY, END_DATE),
                new BasicNameValuePair(OPERATION_KEY, OPERATION_TYPE),
                new BasicNameValuePair(ORDER_ID_KEY, ORDER_ID),
                new BasicNameValuePair(PSPID_KEY, PSP_ID),
                new BasicNameValuePair(PSWD_KEY, PASSWORD),
                new BasicNameValuePair(USERID_KEY, USER_ID))
                .build()));
    }
}
