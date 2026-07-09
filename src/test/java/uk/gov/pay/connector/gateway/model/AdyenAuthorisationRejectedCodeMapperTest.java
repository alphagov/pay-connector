package uk.gov.pay.connector.gateway.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class AdyenAuthorisationRejectedCodeMapperTest {

    @Test
    void shouldBeUncategorisedIfAdyenRefusalReasonCodeNotRecognised() {
        MappedAuthorisationRejectedReason mappedAuthorisationRejectedReason = AdyenAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason("999");
        assertThat(mappedAuthorisationRejectedReason.name(), is("UNCATEGORISED"));
    }

    @Test
    void shouldBeCategorisedCorrectlyIfAdyenRefusalReasonCodeIsRecognised() {
        MappedAuthorisationRejectedReason mappedAuthorisationRejectedReason = AdyenAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason("6");
        assertThat(mappedAuthorisationRejectedReason.name(), is("EXPIRED_CARD"));
    }

    @Test
    void shouldMapExpiredCardCode() {
        MappedAuthorisationRejectedReason reason = AdyenAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason("6");
        assertThat(reason, is(MappedAuthorisationRejectedReason.EXPIRED_CARD));
        assertThat(reason.canRetry(), is(false));
    }

    @Test
    void shouldMapInsufficientFundsCode() {
        MappedAuthorisationRejectedReason reason = AdyenAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason("12");
        assertThat(reason, is(MappedAuthorisationRejectedReason.INSUFFICIENT_FUNDS));
        assertThat(reason.canRetry(), is(true));
    }

    @Test
    void shouldMapAuthenticationRequiredCode() {
        MappedAuthorisationRejectedReason reason = AdyenAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason("38");
        assertThat(reason, is(MappedAuthorisationRejectedReason.AUTHENTICATION_REQUIRED));
        assertThat(reason.canRetry(), is(false));
    }

    @Test
    void shouldMapIssuerUnavailableCode() {
        MappedAuthorisationRejectedReason reason = AdyenAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason("4");
        assertThat(reason, is(MappedAuthorisationRejectedReason.ISSUER_TEMPORARILY_UNAVAILABLE));
        assertThat(reason.canRetry(), is(true));
    }

    @Test
    void shouldMapDoNotRetryCode() {
        MappedAuthorisationRejectedReason reason = AdyenAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason("46");
        assertThat(reason, is(MappedAuthorisationRejectedReason.DO_NOT_RETRY));
        assertThat(reason.canRetry(), is(false));
    }
}
