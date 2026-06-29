package uk.gov.pay.connector.it.resources.adyen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.client.cardid.model.CardidCardType;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_QUEUED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_ADYEN_LEGAL_ENTITY_ID;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonForMotoApiPaymentAuthorisation;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomLong;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_AUTHORISATION_REQUEST_WITH_FULL_BILLING_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

@ExtendWith(DropwizardExtensionsSupport.class)
public class AdyenCardResourceAuthoriseMotoApiPaymentIT {
    
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("adyen", app.getLocalPort(), app.getDatabaseTestHelper());

    private static final String AUTHORISE_MOTO_API_URL = "/v1/api/charges/authorise";
    private static final String VALID_CARD_NUMBER = "4444333322221111";
    private static final String VISA = "visa";
    private static final String CHARGE_EXTERNAL_ID = String.valueOf(randomLong());
    private static final long CHARGE_ID = randomLong();

    private DatabaseFixtures.TestToken token;
    private DatabaseFixtures.TestCharge charge;

    @BeforeEach
    void setUp() {

        var cardDetails = app.getDatabaseFixtures()
                .aTestCardDetails()
                .withChargeId(CHARGE_ID)
                .withCardBrand(VISA)
                .withBillingAddress(new DatabaseFixtures.TestAddress());

        long testCredentialsId = randomLong();
        long gatewayAccountId = randomLong();

        var credentialParams = anAddGatewayAccountCredentialsParams()
                .withId(testCredentialsId)
                .withPaymentProvider(ADYEN.getName())
                .withGatewayAccountId(gatewayAccountId)
                .withState(ACTIVE)
                .withCredentials(ImmutableMap.of(CREDENTIALS_ADYEN_LEGAL_ENTITY_ID, "legal_entity_id"))
                .build();

        var adyenAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withCardTypeEntities(List.of(app.getDatabaseTestHelper().getVisaCreditCard()))
                .withGatewayAccountCredentials(List.of(credentialParams))
                .withAccountId(gatewayAccountId)
                .withServiceId("service-id")
                .insert();

        charge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withChargeId(CHARGE_ID)
                .withExternalChargeId(CHARGE_EXTERNAL_ID)
                .withCardDetails(cardDetails)
                .withPaymentProvider("adyen")
                .withChargeStatus(CREATED)
                .withAuthorisationMode(MOTO_API)
                .withAmount(6234L)
                .withTestAccount(adyenAccount)
                .withGatewayCredentialId(testCredentialsId)
                .insert();

        token = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestToken()
                .withCharge(charge)
                .withUsed(false)
                .insert();
    }

    @Test
    void should_send_billingAddress_when_authorising_a_payment_with_authorisationMode_set_to_MOTO_API() throws JsonProcessingException {
        String validPayload = buildJsonForMotoApiPaymentAuthorisation("John Doe", VALID_CARD_NUMBER, "03/30", "737",
                token.getSecureRedirectToken());

        var cardInformation = aCardInformation().withBrand(VISA).withType(CardidCardType.CREDIT).build();
        app.getCardidStub().returnCardInformation(VALID_CARD_NUMBER, cardInformation);

        var pspReferenceFromAdyen = "993617895215577D";
        app.getAdyenCheckoutMockClient().mockAuthorisationSuccess(pspReferenceFromAdyen);

        app.givenSetup()
                .body(validPayload)
                .post(AUTHORISE_MOTO_API_URL)
                .then()
                .statusCode(204);

        assertThat(app.getDatabaseTestHelper().isChargeTokenUsed(token.getSecureRedirectToken()), is(true));
        assertThat(app.getDatabaseTestHelper().getChargeStatus(charge.getChargeId()), is(CAPTURE_QUEUED.getValue()));

        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/payments"))
                .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                .withRequestBody(equalToJson(
                        load(ADYEN_AUTHORISATION_REQUEST_WITH_FULL_BILLING_ADDRESS)
                                .formatted(CHARGE_EXTERNAL_ID, "http://CardFrontend//card_details/" + CHARGE_EXTERNAL_ID +"/3ds_required_in/adyen", "Moto"))));

    }


}
