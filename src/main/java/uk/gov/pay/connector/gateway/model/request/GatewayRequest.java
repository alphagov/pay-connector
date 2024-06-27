package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Optional;

public interface GatewayRequest {
    GatewayAccountEntity gatewayAccount();

    GatewayOperation requestType();

    GatewayCredentials gatewayCredentials();

    AuthorisationMode authorisationMode();
    
    boolean isForRecurringPayment();
}
