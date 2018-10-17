package uk.gov.pay.connector.charge.model;

import uk.gov.pay.connector.exception.ValidationException;

public class PageNumber {

    private Long pageNumber;

    public PageNumber(Long pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Long getPageNumber() {
        return pageNumber;
    }

    private static boolean isValid(Long pageNumber) {
        return pageNumber > 0;
    }

    public static PageNumber of(Long pageNumber) {
        if (!(isValid(pageNumber))) {
            throw new ValidationException("PageNumber is invalid");

        }
        return new PageNumber(pageNumber);
    }

    @Override
    public String toString() {
        return String.valueOf(pageNumber);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PageNumber that = (PageNumber) o;

        return pageNumber != null ? pageNumber.equals(that.pageNumber) : that.pageNumber == null;
    }

    @Override
    public int hashCode() {
        return pageNumber != null ? pageNumber.hashCode() : 0;
    }
}
