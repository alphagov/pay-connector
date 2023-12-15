package uk.gov.pay.connector.gateway;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum PaymentGatewayName {
    SANDBOX("sandbox"), SMARTPAY("smartpay"), WORLDPAY("worldpay"), EPDQ("epdq"), STRIPE("stripe");

    private final String gatewayName;
    
    private static final Set<String> UNSUPPORTED = Set.of(SMARTPAY.gatewayName, EPDQ.gatewayName);

    private static final Set<String> PAYMENT_GATEWAY_NAMES = EnumSet.allOf(PaymentGatewayName.class).stream().map(x -> x.gatewayName).collect(Collectors.toSet());

    PaymentGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    public String getName() {
        return gatewayName;
    }
    
    public static boolean isUnsupported(String gatewayName) {
        return UNSUPPORTED.contains(gatewayName);
    }

    public static class Unsupported extends RuntimeException {
        public Unsupported() { super(); }
        public Unsupported(String msg) { super(msg); }
    }

    public static boolean isValidPaymentGateway(String name) {
        try {
            valueFrom(name);
            return true;
        } catch (Unsupported e){
            return false;
        }
    }

    public static PaymentGatewayName valueFrom(String gatewayName) {
        if (PAYMENT_GATEWAY_NAMES.contains(gatewayName)) {
            return PaymentGatewayName.valueOf(gatewayName.toUpperCase());
        }
        throw new Unsupported("Unsupported Payment Gateway " + gatewayName);
    }
}
