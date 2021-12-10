package uk.gov.pay.connector.model.domain;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.fee.model.Fee;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;

public class ChargeEntityTest {

    @Test
    public void shouldHaveTheGivenStatus() {
        assertEquals(aValidChargeEntity().withStatus(CREATED).build().getStatus(), CREATED.toString());
        assertEquals(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().getStatus(), ENTERING_CARD_DETAILS.toString());
    }


    @Test
    public void shouldHaveAtLeastOneOfTheGivenStatuses() {
        assertEquals(aValidChargeEntity().withStatus(CREATED).build().getStatus(), CREATED.toString());
        assertEquals(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().getStatus(), ENTERING_CARD_DETAILS.toString());
    }


    @Test
    public void shouldHaveTheExternalGivenStatus() {
        assertTrue(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus(EXTERNAL_CREATED));
        assertTrue(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasExternalStatus(EXTERNAL_STARTED));
    }

    @Test
    public void shouldHaveAtLeastOneOfTheExternalGivenStatuses() {
        assertTrue(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus(EXTERNAL_CREATED, EXTERNAL_STARTED, EXTERNAL_SUBMITTED));
        assertTrue(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasExternalStatus(EXTERNAL_STARTED, EXTERNAL_SUCCESS));
    }

    @Test
    public void shouldHaveNoneOfTheExternalGivenStatuses() {
        assertFalse(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus());
        assertFalse(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus(EXTERNAL_STARTED, EXTERNAL_SUBMITTED, EXTERNAL_SUCCESS));
        assertFalse(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasExternalStatus(EXTERNAL_CREATED, EXTERNAL_SUCCESS));
    }

    @Test
    public void shouldAllowAValidStatusTransition() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(CREATED).build();
        chargeCreated.setStatus(ENTERING_CARD_DETAILS);
        assertThat(chargeCreated.getStatus(), is(ENTERING_CARD_DETAILS.toString()));
    }

    @Test(expected = InvalidStateTransitionException.class)
    public void shouldRejectAnInvalidStatusTransition() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(CREATED).build();
        chargeCreated.setStatus(CAPTURED);
    }

    @Test
    public void shouldReturnRightAmountOfFees() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity()
                .withFee(Fee.of(FeeType.TRANSACTION, 100L))
                .withFee(Fee.of(FeeType.RADAR, 10L))
                .withFee(Fee.of(FeeType.THREE_D_S, 20L))
                .withStatus(CAPTURED).build();

        Optional<Long> totalFee = chargeCreated.getFeeAmount();
        assertTrue(totalFee.isPresent());
        assertEquals(totalFee.get().longValue(), 130L);
    }

    @Test
    public void shouldReturnNoFee() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(CREATED).build();
        Optional<Long> totalFee = chargeCreated.getFeeAmount();
        assertFalse(totalFee.isPresent());
    }
}
