package uk.gov.pay.connector.gateway.sandbox;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;

public class SandboxFullCardNumbersTest {

    private final SandboxFullCardNumbers sandboxFullCardNumbers = new SandboxFullCardNumbers();
    
    @Test
    public void shouldDetectValidCards() {
        assertTrue(sandboxFullCardNumbers.isValidCard("4444333322221111"));
        assertTrue(sandboxFullCardNumbers.isValidCard("4000180000000002"));
        assertTrue(sandboxFullCardNumbers.isValidCard("5101110000000004"));
        assertTrue(sandboxFullCardNumbers.isValidCard("5555555555554444"));
    }

    @Test
    public void shouldDetectErrorCards() {
        assertTrue(sandboxFullCardNumbers.isErrorCard("4000000000000119"));
    }

    @Test
    public void shouldDetectRejectedCards() {
        assertTrue(sandboxFullCardNumbers.isRejectedCard("4000000000000002"));
        assertTrue(sandboxFullCardNumbers.isRejectedCard("4000000000000069"));
        assertTrue(sandboxFullCardNumbers.isRejectedCard("4000000000000127"));
    }

    @Test
    public void shouldRetrieveStatusForErrorCards() {
        assertEquals(sandboxFullCardNumbers.cardErrorFor("4000000000000119").getNewErrorStatus(), AUTHORISATION_ERROR);
    }

}
