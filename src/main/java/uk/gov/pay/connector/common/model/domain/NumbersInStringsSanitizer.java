package uk.gov.pay.connector.common.model.domain;

import com.google.common.base.CharMatcher;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class NumbersInStringsSanitizer {

    private static final int MAXIMUM_NUMBER_OF_NUMBERS_IN_STRING_ALLOWED = 10;
    private static final CharMatcher CHAR_MATCHER_NUMBERS_RANGE = CharMatcher.inRange('0', '9');
    private static final String CHARACTER_PER_NUMBER_REPLACEMENT = "*";

    public static String sanitize(String value) {
        String result = value;
        if (isNotBlank(value)) {
            String numberOfDigits = CHAR_MATCHER_NUMBERS_RANGE.retainFrom(value);
            if (numberOfDigits.length() > MAXIMUM_NUMBER_OF_NUMBERS_IN_STRING_ALLOWED) {
                result = CHAR_MATCHER_NUMBERS_RANGE.replaceFrom(value, CHARACTER_PER_NUMBER_REPLACEMENT);
            }
        }
        return result;
    }
}
