package uk.gov.pay.connector.gateway.stripe.model;

import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.Strings;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.util.Arrays;

public enum StripeChargeStatus {
    CANCELED("canceled"),
    PROCESSING("processing"),
    REQUIRES_ACTION("requires_action"),
    REQUIRES_CAPTURE("requires_capture"),
    REQUIRES_CONFIRMATION("requires_confirmation"),
    REQUIRES_PAYMENT_METHOD("requires_payment_method"),
    SUCCEEDED("succeeded");

    private String name;

    StripeChargeStatus(String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() {
        return name;
    }

    public static StripeChargeStatus fromString(String stripeStatusValue) {
        return Arrays.stream(StripeChargeStatus.values())
                .filter(stripeChargeStatusEnum -> Strings.CS.equals(stripeChargeStatusEnum.getName(), stripeStatusValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stripe charge status not recognized: " + stripeStatusValue));
    }

    public static ChargeStatus mapToChargeStatus(StripeChargeStatus stripeChargeStatus) {
        switch (stripeChargeStatus) {
            case CANCELED:
                return ChargeStatus.AUTHORISATION_CANCELLED;
            case PROCESSING:
                return ChargeStatus.CAPTURE_SUBMITTED;
            case REQUIRES_ACTION:
                return ChargeStatus.AUTHORISATION_3DS_REQUIRED;
            case REQUIRES_CAPTURE:
            case REQUIRES_CONFIRMATION:
                return ChargeStatus.AUTHORISATION_SUCCESS;
            case REQUIRES_PAYMENT_METHOD:
                return ChargeStatus.AUTHORISATION_REJECTED;
            case SUCCEEDED:
                return ChargeStatus.CAPTURED;
            default:
                return ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
        }
    }
}
