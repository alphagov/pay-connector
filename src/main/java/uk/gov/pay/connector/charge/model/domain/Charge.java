package uk.gov.pay.connector.charge.model.domain;

public class Charge {

    private String externalId;
    private Long amount;
    private String status;
    private String gatewayTransactionId;
    private Long corporateSurcharge;

    public Charge(String externalId, Long amount, String status, String gatewayTransactionId, Long corporateSurcharge) {
        this.externalId = externalId;
        this.amount = amount;
        this.status = status;
        this.gatewayTransactionId = gatewayTransactionId;
        this.corporateSurcharge = corporateSurcharge;
    }

    public String getExternalId() {
        return externalId;
    }

    public Long getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public Long getCorporateSurcharge() {
        return corporateSurcharge;
    }
}
