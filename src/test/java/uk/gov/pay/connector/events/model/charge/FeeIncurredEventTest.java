package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.events.exception.EventCreationException;

import java.time.Instant;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.FeeType.RADAR;
import static uk.gov.pay.connector.charge.model.domain.FeeType.THREE_D_S;
import static uk.gov.pay.connector.charge.model.domain.FeeType.TRANSACTION;

class FeeIncurredEventTest {
    private ChargeEntity chargeEntity;
    private Instant radarFeeCreatedDate;
    private Instant transactionFeeCreatedDate;
    private Instant threeDsFeeCreatedDate;
    
    @BeforeEach
    void setUp() {
        chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(900L)
                .withExternalId("paymentId")
                .withStatus(CAPTURED)
                .build();
        radarFeeCreatedDate = Instant.parse("2021-10-01T10:00:00Z");
        transactionFeeCreatedDate = Instant.parse("2021-11-01T11:00:00Z");
        threeDsFeeCreatedDate = Instant.parse("2021-12-01T12:00:00Z");
    }
    
    @Test
    public void serializesFeeIncurredEventGivenChargeEntity() throws JsonProcessingException, EventCreationException {
        FeeEntity radarFee = new FeeEntity(chargeEntity, radarFeeCreatedDate, 100L, RADAR);
        FeeEntity transactionFee = new FeeEntity(chargeEntity, transactionFeeCreatedDate, 200L, TRANSACTION);
        FeeEntity threeDsFee = new FeeEntity(chargeEntity, threeDsFeeCreatedDate, 300L, THREE_D_S);
        chargeEntity.addFee(radarFee);
        chargeEntity.addFee(transactionFee);
        chargeEntity.addFee(threeDsFee);
        String actual = FeeIncurredEvent.from(chargeEntity).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo("2021-10-01T10:00:00.000000Z")));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("FEE_INCURRED_EVENT")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo("paymentId")));
        assertThat(actual, hasJsonPath("$.event_details.fee", equalTo(600)));
        assertThat(actual, hasJsonPath("$.event_details.net_amount", equalTo(300)));
        assertThat(actual, hasJsonPath("$.event_details.fee_breakdown[0].fee_type", equalTo("radar")));
        assertThat(actual, hasJsonPath("$.event_details.fee_breakdown[0].amount", equalTo(100)));
        assertThat(actual, hasJsonPath("$.event_details.fee_breakdown[1].fee_type", equalTo("transaction")));
        assertThat(actual, hasJsonPath("$.event_details.fee_breakdown[1].amount", equalTo(200)));
        assertThat(actual, hasJsonPath("$.event_details.fee_breakdown[2].fee_type", equalTo("three_ds")));
        assertThat(actual, hasJsonPath("$.event_details.fee_breakdown[2].amount", equalTo(300)));
    }

    @Test
    public void creatingFeeIncurredEventThrowsEventCreationExceptionWhenNoFeesOnCharge() {
        var thrown = assertThrows(EventCreationException.class,
                () -> FeeIncurredEvent.from(chargeEntity).toJsonString());
        assertThat(thrown.getMessage(), is(String.format("Event id = [%s], exception = Failed to create FeeIncurredEvent due to no fees present on charge", chargeEntity.getExternalId())));
    }
}
