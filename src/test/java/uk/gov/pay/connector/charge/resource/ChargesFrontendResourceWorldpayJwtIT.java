package uk.gov.pay.connector.charge.resource;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.secureRandomLong;

public class ChargesFrontendResourceWorldpayJwtIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay", app.getLocalPort(), app.getDatabaseTestHelper());

    @Test
    void shouldGetCorrectDdcToken() {
        var chargeExternalId = "myFirstChargeId";
        var gatewayAccountId = "101";
        var validCredentials = Map.of(
                "issuer", "ME",
                "organisational_unit_id", "My Org",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        setUpChargeAndAccount(gatewayAccountId, WORLDPAY, validCredentials, secureRandomLong(), chargeExternalId, ChargeStatus.CREATED);

        testBaseExtension.getConnectorRestApiClient()
                .withChargeId(chargeExternalId)
                .getWorldpay3dsFlexDdcJwt()
                .statusCode(HttpStatus.SC_OK)
                .body("jwt", is(notNullValue()));
    }

    @Test
    void shouldReturn409WhenCredentialsAreMissingForGatewayAccount() {
        var chargeExternalId = "mySecondChargeId";
        var gatewayAccountId = "202";
        setUpChargeAndAccount(gatewayAccountId, WORLDPAY, null, secureRandomLong(), chargeExternalId,
                ChargeStatus.CREATED);

        testBaseExtension.getConnectorRestApiClient()
                .withChargeId(chargeExternalId)
                .getWorldpay3dsFlexDdcJwt()
                .statusCode(HttpStatus.SC_CONFLICT)
                .body("message", is(List.of("Cannot generate Worldpay 3ds Flex JWT for account 202 because credentials are unavailable")));
    }

    @Test
    void shouldReturn409WhenThePaymentProviderIsNotWorldpayForDdcToken() {
        var chargeExternalId = "myThirdChargeId";
        var gatewayAccountId = "303";
        var validCredentials = Map.of(
                "issuer", "ME",
                "organisational_unit_id", "My Org",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        setUpChargeAndAccount(gatewayAccountId, STRIPE, validCredentials, secureRandomLong(), chargeExternalId,
                ChargeStatus.CREATED);

        testBaseExtension.getConnectorRestApiClient()
                .withChargeId(chargeExternalId)
                .getWorldpay3dsFlexDdcJwt()
                .statusCode(HttpStatus.SC_CONFLICT)
                .body("message", is(List.of("Cannot provide a Worldpay 3ds flex JWT for account 303 because the Payment Provider is not Worldpay.")));
    }

    @Test
    void shouldReturnChallengeJwt() {
        long chargeId = secureRandomLong();
        var chargeExternalId = "myFirstChargeId";
        var gatewayAccountId = "101";
        var validCredentials = Map.of(
                "issuer", "ME",
                "organisational_unit_id", "My Org",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        setUpChargeAndAccount(gatewayAccountId, WORLDPAY, validCredentials, chargeId, chargeExternalId,
                ChargeStatus.AUTHORISATION_3DS_REQUIRED);

        app.getDatabaseTestHelper().updateCharge3dsFlexChallengeDetails(chargeId,
                "http://example.com",
                "a-transaction-id",
                "a-payload",
                "2.1.0");

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(gatewayAccountId)
                .withChargeId(chargeExternalId)
                .getFrontendCharge()
                .statusCode(HttpStatus.SC_OK)
                .body("$", hasKey("auth_3ds_data"))
                .body("auth_3ds_data", hasKey("worldpayChallengeJwt"));
    }

    @Test
    void shouldOmitChallengeJwtWhenChargeNotInAppropriateState() {
        long chargeId = secureRandomLong();
        var chargeExternalId = "myFirstChargeId";
        var gatewayAccountId = "101";
        var validCredentials = Map.of(
                "issuer", "ME",
                "organisational_unit_id", "My Org",
                "jwt_mac_id", "fa2daee2-1fbb-45ff-4444-52805d5cd9e0"
        );
        setUpChargeAndAccount(gatewayAccountId, WORLDPAY, validCredentials, chargeId, chargeExternalId,
                ChargeStatus.CREATED);

        app.getDatabaseTestHelper().updateCharge3dsFlexChallengeDetails(chargeId,
                "http://example.com",
                "a-transaction-id",
                "a-payload",
                "2.1.0");

        testBaseExtension.getConnectorRestApiClient()
                .withAccountId(gatewayAccountId)
                .withChargeId(chargeExternalId)
                .getFrontendCharge()
                .statusCode(HttpStatus.SC_OK)
                .body("$", hasKey("auth_3ds_data"))
                .body("auth_3ds_data", not(hasKey("worldpayChallengeJwt")));
    }

    private void setUpChargeAndAccount(String gatewayAccountId,
                                       PaymentGatewayName paymentProvider,
                                       Map<String, String> credentials,
                                       Long chargeId,
                                       String chargeExternalId,
                                       ChargeStatus chargeStatus) {
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(gatewayAccountId)
                .withPaymentGateway(paymentProvider.getName())
                .build());

        if (credentials != null) {
            app.getDatabaseTestHelper().insertWorldpay3dsFlexCredential(
                    Long.valueOf(gatewayAccountId),
                    credentials.get("jwt_mac_id"),
                    credentials.get("issuer"),
                    credentials.get("organisational_unit_id"),
                    2L);
        }

        app.getDatabaseTestHelper().addCharge(
                anAddChargeParams()
                        .withChargeId(chargeId)
                        .withGatewayAccountId(gatewayAccountId)
                        .withExternalChargeId(chargeExternalId)
                        .withPaymentProvider(paymentProvider.getName())
                        .withStatus(chargeStatus)
                        .build()
        );
    }

}
