package uk.gov.pay.connector.gateway.util;

import net.logstash.logback.argument.StructuredArgument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.ArrayMatching.arrayContaining;
import static org.hamcrest.collection.IsArrayWithSize.emptyArray;
import static org.mockito.BDDMockito.given;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_APPLICABLE;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.BILLING_ADDRESS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.DATA_FOR_3DS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.DATA_FOR_3DS2;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.IP_ADDRESS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT;

@ExtendWith(MockitoExtension.class)
class AuthorisationRequestSummaryStructuredLoggingTest {

    @Mock private AuthorisationRequestSummary mockAuthorisationRequestSummary;

    private final AuthorisationRequestSummaryStructuredLogging structuredLogging = new AuthorisationRequestSummaryStructuredLogging();

    @Test
    void createArgsWithAllPresent() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.ipAddress()).willReturn("1.1.1.1");

        StructuredArgument[] result = structuredLogging.createArgs(mockAuthorisationRequestSummary);

        assertThat(result, arrayContaining(
                kv(BILLING_ADDRESS, true),
                kv(DATA_FOR_3DS, true),
                kv(DATA_FOR_3DS2, true),
                kv(WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT, true),
                kv(IP_ADDRESS, "1.1.1.1")
        ));
    }

    @Test
    void createArgsWithAllNotPresent() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_PRESENT);

        StructuredArgument[] result = structuredLogging.createArgs(mockAuthorisationRequestSummary);

        assertThat(result, is(arrayContaining(
                kv(BILLING_ADDRESS, false),
                kv(DATA_FOR_3DS, false),
                kv(DATA_FOR_3DS2, false),
                kv(WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT, false)
        )));
    }

    @Test
    void createArgsWithAllNotApplicable() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_APPLICABLE);

        StructuredArgument[] result = structuredLogging.createArgs(mockAuthorisationRequestSummary);

        assertThat(result, is(emptyArray()));
    }

    @Test
    void createArgsWithMixture() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.ipAddress()).willReturn("1.1.1.1");

        StructuredArgument[] result = structuredLogging.createArgs(mockAuthorisationRequestSummary);

        assertThat(result, is(arrayContaining(
                kv(BILLING_ADDRESS, true),
                kv(DATA_FOR_3DS, true),
                kv(WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT, false),
                kv(IP_ADDRESS, "1.1.1.1")
        )));
    }

}
