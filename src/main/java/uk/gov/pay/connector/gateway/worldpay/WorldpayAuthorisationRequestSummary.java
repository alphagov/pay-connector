package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_APPLICABLE;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

public class WorldpayAuthorisationRequestSummary implements AuthorisationRequestSummary {

    private final Presence billingAddress;
    private final Presence dataFor3ds;
    private final Presence deviceDataCollectionResult;
    private final Presence exemptionRequest;

    private WorldpayAuthorisationRequestSummary(ChargeEntity chargeEntity, AuthCardDetails authCardDetails, Presence exemptionRequest) {
        billingAddress = authCardDetails.getAddress().map(address -> PRESENT).orElse(NOT_PRESENT);
        deviceDataCollectionResult = authCardDetails.getWorldpay3dsFlexDdcResult().map(address -> PRESENT).orElse(NOT_PRESENT);
        GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
        dataFor3ds = (deviceDataCollectionResult == PRESENT || gatewayAccount.isRequires3ds()) ? PRESENT : NOT_PRESENT;
        this.exemptionRequest = exemptionRequest;
    }
    
    public static WorldpayAuthorisationRequestSummary summaryWithoutExemptionInformation(ChargeEntity chargeEntity, AuthCardDetails authCardDetails) {
        return new WorldpayAuthorisationRequestSummary(chargeEntity, authCardDetails, NOT_APPLICABLE);
    }
    
    public static WorldpayAuthorisationRequestSummary summaryWithExemptionInformation(ChargeEntity chargeEntity, 
                                                                                      AuthCardDetails authCardDetails, 
                                                                                      boolean exemptionEngineEnabled) {
        return new WorldpayAuthorisationRequestSummary(chargeEntity, authCardDetails, exemptionEngineEnabled ? PRESENT : NOT_PRESENT);
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
    public Presence exemptionRequest() {
        return exemptionRequest;
    }
}
