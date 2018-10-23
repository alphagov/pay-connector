package uk.gov.pay.connector.model.domain;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

public class ChargeEntityTest {

    @Test
    public void shouldHaveTheGivenStatus() {
        assertTrue(aValidChargeEntity().withStatus(CREATED).build().hasStatus(CREATED));
        assertTrue(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasStatus(ENTERING_CARD_DETAILS));
    }


    @Test
    public void shouldHaveAtLeastOneOfTheGivenStatuses() {
        assertTrue(aValidChargeEntity().withStatus(CREATED).build().hasStatus(CREATED, ENTERING_CARD_DETAILS));
        assertTrue(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasStatus(CAPTURED, ENTERING_CARD_DETAILS));
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
    public void shouldAllowAValidStatusTransition() throws Exception {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(CREATED).build();
        chargeCreated.setStatus(ENTERING_CARD_DETAILS);
        assertThat(chargeCreated.getStatus(), is(ENTERING_CARD_DETAILS.getValue()));
    }

    @Test(expected = InvalidStateTransitionException.class)
    public void shouldRejectAnInvalidStatusTransition() throws Exception {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity()
                .withStatus(CREATED).build();
        chargeCreated.setStatus(CAPTURED);
    }
}
