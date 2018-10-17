package uk.gov.pay.connector.charge.model;

import uk.gov.pay.connector.exception.ValidationException;

public class DisplaySize {
    
    private Long displaySize;

    public DisplaySize(Long displaySize) {
        this.displaySize = displaySize;
    }

    private static boolean isValid(Long displaySize) {
        return displaySize > 0;
    }

    public static DisplaySize of(Long displaySize) {
        if (!(isValid(displaySize))) {
            throw new ValidationException("DisplaySize is invalid");
        }
        return new DisplaySize(displaySize);
    }

    public Long getDisplaySize() {
        return displaySize;
    }

    @Override
    public String toString() {
        return String.valueOf(displaySize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DisplaySize that = (DisplaySize) o;

        return displaySize != null ? displaySize.equals(that.displaySize) : that.displaySize == null;
    }

    @Override
    public int hashCode() {
        return displaySize != null ? displaySize.hashCode() : 0;
    }
}
