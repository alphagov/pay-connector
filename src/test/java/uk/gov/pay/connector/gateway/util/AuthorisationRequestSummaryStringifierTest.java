package uk.gov.pay.connector.gateway.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_APPLICABLE;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

@ExtendWith(MockitoExtension.class)
class AuthorisationRequestSummaryStringifierTest {

    @Mock
    private AuthorisationRequestSummary mockAuthorisationRequestSummary;

    private final AuthorisationRequestSummaryStringifier stringifier = new AuthorisationRequestSummaryStringifier();

    @Test
    void stringifySummarises() {
        given(mockAuthorisationRequestSummary.setUpAgreement()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.email()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.corporateCard()).willReturn(true);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.ipAddress()).willReturn("1.1.1.1");

        String result = stringifier.stringify(mockAuthorisationRequestSummary);

        assertThat(result, is(" with set up agreement and with billing address and with email address and with corporate card and with 3DS data and without device data collection result and with remote IP 1.1.1.1"));
    }

    @Test
    void stringifySummarisesWithoutEmail() {
        given(mockAuthorisationRequestSummary.setUpAgreement()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.email()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.corporateCard()).willReturn(true);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.ipAddress()).willReturn("1.1.1.1");

        String result = stringifier.stringify(mockAuthorisationRequestSummary);

        assertThat(result, is(" with set up agreement and with billing address and without email address and with corporate card and with 3DS data and without device data collection result and with remote IP 1.1.1.1"));
    }

    @Test
    void stringifySummariesCorporateExemptionHonoured() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.email()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.corporateCard()).willReturn(true);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.corporateExemptionRequested()).willReturn(Optional.of(Boolean.TRUE));
        given(mockAuthorisationRequestSummary.corporateExemptionResult()).willReturn(Optional.of(Exemption3ds.EXEMPTION_HONOURED));

        String result = stringifier.stringify(mockAuthorisationRequestSummary);

        assertThat(result, is(" with corporate card and with corporate exemption requested and honoured"));
    }

    @Test
    void stringifySummariesCorporateExemptionRejected() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.email()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.corporateCard()).willReturn(true);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.corporateExemptionRequested()).willReturn(Optional.of(Boolean.TRUE));
        given(mockAuthorisationRequestSummary.corporateExemptionResult()).willReturn(Optional.of(Exemption3ds.EXEMPTION_REJECTED));

        String result = stringifier.stringify(mockAuthorisationRequestSummary);

        assertThat(result, is(" with corporate card and with corporate exemption requested and rejected"));
    }

    @Test
    void stringifySummariesCorporateExemptionOutOfScope() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.email()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.corporateCard()).willReturn(true);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.corporateExemptionRequested()).willReturn(Optional.of(Boolean.TRUE));
        given(mockAuthorisationRequestSummary.corporateExemptionResult()).willReturn(Optional.of(Exemption3ds.EXEMPTION_OUT_OF_SCOPE));

        String result = stringifier.stringify(mockAuthorisationRequestSummary);

        assertThat(result, is(" with corporate card and with corporate exemption requested and out of scope"));
    }

    @Test
    void stringifyWithEverythingNotApplicableReturnsSingleSpace() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.email()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_APPLICABLE);

        String result = stringifier.stringify(mockAuthorisationRequestSummary);

        assertThat(result, is(" "));
    }

}
