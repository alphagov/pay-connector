package uk.gov.pay.connector.charge.model;

import uk.gov.pay.connector.exception.ValidationException;

public abstract class PositiveLong {

    private Long positiveLong;

    PositiveLong(Long positiveLong) {
        if (positiveLong <= 0) {
            throw new ValidationException("DisplaySize must be a positive integer");
        }
        this.positiveLong = positiveLong;
    }

    PositiveLong(Long positiveLong, Long defaultValue) {
        if (positiveLong == null) {
            this.positiveLong = defaultValue;
        } else {
            this.positiveLong = positiveLong;
        }
    }

    public Long getRawValue() {
        return positiveLong;
    }

    
    @Override
    public String toString() {
        return String.valueOf(positiveLong);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PositiveLong that = (PositiveLong) o;

        return positiveLong != null ? positiveLong.equals(that.positiveLong) : that.positiveLong == null;
    }

    @Override
    public int hashCode() {
        return positiveLong != null ? positiveLong.hashCode() : 0;
    }
}
