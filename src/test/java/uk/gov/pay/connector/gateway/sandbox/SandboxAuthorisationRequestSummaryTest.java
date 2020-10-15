package uk.gov.pay.connector.gateway.sandbox;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_APPLICABLE;

class SandboxAuthorisationRequestSummaryTest {

    @Test
    void billingAddressAlwaysNotApplicable() {
        var sandboxAuthorisationRequestSummary = new SandboxAuthorisationRequestSummary();
        assertThat(sandboxAuthorisationRequestSummary.billingAddress(), is(NOT_APPLICABLE));
    }

    @Test
    void dataFor3dsAlwaysNotApplicable() {
        var sandboxAuthorisationRequestSummary = new SandboxAuthorisationRequestSummary();
        assertThat(sandboxAuthorisationRequestSummary.dataFor3ds(), is(NOT_APPLICABLE));
    }

    @Test
    void dataFor3ds2AlwaysNotApplicable() {
        var sandboxAuthorisationRequestSummary = new SandboxAuthorisationRequestSummary();
        assertThat(sandboxAuthorisationRequestSummary.dataFor3ds2(), is(NOT_APPLICABLE));
    }

    @Test
    void deviceDataCollectionResultAlwaysNotApplicable() {
        var sandboxAuthorisationRequestSummary = new SandboxAuthorisationRequestSummary();
        assertThat(sandboxAuthorisationRequestSummary.deviceDataCollectionResult(), is(NOT_APPLICABLE));
    }

}
