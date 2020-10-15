package uk.gov.pay.connector.gateway.stripe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_APPLICABLE;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

@ExtendWith(MockitoExtension.class)
class StripeAuthorisationRequestSummaryTest {

    @Mock private AuthCardDetails mockAuthCardDetails;

    @Test
    void billingAddressPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.of(mock(Address.class)));
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockAuthCardDetails);
        assertThat(stripeAuthorisationRequestSummary.billingAddress(), is(PRESENT));
    }

    @Test
    void billingAddressNotPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.empty());
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockAuthCardDetails);
        assertThat(stripeAuthorisationRequestSummary.billingAddress(), is(NOT_PRESENT));
    }

    @Test
    void dataFor3dsAlwaysNotApplicable() {
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockAuthCardDetails);
        assertThat(stripeAuthorisationRequestSummary.dataFor3ds(), is(NOT_APPLICABLE));
    }

    @Test
    void dataFor3ds2AlwaysNotApplicable() {
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockAuthCardDetails);
        assertThat(stripeAuthorisationRequestSummary.dataFor3ds2(), is(NOT_APPLICABLE));
    }

    @Test
    void deviceDataCollectionResultAlwaysNotApplicable() {
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockAuthCardDetails);
        assertThat(stripeAuthorisationRequestSummary.deviceDataCollectionResult(), is(NOT_APPLICABLE));
    }

}
