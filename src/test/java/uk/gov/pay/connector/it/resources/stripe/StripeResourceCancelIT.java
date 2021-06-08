package uk.gov.pay.connector.it.resources.stripe;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class StripeResourceCancelIT {
    private static final String AMOUNT = "6234";
    private static final String DESCRIPTION = "Test description";

    private String stripeAccountId;
    private String accountId;
    private StripeMockClient stripeMockClient;
    private String paymentProvider = PaymentGatewayName.STRIPE.getName();

    private DatabaseTestHelper databaseTestHelper;

    @DropwizardTestContext
    private TestContext testContext;

    private WireMockServer wireMockServer;

    @Before
    public void setup() {
        wireMockServer = testContext.getWireMockServer();
        stripeMockClient = new StripeMockClient(wireMockServer);
        
        stripeAccountId = String.valueOf(RandomUtils.nextInt());
        databaseTestHelper = testContext.getDatabaseTestHelper();
        accountId = String.valueOf(RandomUtils.nextInt());
        stripeMockClient.mockCancelCharge();
    }

    @Test
    public void userCancelCharge() {

        addGatewayAccount(ImmutableMap.of("stripe_account_id", stripeAccountId));

        String transactionId = "stripe-" + RandomUtils.nextInt();
        String externalChargeId = addChargeWithStatusAndTransactionId(AUTHORISATION_SUCCESS, transactionId);

        given().port(testContext.getPort())
                .contentType(JSON)
                .post("/v1/frontend/charges/{chargeId}/cancel".replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(NO_CONTENT_204);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/refunds"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED))
                .withRequestBody(matching(constructExpectedCancelRequestBody(transactionId))));

        Long chargeId = databaseTestHelper.getChargeIdByExternalId(externalChargeId);
        assertEquals(databaseTestHelper.getChargeStatus(chargeId), ChargeStatus.USER_CANCELLED.getValue());
    }

    @Test
    public void systemCancelCharge() {

        addGatewayAccount(ImmutableMap.of("stripe_account_id", stripeAccountId));

        String transactionId = "stripe-" + RandomUtils.nextInt();
        String externalChargeId = addChargeWithStatusAndTransactionId(AUTHORISATION_SUCCESS, transactionId);

        given().port(testContext.getPort())
                .contentType(JSON)
                .post("/v1/api/accounts/" + accountId + "/charges/" + externalChargeId + "/cancel")
                .then()
                .statusCode(NO_CONTENT_204);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/refunds"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED))
                .withRequestBody(matching(constructExpectedCancelRequestBody(transactionId))));

        Long chargeId = databaseTestHelper.getChargeIdByExternalId(externalChargeId);
        assertEquals(databaseTestHelper.getChargeStatus(chargeId), ChargeStatus.SYSTEM_CANCELLED.getValue());
    }

    private String addChargeWithStatusAndTransactionId(ChargeStatus chargeStatus, String transactionId) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(Long.valueOf(AMOUNT))
                .withStatus(chargeStatus)
                .withTransactionId(transactionId)
                .build());
        return externalChargeId;
    }

    private void addGatewayAccount(Map credentials) {
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway(paymentProvider)
                .withCredentials(credentials)
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
