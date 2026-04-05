package uk.gov.pay.connector.it.resources;

import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.AddChargeParameters;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.queue.capture.CaptureQueue;

import java.time.Instant;

import static io.dropwizard.testing.ConfigOverride.config;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.SERVICE_ID;

public class CardResourceCaptureWithSqsQueueIT {

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(CaptureQueue.class);
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(config("captureProcessConfig.backgroundProcessingEnabled", "false"));
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());

    @Nested
    class CaptureApproveByChargeId {
        @Test
        void shouldAddChargeToQueueAndSubmitForCapture_IfChargeWasPreviouslyAuthorised() {
            String chargeId = testBaseExtension.authoriseNewCharge();

            app.givenSetup()
                    .post(ITestBaseExtension.captureChargeUrlFor(chargeId))
                    .then()
                    .statusCode(204);

            testBaseExtension.assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
            testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
            assertThat(logs.size())
                    .isOne();
            logs.assertContains("Charge [" + chargeId + "] added to capture queue. Message ID [");
        }
    }

    @Nested
    class DelayedCaptureApproveByChargeIdAndAccountId {
        @Test
        void shouldAddChargeToQueueAndSubmitForCapture_IfChargeWasAwaitingCapture_whenCaptureByChargeIdAndAccountId() {
            String chargeId = testBaseExtension.addCharge(
                    AddChargeParameters.Builder.anAddChargeParameters().withChargeStatus(AWAITING_CAPTURE_REQUEST)
                            .withCreatedDate(Instant.now().minus(48, HOURS))
                            .build());

            app.givenSetup()
                    .post(format("/v1/api/accounts/%s/charges/%s/capture", testBaseExtension.getAccountId(), chargeId))
                    .then()
                    .statusCode(204);

            testBaseExtension.assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
            testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
            assertThat(logs.size())
                    .isOne();
            logs.assertContains("Charge [" + chargeId + "] added to capture queue. Message ID [");
        }
    }

    @Nested
    class DelayedCaptureApproveByServiceIdAndAccountType {
        @Test
        void shouldAddChargeToQueueAndSubmitForCapture_IfChargeWasAwaitingCapture_whenCaptureByChargeId() {
            String chargeId = testBaseExtension.addCharge(
                    AddChargeParameters.Builder.anAddChargeParameters().withChargeStatus(AWAITING_CAPTURE_REQUEST)
                            .withCreatedDate(Instant.now().minus(48, HOURS).plus(1, MINUTES))
                            .build());

            app.givenSetup()
                    .post(format("/v1/api/service/%s/account/test/charges/%s/capture", SERVICE_ID, chargeId))
                    .then()
                    .statusCode(204);

            testBaseExtension.assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
            testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());

            assertThat(logs.size())
                    .isOne();
            logs.assertContains("Charge [" + chargeId + "] added to capture queue. Message ID [");
        }
    }
}
