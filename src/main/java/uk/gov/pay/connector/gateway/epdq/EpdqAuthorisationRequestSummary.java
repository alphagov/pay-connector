package uk.gov.pay.connector.gateway.epdq;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

public class EpdqAuthorisationRequestSummary implements AuthorisationRequestSummary {

    private final Presence billingAddress;
    private final Presence dataFor3ds;
    private final Presence dataFor3ds2;
    private final String ipAddress;

    public EpdqAuthorisationRequestSummary(GatewayAccountEntity gatewayAccount, AuthCardDetails authCardDetails) {
        billingAddress = authCardDetails.getAddress().map(address -> PRESENT).orElse(NOT_PRESENT);
        dataFor3ds = gatewayAccount.getCardConfigurationEntity().isRequires3ds() ? PRESENT : NOT_PRESENT;
        dataFor3ds2 = (gatewayAccount.getCardConfigurationEntity().isRequires3ds()
                && gatewayAccount.getCardConfigurationEntity().getIntegrationVersion3ds() == 2) ? PRESENT : NOT_PRESENT;
        ipAddress = authCardDetails.getIpAddress().orElse(null);
    }

    @Override
    public Presence billingAddress() {
        return billingAddress;
    }

    @Override
    public Presence dataFor3ds() {
        return dataFor3ds;
    }

    @Override
    public Presence dataFor3ds2() {
        return dataFor3ds2;
    }

    @Override
    public String ipAddress() { 
        return ipAddress; 
    }
}
