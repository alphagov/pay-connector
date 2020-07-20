package uk.gov.pay.connector.gateway.sandbox;


import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.gateway.sandbox.SandboxWalletCardNumbers.cardErrorFor;
import static uk.gov.pay.connector.gateway.sandbox.SandboxWalletCardNumbers.isErrorCard;
import static uk.gov.pay.connector.gateway.sandbox.SandboxWalletCardNumbers.isRejectedCard;
import static uk.gov.pay.connector.gateway.sandbox.SandboxWalletCardNumbers.isValidCard;

public class SandboxWalletCardNumbersTest {

    @Test
    public void shouldDetectValidCards() {
        assertTrue(isValidCard("4242"));
        assertTrue(isValidCard(""));
    }

    @Test
    public void shouldDetectErrorCards() {
        assertTrue(isErrorCard("0119"));
    }

    @Test
    public void shouldDetectRejectedCards() {
        assertTrue(isRejectedCard("0002"));
        assertTrue(isRejectedCard("0069"));
        assertTrue(isRejectedCard("0127"));
    }

    @Test
    public void shouldRetrieveStatusForErrorCards() {
        assertEquals(cardErrorFor("0119").getNewErrorStatus(), AUTHORISATION_ERROR);
    }
}
