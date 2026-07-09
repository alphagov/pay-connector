package uk.gov.pay.connector.it.resources.worldpay.gatewayrequestrecords;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.paymentprocessor.service.CardAuthoriseService;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingXPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.base.AddChargeParameters.Builder.anAddChargeParameters;
import static uk.gov.pay.connector.rules.WorldpayMockClient.WORLDPAY_URL;

public class WorldpayMotoAuthoriseRequestIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay",
            app.getLocalPort(), app.getDatabaseTestHelper());

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(CardAuthoriseService.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DESCRIPTION = "My description";
    private static final long AMOUNT = 20_00L;
    private static final String CARD_NUMBER = "4242424242424242";
    private static final String CVC = "123";
    private static final CardExpiryDate EXPIRY_DATE = CardExpiryDate.valueOf("11/30");
    private static final String CARDHOLDER_NAME = "Alec Barley";

    @Test
    void shouldSendCorrectRequestToWorldpayReturnCorrectResponseAndLog() throws JsonProcessingException {
        String externalChargeId = testBaseExtension.addCharge(anAddChargeParameters()
                        .withChargeStatus(ENTERING_CARD_DETAILS)
                        .withDescription(DESCRIPTION)
                        .withAmount(AMOUNT)
                        .withIsMoto(true)
                        .build()
        );

        app.getWorldpayMockClient().mockAuthorisationSuccess();

        Map<String, Object> requestParameters = Map.of(
                "card_number", CARD_NUMBER,
                "cvc", CVC,
                "expiry_date", EXPIRY_DATE.getTwoDigitMonth() + '/' + EXPIRY_DATE.getTwoDigitYear(),
                "card_brand", "visa",
                "cardholder_name", CARDHOLDER_NAME,
                "accept_header", "text/html",
                "user_agent_header", "Mozilla/5.0",
                "prepaid", "NOT_PREPAID");

        String requestBody = objectMapper.writeValueAsString(requestParameters);

        app.givenSetup()
                .body(requestBody)
                .post(ITestBaseExtension.authoriseChargeUrlFor(externalChargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_SUCCESS.toString());

        @SuppressWarnings("unchecked")
        Map<String, String> oneOffCreds = (Map<String, String>) testBaseExtension.getCredentials()
                .get(GatewayAccount.ONE_OFF_CUSTOMER_INITIATED);

        String merchantCode = oneOffCreds.get("merchant_code");

        app.getWorldpayWireMockServer().verify(
                postRequestedFor(urlPathEqualTo(WORLDPAY_URL))
                        .withHeader("Content-Type", equalTo("application/xml"))
                        .withHeader("Authorization", equalTo("Basic dGVzdC11c2VyOnRlc3QtcGFzc3dvcmQ="))
                        .withRequestBody(hasMerchantCode(merchantCode))
                        .withRequestBody(hasOrderCode())
                        .withRequestBody(hasDescription(DESCRIPTION))
                        .withRequestBody(hasAmount(String.valueOf(AMOUNT)))
                        .withRequestBody(hasCardNumber(CARD_NUMBER))
                        .withRequestBody(hasExpiryMonth(EXPIRY_DATE.getTwoDigitMonth()))
                        .withRequestBody(hasExpiryYear(EXPIRY_DATE.getFourDigitYear()))
                        .withRequestBody(hasCardHolderName(CARDHOLDER_NAME))
                        .withRequestBody(hasCvc(CVC))
                        .withRequestBody(hasNoCardAddress())
                        .withRequestBody(hasNoSession())
                        .withRequestBody(hasNoShopper()));

        logs.assertContains("Authorisation without billing address for " + externalChargeId);
        assertThat(logs.getEvents().stream().findFirst().isPresent(), is(true));
        List<String> structuredLogging = logs.getEvents().stream().findFirst().get()
                .getArguments().stream().map(Object::toString).toList();
        assertThat(structuredLogging, hasItems(
                "payment_external_id="  + externalChargeId,
                "provider=worldpay",
                "gateway_request_record=true",
                "billing_address=false",
                "email_address=false",
                "data_for_3ds=false",
                "moto=true",
                "corporate_card=false"
        ));
    }

    private static StringValuePattern hasMerchantCode(String merchantCode) {
        return matchingXPath("/paymentService/@merchantCode", equalTo(merchantCode));
    }

    private static StringValuePattern hasOrderCode() {
        return matchingXPath("/paymentService/submit/order/@orderCode");
    }

    private static StringValuePattern hasDescription(String description) {
        return matchingXPath("/paymentService/submit/order/description/text()", equalTo(description));
    }

    private static StringValuePattern hasAmount(String amount) {
        return matchingXPath("/paymentService/submit/order/amount/@value", equalTo(amount));
    }

    private static StringValuePattern hasCardNumber(String cardNumber) {
        return matchingXPath("/paymentService/submit/order/paymentDetails/CARD-SSL/cardNumber/text()", equalTo(cardNumber));
    }

    private static StringValuePattern hasExpiryMonth(String month) {
        return matchingXPath("/paymentService/submit/order/paymentDetails/CARD-SSL/expiryDate/date/@month", equalTo(month));
    }

    private static StringValuePattern hasExpiryYear(String year) {
        return matchingXPath("/paymentService/submit/order/paymentDetails/CARD-SSL/expiryDate/date/@year", equalTo(year));
    }

    private static StringValuePattern hasCardHolderName(String cardHolderName) {
        return matchingXPath("/paymentService/submit/order/paymentDetails/CARD-SSL/cardHolderName/text()", equalTo(cardHolderName));
    }

    private static StringValuePattern hasCvc(String cvc) {
        return matchingXPath("/paymentService/submit/order/paymentDetails/CARD-SSL/cvc/text()", equalTo(cvc));
    }

    private static StringValuePattern hasNoCardAddress() {
        return matchingXPath("count(/paymentService/submit/order/paymentDetails/CARD-SSL/cardAddress)", equalTo("0"));
    }

    private static StringValuePattern hasNoSession() {
        return matchingXPath("count(/paymentService/submit/order/paymentDetails/session)", equalTo("0"));
    }

    private static StringValuePattern hasNoShopper() {
        return matchingXPath("count(/paymentService/submit/order/shopper)", equalTo("0"));
    }

}
