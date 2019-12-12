package uk.gov.pay.connector.gatewayaccount.service;

public enum IntegrationVersion3DS {
    ONE(1),
    TWO(2);

    private final int value;

    IntegrationVersion3DS(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
