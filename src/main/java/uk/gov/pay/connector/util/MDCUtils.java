package uk.gov.pay.connector.util;

import org.slf4j.MDC;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import static uk.gov.service.payments.logging.LoggingKeys.AGREEMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.AUTHORISATION_MODE;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class MDCUtils {
    public static void addChargeAndGatewayAccountDetailsToMDC(ChargeEntity charge) {
        MDC.put(PAYMENT_EXTERNAL_ID, charge.getExternalId());
        MDC.put(PROVIDER, charge.getPaymentProvider());
        MDC.put(AUTHORISATION_MODE, charge.getAuthorisationMode().getName());
        charge.getAgreementId().ifPresent(agreementId -> MDC.put(AGREEMENT_EXTERNAL_ID, agreementId));
        
        addGatewayAccountDetailsToMDC(charge.getGatewayAccount());
    }
    
    public static void addGatewayAccountDetailsToMDC(GatewayAccountEntity gatewayAccount) {
        MDC.put(GATEWAY_ACCOUNT_ID, gatewayAccount.getId().toString());
        MDC.put(GATEWAY_ACCOUNT_TYPE, gatewayAccount.getType());
    }

    public static void removeChargeAndGatewayAccountDetailsFromMDC() {
        MDC.remove(PAYMENT_EXTERNAL_ID);
        MDC.remove(PROVIDER);
        MDC.remove(AUTHORISATION_MODE);
        MDC.remove(AGREEMENT_EXTERNAL_ID);
        removeGatewayAccountDetailsFromMDC();
    }

    public static void removeGatewayAccountDetailsFromMDC() {
        MDC.remove(GATEWAY_ACCOUNT_ID);
        MDC.remove(GATEWAY_ACCOUNT_TYPE);
    }
}
