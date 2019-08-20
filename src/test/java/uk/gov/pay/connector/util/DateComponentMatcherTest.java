package uk.gov.pay.connector.util;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Java6Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class DateComponentMatcherTest {
    
    @Test
    @Parameters({
            "00, true",
            "0, false",
            "-1, false",
            "60, false",
            "9, false",
            "09, true",
            "11, true",
            "59, true",
    })
    public void testMinutesAndSeconds(String number, boolean expected) {
        assertThat(DateComponentMatcher.isValidMinute().matches(number)).isEqualTo(expected);
        assertThat(DateComponentMatcher.isValidSecond().matches(number)).isEqualTo(expected);
    }
    
    @Test
    @Parameters({
            "00, true",
            "0, false",
            "-1, false",
            "1, false",
            "11, true",
            "23, true",
            "24, false",
    })
    public void testHour(String number, boolean expected) {
        assertThat(DateComponentMatcher.isValidHour().matches(number)).isEqualTo(expected);
    }

    @Test
    @Parameters({
            "00, false",
            "1, false",
            "-1, false",
            "01, true",
            "11, true",
            "31, true",
            "32, false",
    })
    public void testDayOfMonth(String number, boolean expected) {
        assertThat(DateComponentMatcher.isValidDayOfMonth().matches(number)).isEqualTo(expected);
    }
}
