package uk.gov.pay.connector.report.model.domain;

import java.math.BigDecimal;

public class GatewayAccountPerformanceReportEntity {
    private final Long totalVolume;
    private final BigDecimal totalAmount;
    private final BigDecimal averageAmount;
    private final Long minAmount;
    private final Long maxAmount;
    private final Long gatewayAccountId;

    public GatewayAccountPerformanceReportEntity(Long totalVolume, BigDecimal totalAmount, BigDecimal averageAmount, Long minAmount, Long maxAmount, Long gatewayAccountId) {
        this.totalVolume = totalVolume;
        this.totalAmount = totalAmount;
        this.averageAmount = averageAmount;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.gatewayAccountId = gatewayAccountId;
    }

    public Long getTotalVolume() {
        return totalVolume;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getAverageAmount() {
        return averageAmount;
    }

    public Long getMinAmount() {
        return minAmount;
    }

    public Long getMaxAmount() {
        return maxAmount;
    }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }
}
