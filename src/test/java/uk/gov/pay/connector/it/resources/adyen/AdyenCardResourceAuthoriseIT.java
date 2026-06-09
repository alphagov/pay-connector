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
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.base.AddChargeParameters.Builder.anAddChargeParameters;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_AUTHORISATION_REQUEST_WITH_FULL_BILLING_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(DropwizardExtensionsSupport.class)
class AdyenCardResourceAuthoriseIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension(
            "adyen", app.getLocalPort(), app.getDatabaseTestHelper());

    private ChargeDao chargeDao;

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
                .withHeader("Idempotency-Key", equalTo("auth-" + chargeId))
                .withRequestBody(equalToJson(
                        load(ADYEN_AUTHORISATION_REQUEST_WITH_FULL_BILLING_ADDRESS)
                                .formatted(chargeId, "Ecommerce"))));

        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION SUCCESS"));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));
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
}
