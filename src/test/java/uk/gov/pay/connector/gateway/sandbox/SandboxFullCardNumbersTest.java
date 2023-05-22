package uk.gov.pay.connector.gateway.sandbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;

class SandboxFullCardNumbersTest {

    private final SandboxFullCardNumbers sandboxFullCardNumbers = new SandboxFullCardNumbers();
    
    @Test
    void shouldDetectValidCards() {
        assertTrue(sandboxFullCardNumbers.isValidCard("4444333322221111"));
        assertTrue(sandboxFullCardNumbers.isValidCard("4000180000000002"));
        assertTrue(sandboxFullCardNumbers.isValidCard("5555555555554444"));
    }

    @Test
    void shouldDetectErrorCards() {
        assertTrue(sandboxFullCardNumbers.isErrorCard("4000000000000119"));
    }

    @Test
    void shouldDetectRejectedCards() {
        assertTrue(sandboxFullCardNumbers.isRejectedCard("4000000000000002"));
        assertTrue(sandboxFullCardNumbers.isRejectedCard("4000000000000069"));
        assertTrue(sandboxFullCardNumbers.isRejectedCard("4000000000000127"));
    }

    @Test
    void shouldRetrieveStatusForErrorCards() {
        assertEquals(sandboxFullCardNumbers.cardErrorFor("4000000000000119").getNewErrorStatus(), AUTHORISATION_ERROR);
    }

}
