package uk.gov.pay.connector.model.domain.report;

public class PerformanceReportEntity {
    private final long totalVolume;
    private final long totalAmount;
    private final double averageAmount;

    public PerformanceReportEntity(long totalVolume, long totalAmount, double averageAmount) {
        this.totalVolume = totalVolume;
        this.totalAmount = totalAmount;
        this.averageAmount = averageAmount;
    }

    public long getTotalVolume() {
        return totalVolume;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public double getAverageAmount() {
        return averageAmount;
    }
}
