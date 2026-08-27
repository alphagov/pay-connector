package uk.gov.pay.connector.it.resources.adyen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.netmikey.logunit.api.LogCapturer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.adyen.AdyenBrowserInfoFactory;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.wallets.WalletAuthoriseService;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Map.entry;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.gateway.adyen.AdyenBrowserInfoFactory.DEFAULT_BROWSER_ACCEPT_HEADER;
import static uk.gov.pay.connector.gateway.adyen.AdyenBrowserInfoFactory.DEFAULT_BROWSER_COLOR_DEPTH;
import static uk.gov.pay.connector.gateway.adyen.AdyenBrowserInfoFactory.DEFAULT_BROWSER_LANGUAGE;
import static uk.gov.pay.connector.gateway.adyen.AdyenBrowserInfoFactory.DEFAULT_BROWSER_SCREEN_HEIGHT;
import static uk.gov.pay.connector.gateway.adyen.AdyenBrowserInfoFactory.DEFAULT_BROWSER_TZ;
import static uk.gov.pay.connector.gateway.adyen.AdyenBrowserInfoFactory.DEFAULT_BROWSER_USER_AGENT;
import static uk.gov.pay.connector.it.base.AddChargeParameters.Builder.anAddChargeParameters;

public class AdyenGooglePayAuthorisePayloadIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("adyen",
            app.getLocalPort(), app.getDatabaseTestHelper());

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(WalletAuthoriseService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TOKEN = "token";
    private static final long AMOUNT = 20_00L;
    private static final String DESCRIPTION = "My description";
    private static final String LAST_DIGITS_CARD_NUMBER = "9";
    private static final String BRAND = "Visa";
    private static final PayersCardType CARD_TYPE = PayersCardType.DEBIT;
    private static final String EMAIL = "test@test.com";
    private static final String ACCEPT_HEADER = "text/html";
    private static final String USER_AGENT_HEADER = "userAgentHeader";
    private static final String IP_ADDRESS = "10.20.30.40";
    private static final boolean JS_ENABLED = true;
    private static final String LANGUAGE = "en";
    private static final int COLOUR_DEPTH = 32;
    private static final int SCREEN_HEIGHT = 982;
    private static final int SCREEN_WIDTH = 1512;
    private static final int TIME_OFFSET = -60;



    @Test
    void shouldSendCorrectRequestToAdyenReturnCorrectResponseAndLog() throws JsonProcessingException {
        String externalChargeId = testBaseExtension.addCharge(anAddChargeParameters()
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .withDescription(DESCRIPTION)
                .withAmount(AMOUNT)
                .build()
        );
        
        app.getAdyenCheckoutMockClient().mockAuthorisationSuccess(externalChargeId);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "payment_info", Map.ofEntries(
                        entry("last_digits_card_number", LAST_DIGITS_CARD_NUMBER),
                        entry("brand", BRAND),
                        entry("card_type", CARD_TYPE),
                        entry("email", EMAIL),
                        entry("accept_header", ACCEPT_HEADER),
                        entry("user_agent_header", USER_AGENT_HEADER),
                        entry("ip_address", IP_ADDRESS),
                        entry("js_enabled", JS_ENABLED),
                        entry("js_navigator_language", LANGUAGE),
                        entry("js_screen_color_depth", COLOUR_DEPTH),
                        entry("js_screen_height", SCREEN_HEIGHT),
                        entry("js_screen_width", SCREEN_WIDTH),
                        entry("js_timezone_offset_mins", TIME_OFFSET)
                ),
                "token", TOKEN
        ));

        app.givenSetup()
                .body(requestBody)
                .post(ITestBaseExtension.authoriseChargeUrlForAdyenGooglePay(externalChargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_SUCCESS.toString());

        app.getAdyenWireMockServer().verify(
                postRequestedFor(urlPathEqualTo("/payments"))
                        .withHeader("Content-Type", equalTo("application/json"))
                        .withHeader("Idempotency-Key", equalTo("authorise-" + externalChargeId))
                        .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                        .withRequestBody(matchingJsonPath("$.merchantAccount", equalTo("adyen-test-merchant-account-id")))
                        .withRequestBody(matchingJsonPath("$.store", equalTo("test-store-id")))
                        .withRequestBody(matchingJsonPath("$.reference", equalTo(externalChargeId)))
                        .withRequestBody(matchingJsonPath("$.amount.value", equalTo(String.valueOf(AMOUNT))))
                        .withRequestBody(matchingJsonPath("$.amount.currency", equalTo("GBP")))
                        .withRequestBody(matchingJsonPath("$.paymentMethod.type", equalTo("googlepay")))
                        .withRequestBody(matchingJsonPath("$.returnUrl", equalTo("https://card.frontend.test/card_details/" + externalChargeId + "/3ds_required_in/adyen")))
                        .withRequestBody(matchingJsonPath("$.browserInfo.javaScriptEnabled", equalTo(String.valueOf(JS_ENABLED))))
                        .withRequestBody(matchingJsonPath("$.browserInfo.acceptHeader", equalTo(ACCEPT_HEADER)))
                        .withRequestBody(matchingJsonPath("$.browserInfo.userAgent", equalTo(USER_AGENT_HEADER)))
                        .withRequestBody(matchingJsonPath("$.browserInfo.language", equalTo(LANGUAGE)))
                        .withRequestBody(matchingJsonPath("$.browserInfo.colorDepth", equalTo(String.valueOf(COLOUR_DEPTH))))
                        .withRequestBody(matchingJsonPath("$.browserInfo.screenHeight", equalTo(String.valueOf(SCREEN_HEIGHT))))
                        .withRequestBody(matchingJsonPath("$.browserInfo.screenWidth", equalTo(String.valueOf(SCREEN_WIDTH))))
                        .withRequestBody(matchingJsonPath("$.browserInfo.timeZoneOffset", equalTo(String.valueOf(TIME_OFFSET))))
                        .withRequestBody(matchingJsonPath("$.browserInfo.javaEnabled", equalTo(String.valueOf(false))))
        );

        logs.assertContains("Authorisation with Google Pay");
        assertThat(logs.getEvents().stream().findFirst().isPresent(), Matchers.is(true));
        List<String> structuredLogging = logs.getEvents().stream()
                .filter(loggingEvent -> loggingEvent.getMessage().contains("Authorisation with Google Pay"))
                .findFirst().get().getArguments().stream().map(Object::toString).toList();

        assertThat(structuredLogging, hasItems(
                "payment_external_id="  + externalChargeId,
                "provider=adyen",
                "gateway_request_record=true",
                "billing_address=false",
                "email_address=false",
                "wallet=GOOGLE_PAY"
        ));
        
    }

    @Test
    void shouldSendCorrectRequestToAdyenReturnCorrectResponseAndLogWithDefaultValuesFromFrontend() throws JsonProcessingException {
        String externalChargeId = testBaseExtension.addCharge(anAddChargeParameters()
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .withDescription(DESCRIPTION)
                .withAmount(AMOUNT)
                .build()
        );

        app.getAdyenCheckoutMockClient().mockAuthorisationSuccess(externalChargeId);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "payment_info", Map.ofEntries(
                        entry("last_digits_card_number", LAST_DIGITS_CARD_NUMBER),
                        entry("brand", BRAND),
                        entry("card_type", CARD_TYPE)
                ),
                "token", TOKEN
        ));

        app.givenSetup()
                .body(requestBody)
                .post(ITestBaseExtension.authoriseChargeUrlForAdyenGooglePay(externalChargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_SUCCESS.toString());

        app.getAdyenWireMockServer().verify(
                postRequestedFor(urlPathEqualTo("/payments"))
                        .withHeader("Content-Type", equalTo("application/json"))
                        .withHeader("Idempotency-Key", equalTo("authorise-" + externalChargeId))
                        .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                        .withRequestBody(matchingJsonPath("$.merchantAccount", equalTo("adyen-test-merchant-account-id")))
                        .withRequestBody(matchingJsonPath("$.store", equalTo("test-store-id")))
                        .withRequestBody(matchingJsonPath("$.reference", equalTo(externalChargeId)))
                        .withRequestBody(matchingJsonPath("$.amount.value", equalTo(String.valueOf(AMOUNT))))
                        .withRequestBody(matchingJsonPath("$.amount.currency", equalTo("GBP")))
                        .withRequestBody(matchingJsonPath("$.paymentMethod.type", equalTo("googlepay")))
                        .withRequestBody(matchingJsonPath("$.returnUrl", equalTo("https://card.frontend.test/card_details/" + externalChargeId + "/3ds_required_in/adyen")))
                        .withRequestBody(matchingJsonPath("$.browserInfo.javaScriptEnabled", equalTo(String.valueOf(false))))
                        .withRequestBody(matchingJsonPath("$.browserInfo.acceptHeader", equalTo(DEFAULT_BROWSER_ACCEPT_HEADER)))
                        .withRequestBody(matchingJsonPath("$.browserInfo.userAgent", equalTo(DEFAULT_BROWSER_USER_AGENT)))
                        .withRequestBody(matchingJsonPath("$.browserInfo.language", equalTo(DEFAULT_BROWSER_LANGUAGE)))
                        .withRequestBody(matchingJsonPath("$.browserInfo.colorDepth", equalTo(String.valueOf(DEFAULT_BROWSER_COLOR_DEPTH))))
                        .withRequestBody(matchingJsonPath("$.browserInfo.screenHeight", equalTo(String.valueOf(DEFAULT_BROWSER_SCREEN_HEIGHT))))
                        .withRequestBody(matchingJsonPath("$.browserInfo.screenWidth", equalTo(String.valueOf(DEFAULT_BROWSER_SCREEN_HEIGHT))))
                        .withRequestBody(matchingJsonPath("$.browserInfo.timeZoneOffset", equalTo(String.valueOf(DEFAULT_BROWSER_TZ))))
                        .withRequestBody(matchingJsonPath("$.browserInfo.javaEnabled", equalTo(String.valueOf(false))))
        );

        logs.assertContains("Authorisation with Google Pay");
        assertThat(logs.getEvents().stream().findFirst().isPresent(), Matchers.is(true));
        List<String> structuredLogging = logs.getEvents().stream()
                .filter(loggingEvent -> loggingEvent.getMessage().contains("Authorisation with Google Pay"))
                .findFirst().get().getArguments().stream().map(Object::toString).toList();

        assertThat(structuredLogging, hasItems(
                "payment_external_id="  + externalChargeId,
                "provider=adyen",
                "gateway_request_record=true",
                "billing_address=false",
                "email_address=false",
                "wallet=GOOGLE_PAY"
        ));
    }
}
