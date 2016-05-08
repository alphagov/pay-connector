package uk.gov.pay.connector.model.domain;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.fixture.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.api.LegacyChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

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
        assertTrue(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus(LEGACY_EXT_CREATED));
        assertTrue(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasExternalStatus(LEGACY_EXT_IN_PROGRESS));
    }

    @Test
    public void shouldHaveAtLeastOneOfTheExternalGivenStatuses() {
        assertTrue(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus(LEGACY_EXT_CREATED, LEGACY_EXT_IN_PROGRESS));
        assertTrue(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasExternalStatus(LEGACY_EXT_IN_PROGRESS, LEGACY_EXT_SUCCEEDED));
    }

    @Test
    public void shouldHaveNoneOfTheExternalGivenStatuses() {
        assertFalse(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus());
        assertFalse(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus(LEGACY_EXT_IN_PROGRESS, LEGACY_EXT_SUCCEEDED));
        assertFalse(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasExternalStatus(LEGACY_EXT_CREATED, LEGACY_EXT_SUCCEEDED));
    }

}