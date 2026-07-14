package uk.gov.pay.connector.gateway.adyen.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class AdyenAuthorisationRejectedCodeMapperTest {


    @ParameterizedTest
    @CsvSource({
            "999, UNCATEGORISED, true",
            "2, GENERIC_DECLINE, true",
            "3,REFER_TO_CARD_ISSUER, true",
            "4,ISSUER_TEMPORARILY_UNAVAILABLE, true",
            "5,CARD_BLOCKED_OR_INVALID_OR_NONEXISTENT, true",
            "6,EXPIRED_CARD, false",
            "7,INVALID_AMOUNT, true",
            "8,INVALID_CARD_NUMBER,false",
            "9,ISSUER_TEMPORARILY_UNAVAILABLE, true",
            "10,TRANSACTION_NOT_PERMITTED, false",
            "11,AUTHENTICATION_REQUIRED, false",
            "12,INSUFFICIENT_FUNDS, true",
            "14,SUSPECTED_FRAUD, true",
            "15,CANCELLED, true",
            "16,CANCELLED, true",
            "20,SUSPECTED_FRAUD, true",
            "21, REENTER_TRANSACTION, true",
            "22,SUSPECTED_FRAUD, true",
            "23,TRANSACTION_NOT_PERMITTED, false",
            "24,INVALID_CVV2, false",
            "25,RESTRICTED_CARD, false",
            "26,REVOCATION_OF_AUTHORISATION, false",
            "27,GENERIC_DECLINE, true",
            "28,EXCEEDS_WITHDRAWAL_AMOUNT_LIMIT, true",
            "29,EXCEEDS_WITHDRAWAL_COUNT_LIMIT, true",
            "31,SUSPECTED_FRAUD, true",
            "32,INVALID_CVV2, false",
            "33,AUTHENTICATION_REQUESTED, true",
            "34,CARD_BLOCKED_OR_INVALID_OR_NONEXISTENT, true",
            "35,CARD_BLOCKED_OR_INVALID_OR_NONEXISTENT, true",
            "38,AUTHENTICATION_REQUIRED, false",
            "39,TRY_AGAIN_LATER, true",
            "40,TRY_AGAIN_LATER, true",
            "41,TRY_AGAIN_LATER, true",
            "42,AUTHENTICATION_REQUIRED, false",
            "46,DO_NOT_RETRY, false",
            "50,TRANSACTION_NOT_PERMITTED, false"
    })
    void should_map_refusalReasonCode_to_MappedAuthorisationRejectedReason(String refusalReasonCode,
                                                                           String mappedRefusalReason,
                                                                           boolean canRetry) {
        var mappedAuthorisationRejectedReason = AdyenAuthorisationRejectedCodeMapper
                .toMappedAuthorisationRejectionReason(refusalReasonCode);

        assertThat(mappedAuthorisationRejectedReason.name(), is(mappedRefusalReason));
        assertThat(mappedAuthorisationRejectedReason.canRetry(), is(canRetry));
    }
}
