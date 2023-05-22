package uk.gov.pay.connector.gateway.sandbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;

class SandboxLast4DigitsCardNumbersTest {

    private final SandboxLast4DigitsCardNumbers sandboxLast4DigitsCardNumbers = new SandboxLast4DigitsCardNumbers();

    @Test
    void shouldDetectValidCards() {
        assertTrue(sandboxLast4DigitsCardNumbers.isValidCard("4242"));
        assertTrue(sandboxLast4DigitsCardNumbers.isValidCard(""));
    }

    @Test
    void shouldDetectErrorCards() {
        assertTrue(sandboxLast4DigitsCardNumbers.isErrorCard("0119"));
    }

    @Test
    void shouldDetectRejectedCards() {
        assertTrue(sandboxLast4DigitsCardNumbers.isRejectedCard("0002"));
        assertTrue(sandboxLast4DigitsCardNumbers.isRejectedCard("0069"));
        assertTrue(sandboxLast4DigitsCardNumbers.isRejectedCard("0127"));
    }

    @Test
    public void shouldRetrieveStatusForErrorCards() {
        assertEquals(sandboxLast4DigitsCardNumbers.cardErrorFor("0119").getNewErrorStatus(), AUTHORISATION_ERROR);
    }
}
