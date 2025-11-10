package uk.gov.pay.connector.gateway.util;

import net.logstash.logback.argument.StructuredArgument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.service.payments.commons.model.AgreementPaymentType;

import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.collection.ArrayMatching.arrayContaining;
import static org.hamcrest.collection.ArrayMatching.hasItemInArray;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_APPLICABLE;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.AGREEMENT_PAYMENT_TYPE;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.BILLING_ADDRESS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.CORPORATE_CARD;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.CORPORATE_EXEMPTION_REQUESTED;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.CORPORATE_EXEMPTION_RESULT;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.DATA_FOR_3DS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.DATA_FOR_3DS2;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.EMAIL;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.IP_ADDRESS;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.MOTO;
import static uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging.THREE_DS_REQUIRED;
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
        given(mockAuthorisationRequestSummary.corporateCard()).willReturn(true);
        given(mockAuthorisationRequestSummary.corporateExemptionRequested()).willReturn(Optional.of(Boolean.TRUE));
        given(mockAuthorisationRequestSummary.corporateExemptionResult()).willReturn(Optional.of(Exemption3ds.EXEMPTION_HONOURED));
        given(mockAuthorisationRequestSummary.email()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.agreementPaymentType()).willReturn(Optional.of(AgreementPaymentType.RECURRING));

        StructuredArgument[] result = structuredLogging.createArgs(mockAuthorisationRequestSummary);

        assertThat(result, arrayContaining(
                kv(BILLING_ADDRESS, true),
                kv(DATA_FOR_3DS, true),
                kv(DATA_FOR_3DS2, true),
                kv(EMAIL, true),
                kv(WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT, true),
                kv(IP_ADDRESS, "1.1.1.1"),
                kv(CORPORATE_CARD, true),
                kv(CORPORATE_EXEMPTION_REQUESTED, true),
                kv(CORPORATE_EXEMPTION_RESULT, Exemption3ds.EXEMPTION_HONOURED.name()),
                kv(AGREEMENT_PAYMENT_TYPE, AgreementPaymentType.RECURRING.name())
        ));
    }

    @Test
    void createArgsWithAllNotPresent() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.email()).willReturn(NOT_PRESENT);

        StructuredArgument[] result = structuredLogging.createArgs(mockAuthorisationRequestSummary);

        assertThat(result, is(arrayContaining(
                kv(BILLING_ADDRESS, false),
                kv(DATA_FOR_3DS, false),
                kv(DATA_FOR_3DS2, false),
                kv(EMAIL, false),
                kv(WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT, false),
                kv(CORPORATE_CARD, false)
        )));
    }

    @Test
    void createArgsWithAllNotApplicable() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.email()).willReturn(NOT_APPLICABLE);

        StructuredArgument[] result = structuredLogging.createArgs(mockAuthorisationRequestSummary);

        assertThat(result, is(arrayContaining(
                kv(CORPORATE_CARD, false)
        )));
    }

    @Test
    void createArgsWithMixture() {
        given(mockAuthorisationRequestSummary.billingAddress()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds()).willReturn(PRESENT);
        given(mockAuthorisationRequestSummary.dataFor3ds2()).willReturn(NOT_APPLICABLE);
        given(mockAuthorisationRequestSummary.deviceDataCollectionResult()).willReturn(NOT_PRESENT);
        given(mockAuthorisationRequestSummary.ipAddress()).willReturn("1.1.1.1");
        given(mockAuthorisationRequestSummary.email()).willReturn(PRESENT);

        StructuredArgument[] result = structuredLogging.createArgs(mockAuthorisationRequestSummary);

        assertThat(result, is(arrayContaining(
                kv(BILLING_ADDRESS, true),
                kv(DATA_FOR_3DS, true),
                kv(EMAIL, true),
                kv(WORLDPAY_3DS_FLEX_DEVICE_DATA_COLLECTION_RESULT, false),
                kv(IP_ADDRESS, "1.1.1.1"),
                kv(CORPORATE_CARD, false)
        )));
    }
    
    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, nullValues = "null", textBlock = """
    is_billing_address_present, payer_email, ip_address, three_ds_required, is_moto
    true, citizen@example.org, 1.1.1.1, true, true
    false, null, null, false, false
    """)
    void createArgsForPreAuthorisationLogging(boolean isBillingAddressPresent, String payerEmail, String ipAddress, boolean isThreeDsRequired, boolean isMoto) {
        WorldpayOrderRequestBuilder mockOrderRequestBuilder = mock(WorldpayOrderRequestBuilder.class);
        WorldpayOrderRequestBuilder.WorldpayTemplateData mockWorldpayTemplateData = mock(WorldpayOrderRequestBuilder.WorldpayTemplateData.class);
        given(mockOrderRequestBuilder.getWorldpayTemplateData()).willReturn(mockWorldpayTemplateData);
        AuthCardDetails mockAuthCardDetails = mock(AuthCardDetails.class);
        
        given(mockAuthCardDetails.getAddress()).willReturn(isBillingAddressPresent ? Optional.of(new Address()) : Optional.empty());
        given(mockOrderRequestBuilder.getWorldpayTemplateData().getPayerEmail()).willReturn(payerEmail);
        given(mockOrderRequestBuilder.getWorldpayTemplateData().getPayerIpAddress()).willReturn(ipAddress);
        given(mockOrderRequestBuilder.getWorldpayTemplateData().isRequires3ds()).willReturn(isThreeDsRequired);

        StructuredArgument[] result = structuredLogging.createArgsForPreAuthorisationLogging(mockOrderRequestBuilder, mockAuthCardDetails, isMoto);
        
        assertThat(result, is(hasItemInArray(kv(BILLING_ADDRESS, isBillingAddressPresent))));
        assertThat(result, is(hasItemInArray(kv(EMAIL, !isBlank(payerEmail)))));
        assertThat(result, is(hasItemInArray(kv(THREE_DS_REQUIRED, isThreeDsRequired))));
        assertThat(result, is(hasItemInArray(kv(MOTO, isMoto))));
                
        if(ipAddress != null) {
            assertThat(result, is(hasItemInArray(kv(IP_ADDRESS, ipAddress))));
        } else  {
            assertThat(result, is(not(hasItemInArray(kv(IP_ADDRESS, null)))));
        }
    }
}
