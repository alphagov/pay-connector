package uk.gov.pay.connector.gateway.adyen;

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
class AdyenAuthorisationRequestSummaryTest {

    @Mock
    private AuthCardDetails mockAuthCardDetails;

    @Test
    void billingAddressPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.of(mock(Address.class)));
        var adyenAuthorisationRequestSummary = new AdyenAuthorisationRequestSummary(mockAuthCardDetails, false);
        assertThat(adyenAuthorisationRequestSummary.billingAddress(), is(PRESENT));
    }

    @Test
    void billingAddressNotPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.empty());
        var adyenAuthorisationRequestSummary = new AdyenAuthorisationRequestSummary(mockAuthCardDetails, false);
        assertThat(adyenAuthorisationRequestSummary.billingAddress(), is(NOT_PRESENT));
    }

    @Test
    void dataFor3dsAlwaysNotApplicable() {
        var adyenAuthorisationRequestSummary = new AdyenAuthorisationRequestSummary(mockAuthCardDetails, false);
        assertThat(adyenAuthorisationRequestSummary.dataFor3ds(), is(NOT_APPLICABLE));
    }

    @Test
    void dataFor3ds2AlwaysNotApplicable() {
        var adyenAuthorisationRequestSummary = new AdyenAuthorisationRequestSummary(mockAuthCardDetails, false);
        assertThat(adyenAuthorisationRequestSummary.dataFor3ds2(), is(NOT_APPLICABLE));
    }

    @Test
    void deviceDataCollectionResultAlwaysNotApplicable() {
        var adyenAuthorisationRequestSummary = new AdyenAuthorisationRequestSummary(mockAuthCardDetails, false);
        assertThat(adyenAuthorisationRequestSummary.deviceDataCollectionResult(), is(NOT_APPLICABLE));
    }

    @Test
    void isSetUpAgreementPresent() {
        var adyenAuthorisationRequestSummary = new AdyenAuthorisationRequestSummary(mockAuthCardDetails, true);
        assertThat(adyenAuthorisationRequestSummary.setUpAgreement(), is(PRESENT));
    }

    @Test
    void isSetUpAgreementNotPresent() {
        var adyenAuthorisationRequestSummary = new AdyenAuthorisationRequestSummary(mockAuthCardDetails, false);
        assertThat(adyenAuthorisationRequestSummary.setUpAgreement(), is(NOT_PRESENT));
    }

    @Test
    void corporateCardUsed() {
        given(mockAuthCardDetails.isCorporateCard()).willReturn(true);
        var adyenAuthorisationRequestSummary = new AdyenAuthorisationRequestSummary(mockAuthCardDetails, false);
        assertThat(adyenAuthorisationRequestSummary.corporateCard(), is(true));
    }

    @Test
    void corporateCardNotUsed() {
        given(mockAuthCardDetails.isCorporateCard()).willReturn(false);
        var adyenAuthorisationRequestSummary = new AdyenAuthorisationRequestSummary(mockAuthCardDetails, false);
        assertThat(adyenAuthorisationRequestSummary.corporateCard(), is(false));
    }

}
