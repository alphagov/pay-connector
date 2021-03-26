package uk.gov.pay.connector.it.resources;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.service.payments.commons.testing.port.PortFactory;
import uk.gov.pay.connector.app.ExecutorServiceConfig;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.AddGatewayAccountParams;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.lang.reflect.Field;
import java.util.Map;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

public class CardAuthorizeDelayedGatewayResponseIT extends ChargingITestBase {
    private int port = PortFactory.findFreePort();

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("worldpay.urls.test", "http://localhost:" + port + "/jsp/merchant/xml/paymentService.jsp"),
            config("smartpay.urls.test", "http://localhost:" + port + "/pal/servlet/soap/Payment"),
            config("epdq.urls.test", "http://localhost:" + port + "/epdq")
    );
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);
    private String validCardDetails = buildJsonAuthorisationDetailsFor(VALID_SANDBOX_CARD_LIST[0], "visa");

    private static final String[] VALID_SANDBOX_CARD_LIST = new String[]{
            "4444333322221111",
            "4917610000000000003",
            "4242424242424242",
            "4000056655665556",
            "5105105105105100",
            "5200828282828210",
            "371449635398431",
            "3566002020360505",
            "6011000990139424",
            "36148900647913"};

    public CardAuthorizeDelayedGatewayResponseIT() {
        super("sandbox");
    }

    @Before
    public void setUp() {
        databaseTestHelper = app.getDatabaseTestHelper();
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway("sandbox")
                .withCredentials(Map.of(
                        CREDENTIALS_MERCHANT_ID, "merchant-id",
                        CREDENTIALS_USERNAME, "test-user",
                        CREDENTIALS_PASSWORD, "test-password",
                        CREDENTIALS_SHA_IN_PASSPHRASE, "test-sha-in-passphrase",
                        CREDENTIALS_SHA_OUT_PASSPHRASE, "test-sha-out-passphrase"
                ))
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);
        connectorRestApiClient = new RestAssuredClient(app.getLocalPort(), accountId);
    }

    @Test
    public void shouldReturn202_WhenGatewayAuthorisationResponseIsDelayed() throws NoSuchFieldException, IllegalAccessException {
        ExecutorServiceConfig conf = app.getConf().getExecutorServiceConfig();
        Field timeoutInSeconds = conf.getClass().getDeclaredField("timeoutInSeconds");
        timeoutInSeconds.setAccessible(true);
        timeoutInSeconds.setInt(conf, 0);

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(validCardDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(202)
                .contentType(JSON)
                .body("message", contains(format("Authorisation for charge already in progress, %s", chargeId)))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }
}
