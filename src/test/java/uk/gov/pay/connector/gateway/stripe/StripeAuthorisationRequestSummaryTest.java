package uk.gov.pay.connector.gateway.stripe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
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

    @Mock private ChargeEntity mockChargeEntity;
    @Mock private AuthCardDetails mockAuthCardDetails;

    @Test
    void billingAddressPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.of(mock(Address.class)));
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(stripeAuthorisationRequestSummary.billingAddress(), is(PRESENT));
    }

    @Test
    void billingAddressNotPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.empty());
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(stripeAuthorisationRequestSummary.billingAddress(), is(NOT_PRESENT));
    }

    @Test
    void dataFor3dsAlwaysNotApplicable() {
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(stripeAuthorisationRequestSummary.dataFor3ds(), is(NOT_APPLICABLE));
    }

    @Test
    void dataFor3ds2AlwaysNotApplicable() {
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(stripeAuthorisationRequestSummary.dataFor3ds2(), is(NOT_APPLICABLE));
    }

    @Test
    void deviceDataCollectionResultAlwaysNotApplicable() {
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(stripeAuthorisationRequestSummary.deviceDataCollectionResult(), is(NOT_APPLICABLE));
    }

    @Test
    void isSetUpAgreementPresent() {
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, true);
        assertThat(stripeAuthorisationRequestSummary.setUpAgreement(), is(PRESENT));
    }

    @Test
    void isSetUpAgreementNotPresent() {
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(stripeAuthorisationRequestSummary.setUpAgreement(), is(NOT_PRESENT));
    }

    @Test
    void corporateCardUsed() {
        given(mockAuthCardDetails.isCorporateCard()).willReturn(true);
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(stripeAuthorisationRequestSummary.corporateCard(), is(true));
    }

    @Test
    void corporateCardNotUsed() {
        given(mockAuthCardDetails.isCorporateCard()).willReturn(false);
        var stripeAuthorisationRequestSummary = new StripeAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(stripeAuthorisationRequestSummary.corporateCard(), is(false));
    }

}
