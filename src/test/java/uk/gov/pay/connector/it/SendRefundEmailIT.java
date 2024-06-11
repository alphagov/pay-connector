package uk.gov.pay.connector.it;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.util.ChargeUtils;

import java.time.ZonedDateTime;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.dropwizard.testing.ConfigOverride.config;
import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.it.util.ChargeUtils.createNewChargeWithAccountId;
import static uk.gov.pay.connector.it.util.NotificationUtils.worldpayRefundNotificationPayload;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.REFUND_ISSUED;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

public class SendRefundEmailIT {
    private static final String WORLDPAY_IP_ADDRESS = "some-worldpay-ip";
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(
            config("notifyConfig.emailNotifyEnabled", "true"),
            config("worldpay.secureNotificationEnabled", "false")
    );
    
    private static final Map<String, Object> credentials = ImmutableMap.of(
            CREDENTIALS_MERCHANT_ID, "merchant-id",
            CREDENTIALS_USERNAME, "test-user",
            CREDENTIALS_PASSWORD, "test-password",
            CREDENTIALS_SHA_IN_PASSPHRASE, "test-sha-in-passphrase",
            CREDENTIALS_SHA_OUT_PASSPHRASE, "test-sha-out-passphrase"
    );
    private final String accountId = String.valueOf(RandomUtils.nextInt());
    @Test
    void shouldSendEmailFollowingASuccessfulRefund() throws Exception {
        app.getNotifyStub().respondWithSuccess();
        addGatewayAccount();

        String transactionId = String.valueOf(RandomUtils.nextInt());
        String refundExternalId = "999999";

        ChargeUtils.ExternalChargeId chargeId = createNewChargeWithAccountId(CAPTURED, transactionId, accountId, app.getDatabaseTestHelper(), "worldpay");
        app.getDatabaseTestHelper().addRefund(refundExternalId,100,  REFUND_SUBMITTED, refundExternalId,
                ZonedDateTime.now(), chargeId.toString());

        given().port(app.getLocalPort())
                .header("X-Forwarded-For", WORLDPAY_IP_ADDRESS)
                .body(worldpayRefundNotificationPayload(transactionId, "REFUNDED", refundExternalId))
                .contentType(TEXT_XML)
                .post("/v1/api/notifications/worldpay");

        Thread.sleep(500L); // Email sent using ExecutorService task: give it some time to complete
        
        app.getNotifyWireMockServer().verify(postRequestedFor(
                urlEqualTo("/v2/notifications/email"))
        );
    }

    private void addGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway("worldpay")
                .withCredentials(credentials)
                .build());
        app.getDatabaseTestHelper().addEmailNotification(Long.valueOf(accountId), "a template", true, REFUND_ISSUED);
    }
}
