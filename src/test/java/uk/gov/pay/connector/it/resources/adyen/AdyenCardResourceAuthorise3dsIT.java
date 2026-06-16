package uk.gov.pay.connector.it.resources.adyen;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

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

    private static Stream<Arguments> authorise3dsResponses() {
        return Stream.of(
                Arguments.of("authorised",
                        (Consumer<AppWithPostgresAndSqsExtension>) a -> a.getAdyenCheckoutMockClient().mock3dsAuthorisationResponse(AUTH_RESULT_REFERENCE, "Authorised"),
                        200,
                        "AUTHORISATION SUCCESS",
                        "AUTHORISATION SUCCESS",
                        AUTH_RESULT_REFERENCE),
                Arguments.of("refused",
                        (Consumer<AppWithPostgresAndSqsExtension>) a -> a.getAdyenCheckoutMockClient().mock3dsAuthorisationResponse(AUTH_RESULT_REFERENCE, "Refused"),
                        400,
                        "AUTHORISATION REJECTED",
                        "AUTHORISATION REJECTED",
                        AUTH_RESULT_REFERENCE),
                Arguments.of("client_error",
                        (Consumer<AppWithPostgresAndSqsExtension>) a -> a.getAdyenCheckoutMockClient().mock3dsAuthorisationClientError(),
                        402,
                        null,
                        "AUTHORISATION ERROR",
                        null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("authorise3dsResponses")
    void should_handle_adyen_3ds_responses(String description,
                                           Consumer<AppWithPostgresAndSqsExtension> mockSetup,
                                           int expectedHttpStatus,
                                           String expectedBodyStatus,
                                           String expectedChargeStatus,
                                           String expectedGatewayTransactionId) {
        var chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);
        mockSetup.accept(app);

        var response = app.givenSetup()
                .body(Map.of("redirect_result", REDIRECT_RESULT))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(expectedHttpStatus);

        if (expectedBodyStatus != null) {
            response.body("status", is(expectedBodyStatus));
        }

        var charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));

        if (expectedChargeStatus != null) {
            assertThat(charge.get().getStatus(), is(expectedChargeStatus));
        }

        if (expectedGatewayTransactionId != null) {
            assertThat(charge.get().getGatewayTransactionId(), is(expectedGatewayTransactionId));
        }
    }
}


