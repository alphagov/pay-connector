package uk.gov.pay.connector.it.resources.stripe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomInt;

public class StripeResourceCancelIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    private static final String AMOUNT = "6234";
    private static final String DESCRIPTION = "Test description";

    private String stripeAccountId;
    private String accountId;
    private String paymentProvider = PaymentGatewayName.STRIPE.getName();

    private DatabaseTestHelper databaseTestHelper;
    private AddGatewayAccountCredentialsParams accountCredentialsParams;

    @BeforeEach
    void setup() {
        stripeAccountId = String.valueOf(secureRandomInt());
        databaseTestHelper = app.getDatabaseTestHelper();
        accountId = String.valueOf(secureRandomInt());

        accountCredentialsParams = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(paymentProvider)
                .withGatewayAccountId(Long.valueOf(accountId))
                .withState(ACTIVE)
                .withCredentials(Map.of("stripe_account_id", stripeAccountId))
                .build();
    }

    @Test
    void userCancelCharge() {

        addGatewayAccount();

        String transactionId = "stripe-" + secureRandomInt();
        app.getStripeMockClient().mockCancelPaymentIntent(transactionId);

        String externalChargeId = addChargeWithStatusAndTransactionId(AUTHORISATION_SUCCESS, transactionId);

        given().port(app.getLocalPort())
                .contentType(JSON)
                .post("/v1/frontend/charges/{chargeId}/cancel".replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(NO_CONTENT_204);

        app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/payment_intents/" + transactionId + "/cancel"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED)));

        Long chargeId = databaseTestHelper.getChargeIdByExternalId(externalChargeId);
        assertEquals(databaseTestHelper.getChargeStatus(chargeId), ChargeStatus.USER_CANCELLED.getValue());
    }

    @Test
    void systemCancelCharge() {

        addGatewayAccount();

        String transactionId = "stripe-" + secureRandomInt();
        app.getStripeMockClient().mockCancelPaymentIntent(transactionId);

        String externalChargeId = addChargeWithStatusAndTransactionId(AUTHORISATION_SUCCESS, transactionId);

        given().port(app.getLocalPort())
                .contentType(JSON)
                .post("/v1/api/accounts/" + accountId + "/charges/" + externalChargeId + "/cancel")
                .then()
                .statusCode(NO_CONTENT_204);

        app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/payment_intents/" + transactionId + "/cancel"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED)));

        Long chargeId = databaseTestHelper.getChargeIdByExternalId(externalChargeId);
        assertEquals(ChargeStatus.SYSTEM_CANCELLED.getValue(), databaseTestHelper.getChargeStatus(chargeId));
    }

    private String addChargeWithStatusAndTransactionId(ChargeStatus chargeStatus, String transactionId) {
        long chargeId = secureRandomInt();
        String externalChargeId = "charge-" + chargeId;
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(Long.valueOf(AMOUNT))
                .withPaymentProvider("stripe")
                .withStatus(chargeStatus)
                .withTransactionId(transactionId)
                .withGatewayCredentialId(accountCredentialsParams.getId())
                .build());
        return externalChargeId;
    }

    private void addGatewayAccount() {
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway(paymentProvider)
                .withGatewayAccountCredentials(List.of(accountCredentialsParams))
                .build());
    }

    private String constructExpectedCancelRequestBody(String paymentId) {
        Map<String, String> params = new HashMap<>();
        params.put("charge", paymentId);
        return encode(params);
    }

    private String encode(Map<String, String> params) {
        return params.keySet().stream()
                .map(key -> encode(key) + "=" + encode(params.get(key)))
                .collect(joining("&"));
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(format("Exception thrown when encoding %s", value));
        }
    }
}
