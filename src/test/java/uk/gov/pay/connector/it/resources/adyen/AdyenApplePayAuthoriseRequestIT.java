package uk.gov.pay.connector.it.resources.adyen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.netmikey.logunit.api.LogCapturer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.wallets.WalletAuthoriseService;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.base.AddChargeParameters.Builder.anAddChargeParameters;

public class AdyenApplePayAuthoriseRequestIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("adyen",
            app.getLocalPort(), app.getDatabaseTestHelper());

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(WalletAuthoriseService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PAYMENT_DATA = "payment_data";
    private static final String LAST_DIGITS_CARD_NUMBER = "9";
    private static final String BRAND = "Visa";
    private static final PayersCardType CARD_TYPE = PayersCardType.DEBIT;
    private static final String EMAIL = "test@test.com";
    private static final String DISPLAY_NAME = "Alec Barley";
    private static final String NETWORK = "Visa";
    private static final String TRANSACTION_IDENTIFIER = "abcdef";
    private static final long AMOUNT = 20_00L;
    private static final String DESCRIPTION = "My description";

    
    @Test
    void shouldSendCorrectRequestToAdyenReturnCorrectResponseAndLog() throws JsonProcessingException {
        String externalChargeId = testBaseExtension.addCharge(anAddChargeParameters()
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .withDescription(DESCRIPTION)
                .withAmount(AMOUNT)
                .build()
        );
        
        app.getAdyenCheckoutMockClient().mockAuthorisationSuccess(externalChargeId);

        Map<String, Object> requestParameters = Map.of(
                "payment_data", PAYMENT_DATA,
                "payment_info", Map.of(
                        "lastDigitsCardNumber", LAST_DIGITS_CARD_NUMBER,
                        "brand", BRAND,
                        "cardType", CARD_TYPE,
                        "email", EMAIL,
                        "displayName", DISPLAY_NAME,
                        "network", NETWORK,
                        "transactionIdentifier", TRANSACTION_IDENTIFIER
                )
        );

        String requestBody = objectMapper.writeValueAsString(requestParameters);

        app.givenSetup()
                .body(requestBody)
                .post(ITestBaseExtension.authoriseChargeUrlForApplePay(externalChargeId))
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
                        .withRequestBody(matchingJsonPath("$.paymentMethod.type", equalTo("applepay")))
                        .withRequestBody(matchingJsonPath("$.paymentMethod.applePayToken", equalTo(PAYMENT_DATA)))
                        .withRequestBody(matchingJsonPath("$.returnUrl", equalTo("https://card.frontend.test/card_details/" + externalChargeId)))
        );

        logs.assertContains("Authorisation with Apple Pay");
        assertThat(logs.getEvents().stream().findFirst().isPresent(), Matchers.is(true));
        List<String> structuredLogging = logs.getEvents().stream()
                .filter(loggingEvent -> loggingEvent.getMessage().contains("Authorisation with Apple Pay"))
                .findFirst().get().getArguments().stream().map(Object::toString).toList();

        assertThat(structuredLogging, hasItems(
                "payment_external_id="  + externalChargeId,
                "provider=adyen",
                "gateway_request_record=true",
                "billing_address=false",
                "email_address=false",
                "wallet=APPLE_PAY"
        ));
    }
}
