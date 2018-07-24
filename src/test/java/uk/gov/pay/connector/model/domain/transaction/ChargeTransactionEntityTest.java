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
    public void createsAChargeTransactionEntityFromChargeEntity() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withStatus(ChargeStatus.CREATED).build();
        ChargeTransactionEntity chargeTransactionEntity = ChargeTransactionEntity.from(chargeEntity);
        assertThat(chargeTransactionEntity.getStatus(), is(ChargeStatus.CREATED));
        assertThat(chargeTransactionEntity.getGatewayTransactionId(), is(chargeEntity.getGatewayTransactionId()));
        assertThat(chargeTransactionEntity.getAmount(), is(chargeEntity.getAmount()));
        assertThat(chargeTransactionEntity.getCreatedDate(), is(chargeEntity.getCreatedDate()));
    }
}
