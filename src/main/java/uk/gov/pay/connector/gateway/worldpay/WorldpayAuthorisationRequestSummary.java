package uk.gov.pay.connector.gateway.worldpay;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.service.payments.commons.model.AgreementPaymentType;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

public class WorldpayAuthorisationRequestSummary implements AuthorisationRequestSummary {

    private final Presence billingAddress;
    private final Presence dataFor3ds;
    private final Presence deviceDataCollectionResult;
    private final String ipAddress;
    private final Presence isSetupAgreement;
    private final boolean isCorporateCard;
    private final boolean isCorporateExemptionRequested;
    private Exemption3ds corporateExemptionResult;
    private final Presence email;
    private final AgreementPaymentType agreementPaymentType;

    public WorldpayAuthorisationRequestSummary(ChargeEntity chargeEntity, AuthCardDetails authCardDetails, boolean isSetupAgreement) {
        billingAddress = authCardDetails.getAddress().map(address -> PRESENT).orElse(NOT_PRESENT);
        deviceDataCollectionResult = authCardDetails.getWorldpay3dsFlexDdcResult().map(address -> PRESENT).orElse(NOT_PRESENT);
        dataFor3ds = (deviceDataCollectionResult == PRESENT || chargeEntity.getGatewayAccount().isRequires3ds()) ? PRESENT : NOT_PRESENT;
        ipAddress = authCardDetails.getIpAddress().orElse(null);
        isCorporateCard = authCardDetails.isCorporateCard();
        this.isSetupAgreement = isSetupAgreement ? PRESENT: NOT_PRESENT;
        isCorporateExemptionRequested = chargeEntity.getExemption3dsRequested() == Exemption3dsType.CORPORATE;
        agreementPaymentType = chargeEntity.getAgreementPaymentType();
        if (isCorporateExemptionRequested) {
            corporateExemptionResult = chargeEntity.getExemption3ds();
        }
        email = StringUtils.isBlank(chargeEntity.getEmail()) ? PRESENT : NOT_PRESENT;
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

    @Override
    public boolean corporateCard() {
        return isCorporateCard;
    }

    @Override
    public Optional<Boolean> corporateExemptionRequested() {
        return Optional.of(isCorporateExemptionRequested);
    }

    @Override
    public Optional<Exemption3ds> corporateExemptionResult() {
        return Optional.ofNullable(corporateExemptionResult);
    }
    
    @Override
    public Presence email() { return email; }
    
    @Override
    public Optional<AgreementPaymentType> agreementPaymentType() { 
        return Optional.ofNullable(agreementPaymentType); 
    }
}
