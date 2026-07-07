package uk.gov.pay.connector.it.resources.adyen;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.base.AddChargeParameters.Builder.anAddChargeParameters;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

@ExtendWith(DropwizardExtensionsSupport.class)
class AdyenCardResourceAuthoriseIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension(
            "adyen", app.getLocalPort(), app.getDatabaseTestHelper());

    private ChargeDao chargeDao;
    final String acceptHeader = "text/html";
    final String userAgent = "Mozilla/5.0";
    final String shopperIp = "127.0.0.1";
    final String language = "en-GB";
    final String colorDepth = "24";
    final String screenHeight = "900";
    final String screenWidth = "1440";
    final String timezoneOffset = "-60";
    final String shopperEmail = "email@fake.test";
    final String origin = "http://CardFrontend/";

    @BeforeEach
    void setUp() {
        chargeDao = app.getInstanceFromGuiceContainer(ChargeDao.class);
    }

    @Test
    void successful_authorisation_of_a_payment_with_a_billing_address() {
        var chargeId = testBaseExtension.createNewCharge(ENTERING_CARD_DETAILS);
        var pspReferenceFromAdyen = "993617895215577D";

        app.getAdyenCheckoutMockClient().mockAuthorisationSuccess(pspReferenceFromAdyen);

        var authCardDetails = anAuthCardDetails()
                .withCardNo("4444333322221111")
                .withCardBrand("Visa")
                .withCardHolder("John Doe")
                .withCvc("737")
                .withEndDate(CardExpiryDate.valueOf("03/30"))
                .withAddress(new Address(
                        "line1",
                        "line2",
                        "postcode",
                        "city",
                        "county",
                        "country"
                )).build();

        app.givenSetup()
                .body(authCardDetails)
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId)
                .then()
                .statusCode(200)
                .body("status", is("AUTHORISATION SUCCESS"));

        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/payments"))
                .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                .withHeader("Idempotency-Key", equalTo("authorise-" + chargeId))
                .withRequestBody(matchingJsonPath("$.billingAddress.houseNumberOrName", equalTo("line1")))
                .withRequestBody(matchingJsonPath("$.billingAddress.street", equalTo("line2")))
                .withRequestBody(matchingJsonPath("$.billingAddress.city", equalTo("city")))
                .withRequestBody(matchingJsonPath("$.billingAddress.country", equalTo("country")))
                .withRequestBody(matchingJsonPath("$.billingAddress.postalCode", equalTo("postcode"))));

        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION SUCCESS"));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));
    }

    @Test
    void should_send_browser_info_origin_email_and_ip_to_adyen_for_web_payment() {
        var chargeId = testBaseExtension.createNewCharge(ENTERING_CARD_DETAILS);
        var pspReferenceFromAdyen = "993617895215577D";

        app.getAdyenCheckoutMockClient().mockAuthorisationSuccess(pspReferenceFromAdyen);

        var authCardDetails = anAuthCardDetails()
                .withAcceptHeader(acceptHeader)
                .withUserAgentHeader(userAgent)
                .withIpAddress(shopperIp)
                .withJsNavigatorLanguage(language)
                .withJsScreenColorDepth(colorDepth)
                .withJsScreenHeight(screenHeight)
                .withJsScreenWidth(screenWidth)
                .withJsTimezoneOffsetMins(timezoneOffset)
                .withJsEnabled(true)
                .build();

        app.givenSetup()
                .body(authCardDetails)
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId)
                .then()
                .statusCode(200)
                .body("status", is("AUTHORISATION SUCCESS"));

        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/payments"))
                .withRequestBody(matchingJsonPath("$.browserInfo.acceptHeader", equalTo(acceptHeader)))
                .withRequestBody(matchingJsonPath("$.browserInfo.colorDepth", equalTo(colorDepth)))
                .withRequestBody(matchingJsonPath("$.browserInfo.javaEnabled", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.browserInfo.javaScriptEnabled", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.browserInfo.language", equalTo(language)))
                .withRequestBody(matchingJsonPath("$.browserInfo.screenHeight", equalTo(screenHeight)))
                .withRequestBody(matchingJsonPath("$.browserInfo.screenWidth", equalTo(screenWidth)))
                .withRequestBody(matchingJsonPath("$.browserInfo.timeZoneOffset", equalTo(timezoneOffset)))
                .withRequestBody(matchingJsonPath("$.browserInfo.userAgent", equalTo(userAgent)))
                .withRequestBody(matchingJsonPath("$.shopperEmail", equalTo(shopperEmail)))
                .withRequestBody(matchingJsonPath("$.shopperIP", equalTo(shopperIp)))
                .withRequestBody(matchingJsonPath("$.origin", equalTo(origin))));
    }

    @Test
    void successful_authorisation_of_a_payment_without_a_billing_address() {
        var chargeId = testBaseExtension.createNewCharge(ENTERING_CARD_DETAILS);
        var pspReferenceFromAdyen = "993617895215577D";

        app.getAdyenCheckoutMockClient().mockAuthorisationSuccess(pspReferenceFromAdyen);

        var authCardDetails = anAuthCardDetails()
                .withCardNo("4444333322221111")
                .withCardBrand("Visa")
                .withCardHolder("John Doe")
                .withCvc("737")
                .withEndDate(CardExpiryDate.valueOf("03/30"))
                .withAddress(null)
                .build();

        app.givenSetup()
                .body(authCardDetails)
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId)
                .then()
                .statusCode(200)
                .body("status", is("AUTHORISATION SUCCESS"));

        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/payments"))
                .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                .withRequestBody(matchingJsonPath("$.billingAddress", absent()))
                .withRequestBody(matchingJsonPath("$.paymentMethod",
                        equalToJson(""" 
                                {
                                  "type": "scheme",
                                  "number": "4444333322221111",
                                  "expiryMonth": "03",
                                  "expiryYear": "2030",
                                  "cvc": "737",
                                  "holderName": "John Doe"
                                }"""))));

        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION SUCCESS"));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));
    }

    @Test
    void declined_authorisation() {
        var chargeId = testBaseExtension.createNewCharge(ENTERING_CARD_DETAILS);
        var pspReferenceFromAdyen = "883617895215577D";

        app.getAdyenCheckoutMockClient().mockAuthorisationRejected(pspReferenceFromAdyen);

        app.givenSetup()
                .body(anAuthCardDetails().build())
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId)
                .then()
                .statusCode(400)
                .body("error_identifier", is("GENERIC"))
                .body("message[0]", is("This transaction was declined."));

        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION REJECTED"));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));
    }

    @Test
    void should_return_402_for_authorisation_error_from_Adyen() {
        var chargeId = testBaseExtension.createNewCharge(ENTERING_CARD_DETAILS);
        var pspReferenceFromAdyen = "883617895215577D";

        app.getAdyenCheckoutMockClient().mockAuthorisationError(pspReferenceFromAdyen);

        app.givenSetup()
                .body(anAuthCardDetails().build())
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId)
                .then()
                .statusCode(402)
                .body("error_identifier", is("GENERIC"))
                .body("message[0]", is("There was an error authorising the transaction."));
        app.getAdyenWireMockServer().checkForUnmatchedRequests();

        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION ERROR"));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));
    }

    @Test
    void should_return_402_for_unexpected_server_error() {
        var chargeId = testBaseExtension.createNewCharge(ENTERING_CARD_DETAILS);

        app.getAdyenCheckoutMockClient().mockError("/payments");

        app.givenSetup()
                .body(anAuthCardDetails().build())
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId)
                .then()
                .statusCode(402)
                .body("error_identifier", is("GENERIC"))
                .body("message[0]", is("Non-success HTTP status code 500 from gateway"));
        app.getAdyenWireMockServer().checkForUnmatchedRequests();

        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION UNEXPECTED ERROR"));
    }

    @Test
    void successful_authorisation_of_a_moto_payment() {
        var chargeParams = anAddChargeParameters().withChargeStatus(CREATED).withIsMoto(true).build();
        var chargeId = testBaseExtension.addCharge(chargeParams);
        var pspReferenceFromAdyen = "993617895215577D";

        app.getAdyenCheckoutMockClient().mockAuthorisationSuccess(pspReferenceFromAdyen);

        var authCardDetails = anAuthCardDetails()
                .withCardNo("4444333322221111")
                .withCardBrand("Visa")
                .withCardHolder("John Doe")
                .withCvc("737")
                .withEndDate(CardExpiryDate.valueOf("03/30"))
                .withAddress(null)
                .build();

        app.givenSetup()
                .body(authCardDetails)
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId)
                .then()
                .statusCode(200)
                .body("status", is("AUTHORISATION SUCCESS"));

        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/payments"))
                .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                .withRequestBody(matchingJsonPath("$.billingAddress", absent()))
                .withRequestBody(matchingJsonPath("$.paymentMethod",
                        equalToJson(""" 
                                {
                                  "type": "scheme",
                                  "number": "4444333322221111",
                                  "expiryMonth": "03",
                                  "expiryYear": "2030",
                                  "cvc": "737",
                                  "holderName": "John Doe"
                                }"""))));

        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION SUCCESS"));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));
    }

    @Test
    void should_save_3ds_data_on_charge_when_adyen_returns_with_result_code_redirect_shopper() {
        var chargeId = testBaseExtension.createNewCharge(ENTERING_CARD_DETAILS);
        var pspReferenceFromAdyen = "993617895215577D";
        var redirectUrl = "https://checkoutshopper-test.adyen.com/checkoutshopper/threeDS/redirect";
        var httpMethod3ds = "GET";

        app.getAdyenCheckoutMockClient()
                .mockAuthorisationRedirectShopper(pspReferenceFromAdyen, redirectUrl, httpMethod3ds, "");

        var authCardDetails = anAuthCardDetails().build();

        app.givenSetup()
                .body(authCardDetails)
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId)
                .then()
                .statusCode(200)
                .body("status", is("AUTHORISATION 3DS REQUIRED"));

        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);

        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION 3DS REQUIRED"));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));
        assertThat(charge.get().get3dsRequiredDetails().getIssuerUrl(), is(redirectUrl));
        assertThat(charge.get().get3dsRequiredDetails().getHttpMethod3ds(), is(httpMethod3ds));
        assertThat(charge.get().get3dsRequiredDetails().getMd(), nullValue());
        assertThat(charge.get().get3dsRequiredDetails().getPaRequest(), nullValue());
    }

    @Test
    void should_save_action_data_on_charge_when_adyen_returns_with_result_code_redirect_shopper() {
        var chargeId = testBaseExtension.createNewCharge(ENTERING_CARD_DETAILS);
        var pspReferenceFromAdyen = "993617895215577D";
        var redirectUrl = "https://checkoutshopper-test.adyen.com/checkoutshopper/threeDS/redirect";
        var httpMethod3ds = "GET";
        var data = """
                    ,"data":{
                    "MD":"testMD123",
                    "PaReq":"testPaReq123"
                }
                """;

        app.getAdyenCheckoutMockClient()
                .mockAuthorisationRedirectShopper(pspReferenceFromAdyen, redirectUrl, httpMethod3ds, data);

        var authCardDetails = anAuthCardDetails().build();

        app.givenSetup()
                .body(authCardDetails)
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId)
                .then()
                .statusCode(200)
                .body("status", is("AUTHORISATION 3DS REQUIRED"));

        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);

        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION 3DS REQUIRED"));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));
        assertThat(charge.get().get3dsRequiredDetails().getIssuerUrl(), is(redirectUrl));
        assertThat(charge.get().get3dsRequiredDetails().getHttpMethod3ds(), is(httpMethod3ds));
        assertThat(charge.get().get3dsRequiredDetails().getMd(), is("testMD123"));
        assertThat(charge.get().get3dsRequiredDetails().getPaRequest(), is("testPaReq123"));
    }
}
