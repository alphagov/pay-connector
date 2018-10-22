package uk.gov.pay.connector.charge.model;

public class PageNumber extends PositiveLong {

    private PageNumber(Long pageNumber) {
        super(pageNumber);
    }

    private PageNumber(Long pageNumber, Long defaultValue) {
        super(pageNumber, defaultValue);
    }

    public static PageNumber of(Long pageNumber) {
        return new PageNumber(pageNumber);
    }

    public static PageNumber ofDefault(Long positiveLong, Long defaultValue) {
        return new PageNumber(positiveLong, defaultValue);
    }
    
}
