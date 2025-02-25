package uk.gov.pay.connector.gateway.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.BDDMockito.given;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_APPLICABLE;

@ExtendWith(MockitoExtension.class)
class SandboxAuthorisationRequestSummaryTest {

    @Mock
    private AuthCardDetails mockAuthCardDetails;

    @Test
    void billingAddressAlwaysNotApplicable() {
        var sandboxAuthorisationRequestSummary = new SandboxAuthorisationRequestSummary(mockAuthCardDetails);
        assertThat(sandboxAuthorisationRequestSummary.billingAddress(), is(NOT_APPLICABLE));
    }

    @Test
    void corporateCardUsed() {
        given(mockAuthCardDetails.isCorporateCard()).willReturn(true);
        var sandboxAuthorisationRequestSummary = new SandboxAuthorisationRequestSummary(mockAuthCardDetails);
        assertThat(sandboxAuthorisationRequestSummary.corporateCard(), is(true));
    }

    @Test
    void corporateCardNotUsed() {
        given(mockAuthCardDetails.isCorporateCard()).willReturn(false);
        var sandboxAuthorisationRequestSummary = new SandboxAuthorisationRequestSummary(mockAuthCardDetails);
        assertThat(sandboxAuthorisationRequestSummary.corporateCard(), is(false));
    }

    @Test
    void dataFor3dsAlwaysNotApplicable() {
        var sandboxAuthorisationRequestSummary = new SandboxAuthorisationRequestSummary(mockAuthCardDetails);
        assertThat(sandboxAuthorisationRequestSummary.dataFor3ds(), is(NOT_APPLICABLE));
    }

    @Test
    void dataFor3ds2AlwaysNotApplicable() {
        var sandboxAuthorisationRequestSummary = new SandboxAuthorisationRequestSummary(mockAuthCardDetails);
        assertThat(sandboxAuthorisationRequestSummary.dataFor3ds2(), is(NOT_APPLICABLE));
    }

    @Test
    void deviceDataCollectionResultAlwaysNotApplicable() {
        var sandboxAuthorisationRequestSummary = new SandboxAuthorisationRequestSummary(mockAuthCardDetails);
        assertThat(sandboxAuthorisationRequestSummary.deviceDataCollectionResult(), is(NOT_APPLICABLE));
    }

}
