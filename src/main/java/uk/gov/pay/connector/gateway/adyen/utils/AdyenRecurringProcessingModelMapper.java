package uk.gov.pay.connector.gateway.adyen.utils;

import uk.gov.service.payments.commons.model.AgreementPaymentType;

public final class AdyenRecurringProcessingModelMapper {

    private AdyenRecurringProcessingModelMapper() {}

    public static String fromAgreementPaymentType(AgreementPaymentType agreementPaymentType) {
        return switch (agreementPaymentType) {
            case RECURRING, INSTALMENT -> "Subscription";
            case UNSCHEDULED -> "UnscheduledCardOnFile";
        };
    }
}
