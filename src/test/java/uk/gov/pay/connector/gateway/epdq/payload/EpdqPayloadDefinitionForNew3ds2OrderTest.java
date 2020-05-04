package uk.gov.pay.connector.gateway.epdq.payload;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.gateway.epdq.EpdqTemplateData;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqParameterBuilder.newParameterBuilder;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_COLOR_DEPTH;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_LANGUAGE;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_SCREEN_HEIGHT;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_SCREEN_WIDTH;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.BROWSER_TIMEZONE_OFFSET_MINS;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.DEFAULT_BROWSER_COLOR_DEPTH;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.DEFAULT_BROWSER_SCREEN_HEIGHT;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2Order.DEFAULT_BROWSER_SCREEN_WIDTH;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3ds2OrderTest.ParameterBuilder.aParameterBuilder;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.ACCEPTURL_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.DECLINEURL_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.EXCEPTIONURL_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.FLAG3D_KEY;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.HTTPACCEPT_URL;
import static uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder.HTTPUSER_AGENT_URL;
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
    private static final String PSP_ID = "PspId";
    private static final String PASSWORD = "password";
    private static final String USER_ID = "User";
    private static final String FRONTEND_URL = "http://www.frontend.example.com";
    private static final String ACCEPT_HEADER = "Test Accept Header";
    private static final String USER_AGENT_HEADER = "Test User Agent Header";
    private static final Clock BRITISH_SUMMER_TIME_OFFSET_CLOCK = Clock.fixed(Instant.parse("2020-05-06T10:10:10.100Z"), ZoneOffset.UTC);
    private static final Clock GREENWICH_MERIDIAN_TIME_OFFSET_CLOCK = Clock.fixed(Instant.parse("2020-01-01T10:10:10.100Z"), ZoneOffset.UTC);
    
    private EpdqPayloadDefinitionForNew3ds2Order epdqPayloadDefinitionFor3ds2NewOrder = 
            new EpdqPayloadDefinitionForNew3ds2Order(FRONTEND_URL, SupportedLanguage.ENGLISH, BRITISH_SUMMER_TIME_OFFSET_CLOCK);

    private EpdqTemplateData epdqTemplateData = new EpdqTemplateData();
    private AuthCardDetails authCardDetails = new AuthCardDetails();
    
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
        assertThat(result, is(aParameterBuilder()
                .withBrowserLanguage(Locale.forLanguageTag("de").toLanguageTag())
                .build()));
    }

    @Test
    @Parameters({"ENGLISH, en-GB", "WELSH, cy"})
    public void should_include_payment_browserLanguage_if_none_provided(SupportedLanguage language, String expectedBrowserLanguage) {
        var epdqPayloadDefinitionFor3ds2NewOrder = new EpdqPayloadDefinitionForNew3ds2Order(FRONTEND_URL, language, BRITISH_SUMMER_TIME_OFFSET_CLOCK);
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
        var epdqPayloadDefinitionFor3ds2NewOrder = new EpdqPayloadDefinitionForNew3ds2Order(FRONTEND_URL, SupportedLanguage.ENGLISH, BRITISH_SUMMER_TIME_OFFSET_CLOCK);
        authCardDetails.setJsTimezoneOffsetMins(timeZoneOffsetMins);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserTimezoneOffsetMins("-60").build()));
    }

    @Test
    @Parameters({"null", "-900", "800", "invalid", "0x6", "123L", "1.2"})
    public void should_include_default_browserTimezoneOffsetMins_for_Gmt_when_timezone_offset_provided_is_invalid
            (@Nullable String timeZoneOffsetMins) {
        var epdqPayloadDefinitionFor3ds2NewOrder = new EpdqPayloadDefinitionForNew3ds2Order(FRONTEND_URL, SupportedLanguage.ENGLISH, GREENWICH_MERIDIAN_TIME_OFFSET_CLOCK);
        authCardDetails.setJsTimezoneOffsetMins(timeZoneOffsetMins);
        List<NameValuePair> result = epdqPayloadDefinitionFor3ds2NewOrder.extract(epdqTemplateData);
        assertThat(result, is(aParameterBuilder().withBrowserTimezoneOffsetMins("0").build()));
    }
    
    static class ParameterBuilder {
        private String browserLanguage = "en-GB";
        private String browserScreenHeight = DEFAULT_BROWSER_SCREEN_HEIGHT;
        private String browserScreenWidth = DEFAULT_BROWSER_SCREEN_WIDTH;
        private String browserColorDepth = DEFAULT_BROWSER_COLOR_DEPTH;
        private String browserTimezoneOffsetMins = "-60";
        
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
                    .add(HTTPACCEPT_URL, ACCEPT_HEADER)
                    .add(HTTPUSER_AGENT_URL, USER_AGENT_HEADER)
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
                    .add(BROWSER_TIMEZONE_OFFSET_MINS, browserTimezoneOffsetMins);
            return epdqParameterBuilder.build();
        }
    }
}
