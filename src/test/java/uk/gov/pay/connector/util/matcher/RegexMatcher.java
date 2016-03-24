package uk.gov.pay.connector.util.matcher;

import org.hamcrest.Description;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexMatcher extends org.hamcrest.TypeSafeMatcher<String> {

    private String pattern;

    private RegexMatcher(String pattern) {
        this.pattern = pattern;
    }

    public static RegexMatcher matchesRegex(final String pattern) {
        return new RegexMatcher(pattern);
    }

    @Override
    protected boolean matchesSafely(String input) {
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(input);
        return matcher.matches();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("A String matching pattern: " + pattern);
    }
}
