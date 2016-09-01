package uk.gov.pay.connector.resources;

public enum PaymentGatewayName {
    SANDBOX("sandbox"), SMARTPAY("smartpay"), WORLDPAY("worldpay");

    private String name;

    PaymentGatewayName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static boolean isValidPaymentGateway(String name) {
        try {
            valueFrom(name);
            return true;
        }catch (RuntimeException e){
            return false;
        }
    }

    public static PaymentGatewayName valueFrom(String name) {
        for (PaymentGatewayName paymentGatewayName : values()) {
            if (paymentGatewayName.getName().equals(name)) {
                return paymentGatewayName;
            }
        }
        throw new RuntimeException("Unsupported PaymentProvider " + name);
    }
}
