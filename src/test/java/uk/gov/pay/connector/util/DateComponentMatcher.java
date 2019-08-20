package uk.gov.pay.connector.util;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class DateComponentMatcher extends BaseMatcher<String> {
    
    private final int lowerBound;
    private final int upperBound;
    private final String description;

    private DateComponentMatcher(int lowerBound, int upperBound, String description) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.description = description;
    }

    public static DateComponentMatcher isValidSecond() {
        return new DateComponentMatcher(0, 59, "Expected a number between 00 and 59");
    }
    
    public static DateComponentMatcher isValidMinute() {
        return new DateComponentMatcher(0, 59, "Expected a number between 00 and 59");
    }
    
    public static DateComponentMatcher isValidHour() {
        return new DateComponentMatcher(0, 23, "Expected a number between 00 and 23");
    }

    public static DateComponentMatcher isValidDayOfMonth() {
        return new DateComponentMatcher(1, 31, "Expected a number between 01 and 31");
    }
    
    public static DateComponentMatcher isValidMonth() {
        return new DateComponentMatcher(1, 12, "Expected a number between 01 and 12");
    }

    public static DateComponentMatcher isValidYear() {
        return new DateComponentMatcher(2019, 9999, "Year is not valid");
    }
    
    @Override
    public boolean matches(Object item) {
        if (item instanceof String) {
            try {
                int value = Integer.parseInt((String) item);
                if (value < lowerBound || value > upperBound) {
                    return false;
                }
                return value >= 10 || item.equals("0" + value);
            } catch (NumberFormatException e) {
                return false;   
            }
        }
        return false;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(this.description);
    }
}
