package uk.gov.pay.connector.it.resources.adyen;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith(DropwizardExtensionsSupport.class)
public class AdyenCardResourceCancelIT {

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
    void successful_system_cancellation_of_payment() {
        var gatewayTransactionId = "XB7XNCQ8HXSKGK82";
        var chargeExternalId = testBaseExtension.createNewChargeWith(ChargeStatus.AUTHORISATION_SUCCESS, gatewayTransactionId);
        app.getAdyenCheckoutMockClient()
                .mockCancellationSuccess("an-adyen-reference", gatewayTransactionId);

        app.givenSetup()
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/cancel",
                        testBaseExtension.getAccountId(),
                        chargeExternalId);

        app.getAdyenWireMockServer()
                .verify(postRequestedFor(urlPathTemplate("/payments/{paymentPspReference}/cancels")));
        var charge = chargeDao.findByExternalId(chargeExternalId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is(ChargeStatus.SYSTEM_CANCEL_SUBMITTED.toString()));
    }
}
