package uk.gov.pay.connector.model.domain.transaction;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.time.ZonedDateTime;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ChargeTransactionEntityTest {
    @Test
    public void setStatusAddsTransactionEvent() throws Exception {
        ChargeTransactionEntity chargeTransactionEntity = new ChargeTransactionEntity();
        ChargeStatus expectedStatus = ChargeStatus.CREATED;

        assertThat(chargeTransactionEntity.getTransactionEvents().size(), is(0));
        chargeTransactionEntity.updateStatus(expectedStatus);

        List<ChargeTransactionEventEntity> transactionEvents = chargeTransactionEntity.getTransactionEvents();
        assertThat(transactionEvents.size(), is(1));
        assertThat(transactionEvents.get(0).getStatus(), is(expectedStatus));
    }

    @Test
    public void setMultipleStatusesAddsMultipleTransactionEvent() throws Exception {
        ChargeTransactionEntity chargeTransactionEntity = new ChargeTransactionEntity();

        ChargeStatus expectedStatus1 = ChargeStatus.CREATED;
        chargeTransactionEntity.updateStatus(expectedStatus1);
        ChargeStatus expectedStatus2 = ChargeStatus.ENTERING_CARD_DETAILS;
        chargeTransactionEntity.updateStatus(expectedStatus2);

        List<ChargeTransactionEventEntity> transactionEvents = chargeTransactionEntity.getTransactionEvents();
        assertThat(transactionEvents.size(), is(2));
        assertThat(transactionEvents.get(0).getStatus(), is(expectedStatus2));
        assertThat(transactionEvents.get(1).getStatus(), is(expectedStatus1));
    }

    @Test
    public void setStatusAddsTransactionEventWithGatewayEventTime() throws Exception {
        ChargeTransactionEntity chargeTransactionEntity = new ChargeTransactionEntity();
        ChargeStatus expectedStatus = ChargeStatus.CREATED;

        ZonedDateTime gatewayEventTime = ZonedDateTime.now();
        chargeTransactionEntity.updateStatus(expectedStatus, gatewayEventTime);

        List<ChargeTransactionEventEntity> transactionEvents = chargeTransactionEntity.getTransactionEvents();
        assertThat(transactionEvents.size(), is(1));
        assertThat(transactionEvents.get(0).getStatus(), is(expectedStatus));
        assertThat(transactionEvents.get(0).getGatewayEventDate(), is(gatewayEventTime));
    }

    @Test
    public void createsAChargeTransactionEntityFromChargeEntity() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withStatus(ChargeStatus.CREATED).build();
        ChargeTransactionEntity chargeTransactionEntity = ChargeTransactionEntity.from(chargeEntity);
        assertThat(chargeTransactionEntity.getStatus(), is(ChargeStatus.CREATED));
        assertThat(chargeTransactionEntity.getGatewayTransactionId(), is(chargeEntity.getGatewayTransactionId()));
        assertThat(chargeTransactionEntity.getAmount(), is(chargeEntity.getAmount()));
        assertThat(chargeTransactionEntity.getCreatedDate(), is(chargeEntity.getCreatedDate()));
    }
}
