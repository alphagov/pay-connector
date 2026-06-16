package uk.gov.pay.connector.it.resources.adyen;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import java.util.Map;

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

    @Test
    void should_submit_redirect_result_to_adyen_and_update_charge_to_success() {
        var chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);
        app.getAdyenCheckoutMockClient().mock3dsAuthorisationResponseAuthorised(AUTH_RESULT_REFERENCE);

        app.givenSetup()
                .body(Map.of("redirect_result", REDIRECT_RESULT))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(200)
                .body("status", is("AUTHORISATION SUCCESS"));


        var charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION SUCCESS"));
        assertThat(charge.get().getGatewayTransactionId(), is(AUTH_RESULT_REFERENCE));
    }

    @Test
    void should_update_charge_to_rejected_when_adyen_returns_refused() {
        var chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);
        app.getAdyenCheckoutMockClient().mock3dsAuthorisationResponseRefused(AUTH_RESULT_REFERENCE);

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
    }

    @Test
    void should_update_charge_to_cancelled_when_adyen_returns_cancelled() {
        var chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);
        app.getAdyenCheckoutMockClient().mock3dsAuthorisationResponseCancelled(AUTH_RESULT_REFERENCE);

        app.givenSetup()
                .body(Map.of("redirect_result", REDIRECT_RESULT))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(400)
                .body("status", is("AUTHORISATION CANCELLED"));

        var charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION CANCELLED"));
        assertThat(charge.get().getGatewayTransactionId(), is(AUTH_RESULT_REFERENCE));
    }

    @Test
    void should_update_charge_to_error_when_adyen_returns_error() {
        var chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);
        app.getAdyenCheckoutMockClient().mock3dsAuthorisationResponseError(AUTH_RESULT_REFERENCE);

        app.givenSetup()
                .body(Map.of("redirect_result", REDIRECT_RESULT))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(400)
                .body("status", is("AUTHORISATION ERROR"));

        var charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION ERROR"));
        assertThat(charge.get().getGatewayTransactionId(), is(AUTH_RESULT_REFERENCE));
    }

    @Test
    void should_update_charge_to_error_when_adyen_returns_unhandled_result_code() {
        var chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);
        app.getAdyenCheckoutMockClient().mock3dsAuthorisationResponseWithUnknownResultCode(AUTH_RESULT_REFERENCE, "Pending");

        app.givenSetup()
                .body(Map.of("redirect_result", REDIRECT_RESULT))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(400)
                .body("status", is("AUTHORISATION ERROR"));

        var charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION ERROR"));
        assertThat(charge.get().getGatewayTransactionId(), is(AUTH_RESULT_REFERENCE));
    }

    @Test
    void should_update_charge_to_error_when_adyen_returns_4xx_or_5xx() {
        var chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);
        app.getAdyenCheckoutMockClient().mock3dsAuthorisationClientError();

        app.givenSetup()
                .body(Map.of("redirect_result", REDIRECT_RESULT))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(402);

        var charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION ERROR"));
    }

    @Test
    void should_update_charge_to_error_when_adyen_returns_5xx() {
        var chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);
        app.getAdyenCheckoutMockClient().mockError("/payments/details");

        app.givenSetup()
                .body(Map.of("redirect_result", REDIRECT_RESULT))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(402);

        var charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is("AUTHORISATION ERROR"));
    }
}


