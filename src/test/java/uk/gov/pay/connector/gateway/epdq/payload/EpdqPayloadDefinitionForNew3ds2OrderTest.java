package uk.gov.pay.connector.gateway.epdq.payload;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.epdq.EpdqTemplateData;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqParameterBuilder.newParameterBuilder;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_ACCEPT_HEADER;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_ACCEPT_MAX_LENGTH;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_COLOR_DEPTH;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_JAVA_ENABLED;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_LANGUAGE;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_SCREEN_HEIGHT;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_SCREEN_WIDTH;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_TIMEZONE;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_USER_AGENT;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_USER_AGENT_MAX_LENGTH;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.DEFAULT_BROWSER_ACCEPT_HEADER;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.DEFAULT_BROWSER_COLOR_DEPTH;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.DEFAULT_BROWSER_SCREEN_HEIGHT;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.DEFAULT_BROWSER_SCREEN_WIDTH;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2OrderTest.ParameterBuilder.aParameterBuilder;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.ACCEPTURL_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.DECLINEURL_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.DEFAULT_BROWSER_USER_AGENT;
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
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.PSPID_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.PSWD_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder.USERID_KEY;
import static uk.gov.pay.connector.util.NameValuePairWithNameMatcher.containsNameValuePairWithName;

@RunWith(JUnitParamsRunner.class)
public class EpdqPayloadDefinitionForNew3ds2OrderTest {

    private static final String OPERATION_TYPE = "RES";
    private static final String ORDER_ID = "OrderId";
    private static final String CARD_NO = "4242424242424242";
    private static final String CVC = "321";
    private static final String END_DATE = "01/18";
    private static final String AMOUNT = "500";
    private static final String CURRENCY = "GBP";
    private static final String CARDHOLDER_NAME = "Ms Making A Payment";
    private static final String ADDRESS_LINE_1 = "The Money Pool";
    private static final String ADDRESS_LINE_2 = "1 Gold Way";
    private static final String ADDRESS_CITY = "London";
    private static final String ADDRESS_POSTCODE = "DO11 4RS";
    private static final String ADDRESS_COUNTRY = "GB";
    private static final String IP_ADDRESS = "8.8.8.8";
    private static final String PSP_ID = "PspId";
    private static final String PASSWORD = "password";
    private static final String USER_ID = "User";
    private static final String FRONTEND_URL = "http://www.frontend.example.com";
    private static final String ACCEPT_HEADER = "image/*";
    private static final String USER_AGENT_HEADER = "Chrome/9.0";
    private static final boolean SEND_PAYER_IP_ADDRESS_TO_GATEWAY = false;
    private static final Clock BRITISH_SUMMER_TIME_OFFSET_CLOCK = Clock.fixed(Instant.parse("2020-05-06T10:10:10.100Z"), ZoneOffset.UTC);
    private static final Clock GREENWICH_MERIDIAN_TIME_OFFSET_CLOCK = Clock.fixed(Instant.parse("2020-01-01T10:10:10.100Z"), ZoneOffset.UTC);
    
    private EpdqPayloadDefinitionForNew3ds2Order epdqPayloadDefinitionFor3ds2NewOrder = 
            new EpdqPayloadDefinitionForNew3ds2Order(FRONTEND_URL, SEND_PAYER_IP_ADDRESS_TO_GATEWAY, SupportedLanguage.ENGLISH, BRITISH_SUMMER_TIME_OFFSET_CLOCK);

    private EpdqTemplateData epdqTemplateData = new EpdqTemplateData();
    private AuthCardDetails authCardDetails = new AuthCardDetails();
    private Address address = new Address();
    
    @Before
    public void setup() {
        epdqTemplateData.setMerchantCode(PSP_ID);
        epdqTemplateData.setPassword(PASSWORD);
        epdqTemplateData.setUserId(USER_ID);
        epdqTemplateData.setOperationType(OPERATION_TYPE);
        epdqTemplateData.setOrderId(ORDER_ID);
        epdqTemplateData.setAmount(AMOUNT);
        epdqTemplateData.setFrontendUrl(FRONTEND_URL);
        epdqTemplateData.setAuthCardDetails(authCardDetails);
        
        authCardDetails.setCardNo(CARD_NO);
        authCardDetails.setCvc(CVC);
        authCardDetails.setEndDate(END_DATE);
        authCardDetails.setCardHolder(CARDHOLDER_NAME);
        authCardDetails.setAcceptHeader(ACCEPT_HEADER);
        authCardDetails.setUserAgentHeader(USER_AGENT_HEADER);
    }
    
