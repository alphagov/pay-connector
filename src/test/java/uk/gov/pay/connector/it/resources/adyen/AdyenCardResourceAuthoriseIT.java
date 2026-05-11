package uk.gov.pay.connector.it.resources.adyen;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.dropwizard.testing.ConfigOverride;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_AUTHORISATION_REQUEST_WITH_FULL_BILLING_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(DropwizardExtensionsSupport.class)
class AdyenCardResourceAuthoriseIT {

    @RegisterExtension
    static WireMockExtension wireMockExtension = WireMockExtension.newInstance()
            .options(wireMockConfig().notifier(new ConsoleNotifier(true)).port(8081))
            .configureStaticDsl(true)
            .build();

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(
            ConfigOverride.config("adyen.baseUrls.checkout.test", "http://localhost:8081/v71"));

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
        stubFor(post("/v71/payments")
                .willReturn(aResponse().withBody("""
                        {
                          "pspReference": "%s",
                          "resultCode": "Authorised",
                          "merchantReference": "string"
                        }""".formatted(pspReferenceFromAdyen))));

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

        verify(postRequestedFor(urlEqualTo("/v71/payments"))
                .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                .withRequestBody(equalToJson(
                        load(ADYEN_AUTHORISATION_REQUEST_WITH_FULL_BILLING_ADDRESS)
                                .formatted(chargeId))));
        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION SUCCESS"));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));
    }

    @Test
    void successful_authorisation_of_a_payment_without_a_billing_address() {
        var chargeId = testBaseExtension.createNewCharge(ENTERING_CARD_DETAILS);
        var pspReferenceFromAdyen = "993617895215577D";
        stubFor(post("/v71/payments")
                .willReturn(aResponse().withBody("""
                        {
                          "pspReference": "%s",
                          "resultCode": "Authorised",
                          "merchantReference": "string"
                        }""".formatted(pspReferenceFromAdyen))));

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

        verify(postRequestedFor(urlEqualTo("/v71/payments"))
                .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                .withRequestBody(WireMock.matchingJsonPath("$.billingAddress", absent()))
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
        stubFor(post("/v71/payments")
                .willReturn(aResponse().withBody("""
                        {
                          "pspReference": "%s",
                          "refusalReason": "Expired Card",
                          "resultCode": "Refused",
                          "refusalReasonCode": "6"
                        }""".formatted(pspReferenceFromAdyen))));


        var response = app.givenSetup()
                .body(anAuthCardDetails().build())
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId);

        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION REJECTED"));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));

        response.then()
                .statusCode(400)
                .body("error_identifier", is("GENERIC"))
                .body("message[0]", is("This transaction was declined."));
    }


    @Test
    void unknown_error_from_Adyen() {
        var chargeId = testBaseExtension.createNewCharge(ENTERING_CARD_DETAILS);
        var pspReferenceFromAdyen = "851563882585825A";
        stubFor(post("/v71/payments")
                .willReturn(aResponse().withBody("""
                        {
                          "pspReference": "%s",
                          "refusalReason": "Acquirer Error",
                          "resultCode": "Error",
                          "refusalReasonCode": "4"
                        }""".formatted(pspReferenceFromAdyen))));


        var response = app.givenSetup()
                .body(anAuthCardDetails().build())
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId);

        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION ERROR"));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));

        response.then()
                .statusCode(402)
                .body("error_identifier", is("GENERIC"))
                .body("message[0]", is("There was an error authorising the transaction."));
    }
}
