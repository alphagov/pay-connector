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
import uk.gov.pay.connector.util.AddAgreementParams;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.service.payments.commons.model.AgreementPaymentType.RECURRING;

@ExtendWith(DropwizardExtensionsSupport.class)
class AdyenCardResourceAuthoriseRecurringPaymentsIT {

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
    void successful_authorisation_of_a_recurring_payment() {
        app.getDatabaseTestHelper().enableRecurring(Long.parseLong(testBaseExtension.getAccountId()));
        String externalAgreementId = "agreement-external-id-123";
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(String.valueOf(testBaseExtension.getAccountId()))
                .withExternalAgreementId(externalAgreementId)
                .withReference("test reference")
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);

        var chargeId = testBaseExtension.addChargeForSetUpAgreement(ENTERING_CARD_DETAILS,
                externalAgreementId,
                null,
                RECURRING).toString();

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
                .withRequestBody(matchingJsonPath("$.billingAddress.postalCode", equalTo("postcode")))
                .withRequestBody(matchingJsonPath("$.shopperReference", equalTo(externalAgreementId)))
                .withRequestBody(matchingJsonPath("$.storePaymentMethod", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.recurringProcessingModel", equalTo("Subscription"))));


        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION SUCCESS"));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));
    }
}
