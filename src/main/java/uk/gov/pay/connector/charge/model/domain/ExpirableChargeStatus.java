package uk.gov.pay.connector.charge.model.domain;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum ExpirableChargeStatus {
    CREATED(ChargeStatus.CREATED, false, ExpiryThresholdType.REGULAR),
    ENTERING_CARD_DETAILS(ChargeStatus.ENTERING_CARD_DETAILS, false, ExpiryThresholdType.REGULAR),
    AUTHORISATION_3DS_READY(ChargeStatus.AUTHORISATION_3DS_READY, false, ExpiryThresholdType.REGULAR),
    AUTHORISATION_3DS_REQUIRED(ChargeStatus.AUTHORISATION_3DS_REQUIRED, false, ExpiryThresholdType.REGULAR),
    AUTHORISATION_SUCCESS(ChargeStatus.AUTHORISATION_SUCCESS, true, ExpiryThresholdType.REGULAR),
    AWAITING_CAPTURE_REQUEST(ChargeStatus.AWAITING_CAPTURE_REQUEST, true, ExpiryThresholdType.DELAYED);
    
    ExpirableChargeStatus(ChargeStatus chargeStatus, boolean expireWithGateway, ExpiryThresholdType expiryThresholdType) {
        this.chargeStatus = chargeStatus;
        this.expireWithGateway = expireWithGateway;
        this.expiryThresholdType = expiryThresholdType;
    }
    private final boolean expireWithGateway;
    private final ChargeStatus chargeStatus;
    private final ExpiryThresholdType expiryThresholdType;

    public static Stream<ExpirableChargeStatus> getValuesAsStream() {
        return Arrays.stream(values());
    }
    
    public static ExpirableChargeStatus of(ChargeStatus chargeStatus) {
        return Arrays.stream(values())
                .filter(expirableChargeStatus -> expirableChargeStatus.getChargeStatus().equals(chargeStatus))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException(String.format("No expiry status corresponds to charge status %s", chargeStatus)));
    }

    public boolean shouldExpireWithGateway() {
        return expireWithGateway;
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
}
