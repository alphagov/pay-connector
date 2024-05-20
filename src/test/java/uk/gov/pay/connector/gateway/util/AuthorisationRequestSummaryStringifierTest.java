package uk.gov.pay.connector.gateway.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_APPLICABLE;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

@ExtendWith(MockitoExtension.class)
class AuthorisationRequestSummaryStringifierTest {

    @Mock private AuthorisationRequestSummary mockAuthorisationRequestSummary;

    private final AuthorisationRequestSummaryStringifier stringifier = new AuthorisationRequestSummaryStringifier();

    @Test
    void stringifySummarises() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.exemptionRequest()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.ipAddress()).willReturn("1.1.1.1");

        String result = stringifier.stringify(mockAuthorisationRequestSummary);

        assertThat(result, is(" with billing address and with 3DS data and without device data collection result and with exemption and with remote IP 1.1.1.1"));
    }

    @Test
    void stringifySummarises_forSetUpAgreement() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.exemptionRequest()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.ipAddress()).willReturn("1.1.1.1");
        given(mockAuthorisationRequestSummary.setUpAgreement()).willReturn(PRESENT);

        String result = stringifier.stringify(mockAuthorisationRequestSummary);

        assertThat(result, is(" with set up agreement and with billing address and with 3DS data and without device data collection result and with exemption and with remote IP 1.1.1.1"));
    }

    @Test
    void stringifyWithEverythingNotApplicableReturnsSingleSpace() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.exemptionRequest()).willReturn(NOT_APPLICABLE);

        String result = stringifier.stringify(mockAuthorisationRequestSummary);

        assertThat(result, is(" "));
    }

}
