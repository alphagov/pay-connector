package uk.gov.pay.connector.gateway.sandbox;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.gateway.sandbox.SandboxCardNumbers.cardErrorFor;
import static uk.gov.pay.connector.gateway.sandbox.SandboxCardNumbers.isErrorCard;
import static uk.gov.pay.connector.gateway.sandbox.SandboxCardNumbers.isRejectedCard;
import static uk.gov.pay.connector.gateway.sandbox.SandboxCardNumbers.isValidCard;

public class SandboxCardNumbersTest {

    @Test
    public void shouldDetectValidCards() {
        assertTrue(isValidCard("4444333322221111"));
        assertTrue(isValidCard("4000180000000002"));
    }

    @Test
    public void shouldDetectErrorCards() {
        assertTrue(isErrorCard("4000000000000119"));
    }

    @Test
    public void shouldDetectRejectedCards() {
        assertTrue(isRejectedCard("4000000000000002"));
        assertTrue(isRejectedCard("4000000000000069"));
        assertTrue(isRejectedCard("4000000000000127"));
    }

    @Test
    public void shouldRetrieveStatusForErrorCards() {
        assertEquals(cardErrorFor("4000000000000119").getNewErrorStatus(), AUTHORISATION_ERROR);
    }
}
