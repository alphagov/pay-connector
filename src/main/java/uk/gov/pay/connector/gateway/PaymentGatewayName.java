package uk.gov.pay.connector.gateway;

import java.util.List;

public enum PaymentGatewayName {
    SANDBOX("sandbox"), SMARTPAY("smartpay"), WORLDPAY("worldpay"), EPDQ("epdq"), STRIPE("stripe");

    private final String gatewayName;
    
    private static List<String> unsupported = List.of(SMARTPAY.gatewayName, EPDQ.gatewayName);

    PaymentGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    public String getName() {
        return gatewayName;
    }
    
    public static boolean isUnsupported(String gatewayName) {
        return unsupported.contains(gatewayName);
    }

    public static class Unsupported extends RuntimeException {
        public Unsupported() { super(); }
        public Unsupported(String msg) { super(msg); }
    }

    public static boolean isValidPaymentGateway(String name) {
        try {
            valueFrom(name);
            return true;
        } catch (RuntimeException e){
            return false;
        }
    }

    public static PaymentGatewayName valueFrom(String gatewayName) {
        for (PaymentGatewayName paymentGatewayName : values()) {
            if (paymentGatewayName.getName().equals(gatewayName)) {
                return paymentGatewayName;
            }
        }
        throw new Unsupported("Unsupported Payment Gateway " + gatewayName);
    }
}
