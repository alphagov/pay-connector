package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.time.temporal.ChronoUnit.HOURS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.it.base.AddChargeParameters.Builder.anAddChargeParameters;

public class ChargeCancelResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay", app.getLocalPort(), app.getDatabaseTestHelper());

    @Test
    public void shouldPreserveCardDetailsIfCancelled() {
        String externalChargeId = createNewInPastChargeWithStatus(AUTHORISATION_SUCCESS);
        Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge"));

        app.getWorldpayMockClient().mockCancelSuccess();

        Map<String, Object> cardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(cardDetails.isEmpty(), is(false));

        testBaseExtension.cancelChargeAndCheckApiStatus(externalChargeId, SYSTEM_CANCELLED, 204);

        cardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(cardDetails, is(notNullValue()));
        assertThat(cardDetails.get("card_brand"), is(notNullValue()));
        assertThat(cardDetails.get("last_digits_card_number"), is(notNullValue()));
        assertThat(cardDetails.get("first_digits_card_number"), is(notNullValue()));
        assertThat(cardDetails.get("expiry_date"), is(notNullValue()));
        assertThat(cardDetails.get("cardholder_name"), is(notNullValue()));
        assertThat(cardDetails.get("address_line1"), is(notNullValue()));
        assertThat(cardDetails.get("address_line2"), is(notNullValue()));
        assertThat(cardDetails.get("address_postcode"), is(notNullValue()));
        assertThat(cardDetails.get("address_country"), is(notNullValue()));
    }

    @Test
    public void shouldRespondWith204WithLockingStatus_IfCancelledAfterAuth() {
        String chargeId = createNewInPastChargeWithStatus(AUTHORISATION_SUCCESS);
        app.getWorldpayMockClient().mockCancelSuccess();

        testBaseExtension.cancelChargeAndCheckApiStatus(chargeId, SYSTEM_CANCELLED, 204);

        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(3));
        assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                SYSTEM_CANCEL_READY.getValue(),
                SYSTEM_CANCELLED.getValue()));
    }

    @Test
    public void shouldRespondWith204WithLockingStatus_IfCancelFailedAfterAuth() {
        String chargeId = createNewInPastChargeWithStatus(AUTHORISATION_SUCCESS);
        app.getWorldpayMockClient().mockCancelError();

        testBaseExtension.cancelChargeAndCheckApiStatus(chargeId, SYSTEM_CANCEL_ERROR, 204);

        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(3));
        assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                SYSTEM_CANCEL_READY.getValue(),
                SYSTEM_CANCEL_ERROR.getValue()));
    }

    private String createNewInPastChargeWithStatus(ChargeStatus status) {
        long chargeId = RandomUtils.nextInt();
        return testBaseExtension.addCharge(anAddChargeParameters().withChargeStatus(status)
                .withCreatedDate(Instant.now().minus(1, HOURS))
                .withChargeId(chargeId)
                .withExternalChargeId("charge" + chargeId)
                .build());
    }
}
