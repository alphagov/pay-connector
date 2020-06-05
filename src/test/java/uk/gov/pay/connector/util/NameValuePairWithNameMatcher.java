package uk.gov.pay.connector.util;

import org.apache.http.NameValuePair;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.List;

public class NameValuePairWithNameMatcher extends TypeSafeMatcher<List<NameValuePair>> {
    
    private String nameToMatch;

    public NameValuePairWithNameMatcher(String nameToMatch) {
        this.nameToMatch = nameToMatch;
    }

    @Override
    protected boolean matchesSafely(List<NameValuePair> nameValuePairs) {
        return nameValuePairs.stream().map(NameValuePair::getName).anyMatch(nameToMatch::equals);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("list to contain " + nameToMatch);
    }
    
    public static Matcher<List<NameValuePair>> containsNameValuePairWithName(String key) {
        return new NameValuePairWithNameMatcher(key);
    }
}
