package uk.gov.pay.connector.it.resources.adyen;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.authorise3dsChargeUrlFor;

@ExtendWith(DropwizardExtensionsSupport.class)
class AdyenCardResourceAuthorise3dsIT {

    private static final String REDIRECT_RESULT = "eyJ0cmFuc1N0YXR1cyI6IlkifQ==";
    private static final String AUTH_RESULT_REFERENCE = "adyen-3ds-psp-reference";

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

    private static Stream<Arguments> authorise3dsOutcomeResponses() {
        return Stream.of(
                Arguments.of("authorised",
                        "Authorised",
                        200,
                        "AUTHORISATION SUCCESS"),
                Arguments.of("refused",
                        "Refused",
                        400,
                        "AUTHORISATION REJECTED")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("authorise3dsOutcomeResponses")
    void should_handle_adyen_3ds_outcome_responses(String description,
                                                   String adyenResult,
                                                   int expectedHttpStatus,
                                                   String expectedBodyAndChargeStatus) {
        var chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);
        app.getAdyenCheckoutMockClient().mock3dsAuthorisationResponse(AUTH_RESULT_REFERENCE, adyenResult);

        app.givenSetup()
                .body(Map.of("redirect_result", REDIRECT_RESULT))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(expectedHttpStatus)
                .body("status", is(expectedBodyAndChargeStatus));

        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/payments/details"))
                .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                .withHeader("Idempotency-Key", equalTo("authorise3DS-" + chargeId))
                .withRequestBody(matchingJsonPath("$.details.redirectResult", equalTo(REDIRECT_RESULT))));

        var charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is(expectedBodyAndChargeStatus));
        assertThat(charge.get().getGatewayTransactionId(), is(AUTH_RESULT_REFERENCE));
    }

    @Test
    void should_handle_adyen_3ds_client_error_response() {
        var chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);
        app.getAdyenCheckoutMockClient().mock3dsAuthorisationClientError();

        app.givenSetup()
                .body(Map.of("redirect_result", REDIRECT_RESULT))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(402);

        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/payments/details"))
                .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                .withHeader("Idempotency-Key", equalTo("authorise3DS-" + chargeId))
                .withRequestBody(matchingJsonPath("$.details.redirectResult", equalTo(REDIRECT_RESULT))));

        var charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION ERROR"));
    }
    
    @Test
    void should_store_gateway_rejection_reason_when_adyen_3ds_authorisation_is_refused() {
        var chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);

        app.getAdyenCheckoutMockClient()
                .mock3dsAuthorisationRejected(AUTH_RESULT_REFERENCE);

        app.givenSetup()
                .body(Map.of("redirect_result", REDIRECT_RESULT))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(400)
                .body("status", is("AUTHORISATION REJECTED"));

        var charge = chargeDao.findByExternalId(chargeId);

        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION REJECTED"));
        assertThat(charge.get().getGatewayTransactionId(), is(AUTH_RESULT_REFERENCE));
        assertThat(
                charge.get().getGatewayRejectionReason(),
                is("6 - Expired Card")
        );
    }
}


