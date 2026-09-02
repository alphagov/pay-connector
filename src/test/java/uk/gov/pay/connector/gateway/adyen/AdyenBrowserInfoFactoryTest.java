package uk.gov.pay.connector.gateway.adyen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenBrowserInfo;
import uk.gov.pay.connector.gateway.model.BrowserDataFor3ds;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdyenBrowserInfoFactoryTest {
    @Mock
    private BrowserDataFor3ds mockBrowserDataFor3ds;

    private final AdyenBrowserInfoFactory adyenBrowserInfoFactory = new AdyenBrowserInfoFactory();

    @Test
    void create_AcceptHeaderHasValue_ReturnsValue() {
        var expectedValue = "some string";
        given(mockBrowserDataFor3ds.getBrowserAcceptHeader()).willReturn(Optional.of(expectedValue));
        
        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);
        
        assertThat(result.acceptHeader(), is(expectedValue));
    }

    @Test
    void create_AcceptHeaderHasNoValue_ReturnsDefault() {
        var expectedValue = "text/html";
        given(mockBrowserDataFor3ds.getBrowserAcceptHeader()).willReturn(Optional.empty());

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.acceptHeader(), is(expectedValue));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "4", "8", "15", "16", "24", "32", "48"})
    void create_BrowserColourDepthHasValue_ReturnsValue(String colourDepth) {
        var expectedValue = Integer.parseInt(colourDepth);
        given(mockBrowserDataFor3ds.getBrowserColorDepth()).willReturn(Optional.of(colourDepth));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.colorDepth(), is(expectedValue));
    }

    @ParameterizedTest
    @CsvSource({"2,1", "5,4", "9,8", "17,16", "25,24", "33,32", "49,48", "0,1", "-1,1"})
    void create_BrowserColourDepthHasInvalidInt_ReturnsLowerBoundValue(String colourDepth, int expectedColourDepth) {
        given(mockBrowserDataFor3ds.getBrowserColorDepth()).willReturn(Optional.of(colourDepth));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.colorDepth(), is(expectedColourDepth));
    }

    @Test
    void create_BrowserColourDepthHasNoValue_ReturnsDefault() {
        var expectedValue = 1;
        given(mockBrowserDataFor3ds.getBrowserColorDepth()).willReturn(Optional.empty());

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.colorDepth(), is(expectedValue));
    }

    @Test
    void create_BrowserColourDepthHasInvalidValue_ReturnsDefault() {
        var expectedValue = 1;
        given(mockBrowserDataFor3ds.getBrowserColorDepth()).willReturn(Optional.of("snoopy"));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.colorDepth(), is(expectedValue));
    }

    @Test
    void create_JavaEnabled_ReturnsFalse() {
        var expectedValue = false;

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.javaEnabled(), is(expectedValue));
    }

    @Test
    void create_JavaScriptEnabledHasValue_ReturnsValue() {
        var expectedValue = true;
        given(mockBrowserDataFor3ds.getBrowserJavaScriptEnabled()).willReturn(Optional.of(true));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.javaScriptEnabled(), is(expectedValue));
    }

    @Test
    void create_JavaScriptEnabledHasNoValue_ReturnsFalse() {
        var expectedValue = false;
        given(mockBrowserDataFor3ds.getBrowserJavaScriptEnabled()).willReturn(Optional.empty());

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.javaScriptEnabled(), is(expectedValue));
    }
    
    @Test
    void create_BrowserLanguageHasValue_ReturnsValue() {
        var expectedValue = "fr-FR";
        given(mockBrowserDataFor3ds.getBrowserLanguage()).willReturn(Optional.of(expectedValue));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.language(), is(expectedValue));
    }

    @Test
    void create_BrowserLanguageHasNoValue_ReturnsDefault() {
        given(mockBrowserDataFor3ds.getBrowserLanguage()).willReturn(Optional.empty());

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.language(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_LANGUAGE));
    }

    @Test
    void create_BrowserLanguageHasInvalidValue_ReturnsDefault() {
        given(mockBrowserDataFor3ds.getBrowserLanguage()).willReturn(Optional.of("some fake language"));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.language(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_LANGUAGE));
    }

    @Test
    void create_BrowserHeightHasValue_ReturnsValue() {
        given(mockBrowserDataFor3ds.getBrowserScreenHeight()).willReturn(Optional.of("" + AdyenBrowserInfoFactory.DEFAULT_BROWSER_SCREEN_HEIGHT));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.screenHeight(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_SCREEN_HEIGHT));
    }

    @Test
    void create_BrowserHeightHasNoValue_ReturnsDefault() {
        given(mockBrowserDataFor3ds.getBrowserScreenHeight()).willReturn(Optional.empty());

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.screenHeight(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_SCREEN_HEIGHT));
    }

    @Test
    void create_BrowserHeightHasInvalidValue_ReturnsDefault() {
        given(mockBrowserDataFor3ds.getBrowserScreenHeight()).willReturn(Optional.of("hello"));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.screenHeight(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_SCREEN_HEIGHT));
    }

    @Test
    void create_BrowserHeightHasNegativeValue_ReturnsDefault() {
        given(mockBrowserDataFor3ds.getBrowserScreenHeight()).willReturn(Optional.of("-1"));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.screenHeight(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_SCREEN_HEIGHT));
    }

    @Test
    void create_BrowserWidthHasValue_ReturnsValue() {
        given(mockBrowserDataFor3ds.getBrowserScreenWidth()).willReturn(Optional.of("" + AdyenBrowserInfoFactory.DEFAULT_BROWSER_SCREEN_WIDTH));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.screenWidth(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_SCREEN_WIDTH));
    }

    @Test
    void create_BrowserWidthHasNoValue_ReturnsDefault() {
        given(mockBrowserDataFor3ds.getBrowserScreenWidth()).willReturn(Optional.empty());

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.screenWidth(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_SCREEN_HEIGHT));
    }

    @Test
    void create_BrowserWidthHasInvalidValue_ReturnsDefault() {
        given(mockBrowserDataFor3ds.getBrowserScreenWidth()).willReturn(Optional.of("hello"));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.screenWidth(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_SCREEN_WIDTH));
    }

    @Test
    void create_BrowserWidthHasNegativeValue_ReturnsDefault() {
        given(mockBrowserDataFor3ds.getBrowserScreenWidth()).willReturn(Optional.of("-1"));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.screenWidth(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_SCREEN_WIDTH));
    }

    @Test
    void create_BrowserTZHasValue_ReturnsValue() {
        given(mockBrowserDataFor3ds.getBrowserTZ()).willReturn(Optional.of("" + AdyenBrowserInfoFactory.DEFAULT_BROWSER_TZ));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.timeZoneOffset(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_TZ));
    }

    @Test
    void create_BrowserTZHasNoValue_ReturnsDefault() {
        given(mockBrowserDataFor3ds.getBrowserTZ()).willReturn(Optional.empty());

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.timeZoneOffset(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_TZ));
    }

    @Test
    void create_BrowserTZHasTooHighValue_ReturnsDefault() {
        given(mockBrowserDataFor3ds.getBrowserTZ()).willReturn(Optional.of("1441"));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.timeZoneOffset(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_TZ));
    }

    @Test
    void create_BrowserTZHasTooLowValue_ReturnsDefault() {
        given(mockBrowserDataFor3ds.getBrowserTZ()).willReturn(Optional.of("-1441"));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.timeZoneOffset(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_TZ));
    }

    @Test
    void create_BrowserUserAgentHasValue_ReturnsValue() {
        given(mockBrowserDataFor3ds.getBrowserUserAgent()).willReturn(Optional.of(AdyenBrowserInfoFactory.DEFAULT_BROWSER_USER_AGENT));

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.userAgent(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_USER_AGENT));
    }

    @Test
    void create_BrowserUserAgentHasNoValue_ReturnsDefault() {
        given(mockBrowserDataFor3ds.getBrowserUserAgent()).willReturn(Optional.empty());

        var result = adyenBrowserInfoFactory.create(mockBrowserDataFor3ds);

        assertThat(result.userAgent(), is(AdyenBrowserInfoFactory.DEFAULT_BROWSER_USER_AGENT));
    }
}
