package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

public class WorldpayAuthorisationRequestSummary implements AuthorisationRequestSummary {

    private final Presence billingAddress;
    private final Presence dataFor3ds;
    private final Presence deviceDataCollectionResult;
    private String ipAddress;
    private Presence isSetupAgreement;

    public WorldpayAuthorisationRequestSummary(GatewayAccountEntity gatewayAccount, AuthCardDetails authCardDetails, boolean isSetupAgreement) {
        billingAddress = authCardDetails.getAddress().map(address -> PRESENT).orElse(NOT_PRESENT);
        deviceDataCollectionResult = authCardDetails.getWorldpay3dsFlexDdcResult().map(address -> PRESENT).orElse(NOT_PRESENT);
        dataFor3ds = (deviceDataCollectionResult == PRESENT || gatewayAccount.getCardConfigurationEntity().isRequires3ds()) ? PRESENT : NOT_PRESENT;
        ipAddress = authCardDetails.getIpAddress().orElse(null);
        this.isSetupAgreement = isSetupAgreement ? PRESENT: NOT_PRESENT;
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
    public Presence deviceDataCollectionResult() {
        return deviceDataCollectionResult;
    }

    @Override
    public String ipAddress() { 
        return ipAddress; 
    }

    @Override
    public Presence setUpAgreement() {
        return isSetupAgreement;
    }
}
