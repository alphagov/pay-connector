package uk.gov.pay.connector.charge.model;

public class DisplaySize extends PositiveLong {

    private DisplaySize(Long pageNumber) {
        super(pageNumber);
    }

    private DisplaySize(Long pageNumber, Long defaultValue) {
        super(pageNumber, defaultValue);
    }

    public static DisplaySize of(Long pageNumber) {
        return new DisplaySize(pageNumber);
    }

    public static DisplaySize ofDefault(Long positiveLong, Long defaultValue) {
        return new DisplaySize(positiveLong, defaultValue);
    }
}
