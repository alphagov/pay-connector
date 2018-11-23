package uk.gov.pay.connector.gateway.sandbox;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.gateway.sandbox.SandboxCardNumbers.cardErrorFor;
import static uk.gov.pay.connector.gateway.sandbox.SandboxCardNumbers.isErrorCard;
import static uk.gov.pay.connector.gateway.sandbox.SandboxCardNumbers.isRejectedCard;
import static uk.gov.pay.connector.gateway.sandbox.SandboxCardNumbers.isValidCard;

public class SandboxCardNumbersTest {

    @Test
    public void shouldDetectValidCards() {
        assertThat(isValidCard("4444333322221111"), is (true));
        assertThat(isValidCard("4000180000000002"), is (true));
        assertThat(isValidCard("4242"), is (true));
    }

    @Test
    public void shouldDetectErrorCards() {
        assertThat(isErrorCard("4000000000000119"), is (true));
        assertThat(isErrorCard("0119"), is (true));
    }

    @Test
    public void shouldDetectRejectedCards() {
        assertThat(isRejectedCard("4000000000000002"), is (true));
        assertThat(isRejectedCard("0002"), is (true));
        assertThat(isRejectedCard("4000000000000069"), is (true));
        assertThat(isRejectedCard("0069"), is (true));
        assertThat(isRejectedCard("4000000000000127"), is (true));
        assertThat(isRejectedCard("0127"), is (true));
    }

    @Test
    public void shouldRetrieveStatusForErrorCards() {
        assertThat(cardErrorFor("4000000000000119").getNewErrorStatus(), is(AUTHORISATION_ERROR));
        assertThat(cardErrorFor("0119").getNewErrorStatus(), is(AUTHORISATION_ERROR));
    }

}
