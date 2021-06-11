package uk.gov.pay.connector.gatewayaccount.resource;

import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.gatewayaccount.GatewayAccountSwitchPaymentProviderRequest;

import java.util.Collections;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.gatewayaccount.GatewayAccountSwitchPaymentProviderRequest.GATEWAY_ACCOUNT_CREDENTIAL_EXTERNAL_ID;
import static uk.gov.pay.connector.gatewayaccount.GatewayAccountSwitchPaymentProviderRequest.USER_EXTERNAL_ID_FIELD;

public class GatewayAccountSwitchPaymentProviderRequestValidator {

    public static void validate(GatewayAccountSwitchPaymentProviderRequest request) {
        if (isBlank(request.getUserExternalId())) {
            throw new ValidationException(Collections.singletonList(format("Field [%s] is required", USER_EXTERNAL_ID_FIELD)));
        }
        if (isBlank(request.getGACredentialExternalId())) {
            throw new ValidationException(Collections.singletonList(format("Field [%s] is required", GATEWAY_ACCOUNT_CREDENTIAL_EXTERNAL_ID)));
        }
    }
}