    @After
    public void tearDown() {
        address.setCity(null);
        address.setCountry(null);
        address.setLine1(null);
        address.setLine2(null);
        address.setPostcode(null);
        authCardDetails.setIpAddress(null);
    }

    @Test
    @Parameters({"0", "999999", "100"})
    public void should_include_browserScreenHeight(String screenHeight) {
        authCardDetails.setJsScreenHeight(screenHeight);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserScreenHeight(screenHeight).build()));
    }
    
    @Test
    @Parameters({"null", "-1", "1000000", "invalid", "0x6", "123L", "1.2"})
    public void should_include_default_browserScreenHeight_when_screen_height_provided_is_invalid
            (@Nullable String screenHeight) {
        authCardDetails.setJsScreenHeight(screenHeight);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().build()));
    }

    @Test
    @Parameters({"0", "999999", "100"})
    public void should_include_browserScreenWidth(String screenWidth) {
        authCardDetails.setJsScreenWidth(screenWidth);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserScreenWidth(screenWidth).build()));
    }

    @Test
    @Parameters({"null", "-1", "1000000", "invalid", "0x6", "123L", "1.2"})
    public void should_include_default_browserScreenWidth_when_screen_width_provided_is_invalid
            (@Nullable String screenWidth) {
        authCardDetails.setJsScreenWidth(screenWidth);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().build()));
    }
    
    @Test
    public void should_include_browserLanguage() {
        authCardDetails.setJsNavigatorLanguage("de");
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserLanguage("de").build()));
    }

    @Test
    @Parameters({"ENGLISH, en-GB", "WELSH, cy"})
    public void should_include_payment_browserLanguage_if_none_provided(SupportedLanguage language, String expectedBrowserLanguage) {
        var epdqPayloadDefinitionFor3ds2NewOrder = new EpdqPayloadDefinitionForNew3ds2Order(FRONTEND_URL, SEND_PAYER_IP_ADDRESS_TO_GATEWAY, language,
                BRITISH_SUMMER_TIME_OFFSET_CLOCK);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserLanguage(expectedBrowserLanguage).build()));
    }

    @Test
    @Parameters({"ENGLISH, en-GB", "WELSH, cy"})
    public void should_include_payment_browserLanguage_when_provided_browser_language_too_long(SupportedLanguage language, String expectedBrowserLanguage) {
        var epdqPayloadDefinitionFor3ds2NewOrder = new EpdqPayloadDefinitionForNew3ds2Order(FRONTEND_URL, SEND_PAYER_IP_ADDRESS_TO_GATEWAY, language,
                BRITISH_SUMMER_TIME_OFFSET_CLOCK);
        authCardDetails.setJsNavigatorLanguage("x-this-is-too-long");
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserLanguage(expectedBrowserLanguage).build()));
    }

    @Test
    @Parameters({"1", "2", "4", "8", "15", "16", "24", "32"})
    public void should_include_accepted_browserColorDepths(String colorDepthValue) {
        authCardDetails.setJsScreenColorDepth(colorDepthValue);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, hasItem(new BasicNameValuePair(BROWSER_COLOR_DEPTH, colorDepthValue)));
    }
    
    @Test
    public void browserColorDepth_should_default_to_24_if_js_screen_color_depth_not_provided() {
        authCardDetails.setJsScreenColorDepth(null);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, hasItem(new BasicNameValuePair(BROWSER_COLOR_DEPTH, "24")));
    }

    @Test
    public void browserColorDepth_should_default_to_24_if_js_screen_color_depth_is_not_in_the_range_of_accepted_values() {
        authCardDetails.setJsScreenColorDepth("100");
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, hasItem(new BasicNameValuePair(BROWSER_COLOR_DEPTH, "24")));
    }

    @Test
    public void should_include_browserColorDepth() {
        authCardDetails.setJsScreenColorDepth("1");
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserColorDepth("1").build()));
    }

    @Test
    @Parameters({"-840", "720", "500"})
    public void should_include_browserTimezoneOffSetMins(String timeZoneOffsetMins) {
        authCardDetails.setJsTimezoneOffsetMins(timeZoneOffsetMins);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserTimezoneOffsetMins(timeZoneOffsetMins).build()));
    }

    @Test
    @Parameters({"null", "-900", "800", "invalid", "0x6", "123L", "1.2"})
    public void should_include_default_browserTimezoneOffsetMins_for_summer_time_when_timezone_offset_provided_is_invalid
            (@Nullable String timeZoneOffsetMins) {
        var epdqPayloadDefinitionFor3ds2NewOrder = new EpdqPayloadDefinitionForNew3ds2Order(FRONTEND_URL, SEND_PAYER_IP_ADDRESS_TO_GATEWAY, SupportedLanguage.ENGLISH, BRITISH_SUMMER_TIME_OFFSET_CLOCK);
        authCardDetails.setJsTimezoneOffsetMins(timeZoneOffsetMins);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserTimezoneOffsetMins("-60").build()));
    }

    @Test
    @Parameters({"null", "-900", "800", "invalid", "0x6", "123L", "1.2"})
    public void should_include_default_browserTimezoneOffsetMins_for_Gmt_when_timezone_offset_provided_is_invalid
            (@Nullable String timeZoneOffsetMins) {
        var epdqPayloadDefinitionFor3ds2NewOrder = new EpdqPayloadDefinitionForNew3ds2Order(FRONTEND_URL, SEND_PAYER_IP_ADDRESS_TO_GATEWAY, SupportedLanguage.ENGLISH, GREENWICH_MERIDIAN_TIME_OFFSET_CLOCK);
        authCardDetails.setJsTimezoneOffsetMins(timeZoneOffsetMins);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserTimezoneOffsetMins("0").build()));
    }

    @Test
    public void should_include_accepted_browserAcceptHeader() {
        authCardDetails.setAcceptHeader("text/html");
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserAcceptHeader("text/html").build()));
    }
    
    @Test
    @Parameters({"null", ""})
    public void browserAcceptHeader_should_include_default_if_browser_accept_header_not_provided(@Nullable String browserAcceptHeader) {
        authCardDetails.setAcceptHeader(browserAcceptHeader);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserAcceptHeader(DEFAULT_BROWSER_ACCEPT_HEADER).build()));
    }

    @Test
    public void browserAcceptHeader_should_include_default_when_browser_accept_header_too_long() {
        var veryLongAcceptHeader = randomAlphanumeric(BROWSER_ACCEPT_MAX_LENGTH + 1);
        authCardDetails.setAcceptHeader(veryLongAcceptHeader);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserAcceptHeader(DEFAULT_BROWSER_ACCEPT_HEADER).build()));
    }

    @Test
    public void should_include_provided_userAgentHeader() {
        authCardDetails.setUserAgentHeader("Opera/9.8");
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserUserAgent("Opera/9.8").build()));
    }
    
    @Test
    @Parameters({"null", ""})
    public void browserUserAgentHeader_should_include_default_if_user_agent_header_not_provided(@Nullable String browserAgentHeader) {
        authCardDetails.setUserAgentHeader(browserAgentHeader);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserUserAgent(DEFAULT_BROWSER_USER_AGENT).build()));
    }

    @Test
    public void browserUserAgentHeader_should_include_default_when_user_agent_header_too_long() {
        var veryLongUserAgentHeader = randomAlphanumeric(BROWSER_USER_AGENT_MAX_LENGTH + 1);
        authCardDetails.setUserAgentHeader(veryLongUserAgentHeader);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserUserAgent(DEFAULT_BROWSER_USER_AGENT).build()));
    }

    @Test
    public void should_include_browserJavaEnabled_parameter() {
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserJavaEnabled("false").build()));
    }
    
    @Test
    public void should_include_ECOM_BILLTO_POSTAL_CITY_if_city_provided() {
        address.setCity(ADDRESS_CITY);
        authCardDetails.setAddress(address);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, hasItem(new BasicNameValuePair(EpdqPayloadDefinitionForNew3ds2Order.ECOM_BILLTO_POSTAL_CITY, "London")));
    }

    @Test
    public void should_not_include_ECOM_BILLTO_POSTAL_CITY_if_city_not_provided() {
        authCardDetails.setAddress(address);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, not(containsNameValuePairWithName(EpdqPayloadDefinitionForNew3ds2Order.ECOM_BILLTO_POSTAL_CITY)));
    }

    @Test
    public void should_include_ECOM_ECOM_BILLTO_POSTAL_COUNTRYCODE_if_country_provided() {
        address.setCountry(ADDRESS_COUNTRY);
        authCardDetails.setAddress(address);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, hasItem(new BasicNameValuePair(EpdqPayloadDefinitionForNew3ds2Order.ECOM_BILLTO_POSTAL_COUNTRYCODE, "GB")));
    }

    @Test
    public void should_not_include_ECOM_BILLTO_POSTAL_COUNTRYCODE_if_country_not_provided() {
        authCardDetails.setAddress(address);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, not(containsNameValuePairWithName(EpdqPayloadDefinitionForNew3ds2Order.ECOM_BILLTO_POSTAL_COUNTRYCODE)));
    }

    @Test
    public void should_include_ECOM_BILLTO_POSTAL_STREET_LINE1_if_address_line1_provided() {
        address.setLine1(ADDRESS_LINE_1);
        authCardDetails.setAddress(address);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, hasItem(new BasicNameValuePair(EpdqPayloadDefinitionForNew3ds2Order.ECOM_BILLTO_POSTAL_STREET_LINE1, "The Money Pool")));
    }

    @Test
    public void should_not_include_ECOM_BILLTO_POSTAL_STREET_LINE1_if_address_line1_not_provided() {
        authCardDetails.setAddress(address);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, not(containsNameValuePairWithName(EpdqPayloadDefinitionForNew3ds2Order.ECOM_BILLTO_POSTAL_STREET_LINE1)));
    }

    @Test
    public void should_include_ECOM_ECOM_BILLTO_POSTAL_STREET_LINE_2_if_address_line2_provided() {
        address.setLine2(ADDRESS_LINE_2);
        authCardDetails.setAddress(address);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, hasItem(new BasicNameValuePair(EpdqPayloadDefinitionForNew3ds2Order.ECOM_BILLTO_POSTAL_STREET_LINE2, "1 Gold Way")));
    }

    @Test
    public void should_not_include_ECOM_ECOM_BILLTO_POSTAL_STREET_LINE_2_if_address_line2_not_provided() {
        authCardDetails.setAddress(address);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, not(containsNameValuePairWithName(EpdqPayloadDefinitionForNew3ds2Order.ECOM_BILLTO_POSTAL_STREET_LINE2)));
    }
    
    @Test
    public void should_include_ECOM_BILLTO_POSTAL_POSTALCODE_if_address_postcode_provided() {
        address.setPostcode(ADDRESS_POSTCODE);
        authCardDetails.setAddress(address);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, hasItem(new BasicNameValuePair(EpdqPayloadDefinitionForNew3ds2Order.ECOM_BILLTO_POSTAL_POSTALCODE, "DO11 4RS")));
    }

    @Test
    public void should_not_include_ECOM_BILLTO_POSTAL_POSTALCODE_if_address_postcode_not_provided() {
        authCardDetails.setAddress(address);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, not(containsNameValuePairWithName(EpdqPayloadDefinitionForNew3ds2Order.ECOM_BILLTO_POSTAL_POSTALCODE)));
    }

    @Test
    public void should_include_REMOTE_ADDR_if_Ip_address_provided_and_sending_Ip_enabled_on_gateway() {
        var epdqPayloadDefinitionFor3ds2NewOrder = new EpdqPayloadDefinitionForNew3ds2Order(FRONTEND_URL, true, SupportedLanguage.ENGLISH, GREENWICH_MERIDIAN_TIME_OFFSET_CLOCK);
        authCardDetails.setIpAddress(IP_ADDRESS);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, hasItem(new BasicNameValuePair(EpdqPayloadDefinitionForNew3ds2Order.REMOTE_ADDR, "8.8.8.8")));
    }

    @Test
    public void should_not_include_REMOTE_ADDR_if_Ip_address_not_provided_and_sending_Ip_enabled_on_gateway() {
        var epdqPayloadDefinitionFor3ds2NewOrder = new EpdqPayloadDefinitionForNew3ds2Order(FRONTEND_URL, true, SupportedLanguage.ENGLISH, GREENWICH_MERIDIAN_TIME_OFFSET_CLOCK);
        authCardDetails.setIpAddress(null);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, not(containsNameValuePairWithName(EpdqPayloadDefinitionForNew3ds2Order.REMOTE_ADDR)));
    }

    @Test
    public void should_not_include_REMOTE_ADDR_if_Ip_address_provided_and_sending_Ip_not_enabled_on_gateway() {
        var epdqPayloadDefinitionFor3ds2NewOrder = new EpdqPayloadDefinitionForNew3ds2Order(FRONTEND_URL, SEND_PAYER_IP_ADDRESS_TO_GATEWAY, SupportedLanguage.ENGLISH, GREENWICH_MERIDIAN_TIME_OFFSET_CLOCK);
        authCardDetails.setIpAddress(IP_ADDRESS);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, not(containsNameValuePairWithName(EpdqPayloadDefinitionForNew3ds2Order.REMOTE_ADDR)));
    }
    
    static class ParameterBuilder {
        private String browserLanguage = "en-GB";
        private String browserScreenHeight = DEFAULT_BROWSER_SCREEN_HEIGHT;
        private String browserScreenWidth = DEFAULT_BROWSER_SCREEN_WIDTH;
        private String browserColorDepth = DEFAULT_BROWSER_COLOR_DEPTH;
        private String browserTimezoneOffsetMins = "-60";
        private String browserAcceptHeader = ACCEPT_HEADER;
        private String browserUserAgent = USER_AGENT_HEADER;
        private String browserJavaEnabled = "false";
        
        public static ParameterBuilder aParameterBuilder() {
            return new ParameterBuilder();
        }

        public ParameterBuilder withBrowserLanguage(String browserLanguage) {
            this.browserLanguage = browserLanguage;
            return this;
        }

        public ParameterBuilder withBrowserColorDepth(String browserColorDepth) {
            this.browserColorDepth = browserColorDepth;
            return this;
        }

        public ParameterBuilder withBrowserScreenHeight(String browserScreenHeight) {
            this.browserScreenHeight = browserScreenHeight;
            return this;
        }

        public ParameterBuilder withBrowserScreenWidth(String browserScreenWidth) {
            this.browserScreenWidth = browserScreenWidth;
            return this;
        }
        
        public ParameterBuilder withBrowserTimezoneOffsetMins(String browserTimezoneOffsetMins) {
            this.browserTimezoneOffsetMins = browserTimezoneOffsetMins;
            return this;
        }

        public ParameterBuilder withBrowserUserAgent(String browserUserAgent) {
            this.browserUserAgent = browserUserAgent;
            return this;
        }
        
        public ParameterBuilder withBrowserAcceptHeader(String browserAcceptHeader) {
            this.browserAcceptHeader = browserAcceptHeader;
            return this;
        }

        public ParameterBuilder withBrowserJavaEnabled(String browserJavaEnabled) {
            this.browserJavaEnabled = browserJavaEnabled;
            return this;
        }
        
        public List<NameValuePair> build() {
            String expectedFrontend3dsIncomingUrl = "http://www.frontend.example.com/card_details/OrderId/3ds_required_in/epdq";
            EpdqParameterBuilder epdqParameterBuilder = newParameterBuilder()
                    .add(ACCEPTURL_KEY, expectedFrontend3dsIncomingUrl)
                    .add(AMOUNT_KEY, AMOUNT)
                    .add(CARD_NO_KEY, CARD_NO)
                    .add(CARDHOLDER_NAME_KEY, CARDHOLDER_NAME)
                    .add(CURRENCY_KEY, CURRENCY)
                    .add(CVC_KEY, CVC)
                    .add(DECLINEURL_KEY, expectedFrontend3dsIncomingUrl + "?status=declined")
                    .add(EXPIRY_DATE_KEY, END_DATE)
                    .add(EXCEPTIONURL_KEY, expectedFrontend3dsIncomingUrl + "?status=error")
                    .add(FLAG3D_KEY, "Y")
                    .add(HTTPACCEPT_KEY, browserAcceptHeader)
                    .add(HTTPUSER_AGENT_KEY, browserUserAgent)
                    .add(LANGUAGE_URL, "en_GB")
                    .add(OPERATION_KEY, OPERATION_TYPE)
                    .add(ORDER_ID_KEY, ORDER_ID)
                    .add(PSPID_KEY, PSP_ID)
                    .add(PSWD_KEY, PASSWORD)
                    .add(USERID_KEY, USER_ID)
                    .add(WIN3DS_URL, "MAINW")
                    .add(BROWSER_COLOR_DEPTH, browserColorDepth)
                    .add(BROWSER_LANGUAGE, browserLanguage)
                    .add(BROWSER_SCREEN_HEIGHT, browserScreenHeight)
                    .add(BROWSER_SCREEN_WIDTH, browserScreenWidth)
                    .add(BROWSER_TIMEZONE, browserTimezoneOffsetMins)
                    .add(BROWSER_ACCEPT_HEADER, browserAcceptHeader)
                    .add(BROWSER_USER_AGENT, browserUserAgent)
                    .add(BROWSER_JAVA_ENABLED, browserJavaEnabled);
            return epdqParameterBuilder.build();
        }
    }
}
