package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.events.eventdetails.charge.CancelledByUserEventDetails;

import java.time.Instant;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class CancelledByUserTest {

    private final boolean live = true;
    private final String paymentId = "jweojfewjoifewj";
    private final String time = "2020-10-13T16:25:01.123456Z";
    private final String transactionId = "validTransactionId";
    private final String gatewayTransactionId = "validGatewayTransactionId";

    private ChargeEntityFixture chargeEntityFixture;

    @Before
    public void setUp() {
        chargeEntityFixture = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse(time))
                .withStatus(ChargeStatus.USER_CANCELLED)
                .withExternalId(paymentId)
                .withTransactionId(transactionId)
                .withGatewayTransactionId(gatewayTransactionId);
    }

    @Test
    public void whenAllTheDataIsAvailable() throws JsonProcessingException {
        ChargeEntity chargeEntity = chargeEntityFixture.build();

        String actual = new CancelledByUser(chargeEntity.getServiceId(), chargeEntity.getGatewayAccount().isLive(), transactionId,
                CancelledByUserEventDetails.from(chargeEntity), Instant.parse(time)).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("CANCELLED_BY_USER")));
        assertThat(actual, hasJsonPath("event_details.gateway_transaction_id", equalTo(gatewayTransactionId)));
    }

    @Test
    public void whenNoGatewayTransactionIdIsAvailable() throws JsonProcessingException {
        ChargeEntity charge = new ChargeEntity();
        charge.setExternalId(transactionId);
        charge.setGatewayTransactionId(null);
        String actual = new CancelledByUser(charge.getServiceId(), live, transactionId,
                CancelledByUserEventDetails.from(charge), Instant.parse(time)).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("CANCELLED_BY_USER")));
        assertThat(actual, hasNoJsonPath("event_details.gateway_transaction_id"));
    }
}
