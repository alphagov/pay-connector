package uk.gov.pay.connector.gateway.sandbox;

public interface SandboxCardNumbers {

    boolean isValidCard(String cardNumber);

    boolean isRejectedCard(String cardNumber);

    boolean isErrorCard(String cardNumber);

    CardError cardErrorFor(String cardNumber);

}
