package uk.gov.pay.connector.model.domain.transaction;

import org.junit.Test;
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

        List<TransactionEventEntity> transactionEvents = chargeTransactionEntity.getTransactionEvents();
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

        List<TransactionEventEntity> transactionEvents = chargeTransactionEntity.getTransactionEvents();
        assertThat(transactionEvents.size(), is(2));
        assertThat(transactionEvents.get(0).getStatus(), is(expectedStatus1));
        assertThat(transactionEvents.get(1).getStatus(), is(expectedStatus2));
    }

    @Test
    public void setStatusAddsTransactionEventWithGatewayEventTime() throws Exception {
        ChargeTransactionEntity chargeTransactionEntity = new ChargeTransactionEntity();
        ChargeStatus expectedStatus = ChargeStatus.CREATED;

        ZonedDateTime gatewayEventTime = ZonedDateTime.now();
        chargeTransactionEntity.updateStatus(expectedStatus, gatewayEventTime);

        List<TransactionEventEntity> transactionEvents = chargeTransactionEntity.getTransactionEvents();
        assertThat(transactionEvents.size(), is(1));
        assertThat(transactionEvents.get(0).getStatus(), is(expectedStatus));
        assertThat(transactionEvents.get(0).getGatewayEventDate(), is(gatewayEventTime));

    }
}
