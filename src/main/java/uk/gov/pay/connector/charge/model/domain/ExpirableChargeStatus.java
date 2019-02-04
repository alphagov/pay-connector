package uk.gov.pay.connector.charge.model.domain;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum ExpirableChargeStatus {
    CREATED(ChargeStatus.CREATED, AuthorisationStage.PRE_AUTHORISATION, ExpiryThresholdType.REGULAR),
    ENTERING_CARD_DETAILS(ChargeStatus.ENTERING_CARD_DETAILS, AuthorisationStage.PRE_AUTHORISATION, ExpiryThresholdType.REGULAR),
    AUTHORISATION_3DS_REQUIRED(ChargeStatus.AUTHORISATION_3DS_REQUIRED, AuthorisationStage.DURING_AUTHORISATION, ExpiryThresholdType.REGULAR),
    AUTHORISATION_3DS_READY(ChargeStatus.AUTHORISATION_3DS_READY, AuthorisationStage.DURING_AUTHORISATION, ExpiryThresholdType.REGULAR),
    AUTHORISATION_SUCCESS(ChargeStatus.AUTHORISATION_SUCCESS, AuthorisationStage.POST_AUTHORISATION, ExpiryThresholdType.REGULAR),
    AWAITING_CAPTURE_REQUEST(ChargeStatus.AWAITING_CAPTURE_REQUEST, AuthorisationStage.POST_AUTHORISATION, ExpiryThresholdType.DELAYED);
    
    ExpirableChargeStatus(ChargeStatus chargeStatus, AuthorisationStage authorisationStage, ExpiryThresholdType expiryThresholdType) {
        this.chargeStatus = chargeStatus;
        this.authorisationStage = authorisationStage;
        this.expiryThresholdType = expiryThresholdType;
    }
    private final AuthorisationStage authorisationStage;
    private final ChargeStatus chargeStatus;
    private final ExpiryThresholdType expiryThresholdType;

    public static Stream<ExpirableChargeStatus> getValuesAsStream() {
        return Arrays.stream(values());
    }
    
    public static ExpirableChargeStatus of(ChargeStatus chargeStatus) {
        return getValuesAsStream()
                .filter(expirableChargeStatus -> expirableChargeStatus.getChargeStatus().equals(chargeStatus))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException(String.format("No expiry status corresponds to charge status %s", chargeStatus)));
    }

    public AuthorisationStage getAuthorisationStage() {
        return authorisationStage;
    }

    public ChargeStatus getChargeStatus() {
        return chargeStatus;
    }

    public boolean isRegularThresholdType() {
        return expiryThresholdType.equals(ExpiryThresholdType.REGULAR);
    }
    
    public boolean isDelayedThresholdType() {
        return expiryThresholdType.equals(ExpiryThresholdType.DELAYED);
    }

    public enum ExpiryThresholdType {
        REGULAR ,
        DELAYED 
    }
    
    public enum AuthorisationStage {
        PRE_AUTHORISATION,
        DURING_AUTHORISATION,
        POST_AUTHORISATION
    }
}
