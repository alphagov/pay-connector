package uk.gov.pay.connector.service.smartpay;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.*;
import static org.joda.time.DateTimeZone.forOffsetHours;
import static org.junit.Assert.assertThat;

public class SmartpayNotificationTest {

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Test
    public void simpleFieldsShould_simplyMap() {
        SmartpayNotification smartpayNotification = aNotificationWith(
                "eventCode", "eventCode",
                "success", "true",
                "eventDate", "2015-10-08T13:48:30+02:00");
        assertThat(smartpayNotification.getEventCode(), is("eventCode"));
        assertThat(smartpayNotification.isSuccessFull(), is(true));
        assertThat(smartpayNotification.getEventDate(), is(new DateTime(2015, 10, 8, 13, 48, 30, forOffsetHours(2))));
    }

    @Test
    public void successValuesThatArentTrueShouldBeFalse() {
        SmartpayNotification smartpayNotification = aNotificationWith("success", "sausages");
        assertThat(smartpayNotification.isSuccessFull(), is(false));
    }

    @Test
    public void ifAnOriginalReferenceIsPresent_thatShouldBeTheTransactionId() {
        SmartpayNotification smartpayNotification = aNotificationWith(
                "originalReference", "original",
                "pspReference", "not a useful transaction id");
        assertThat(smartpayNotification.getTransactionId(), is("original"));
    }

    @Test
    public void ifAnOriginalReferenceIsNotPresent_thePspReferenceShouldBeTheTransactionId() {
        SmartpayNotification smartpayNotification = aNotificationWith(
                "pspReference", "now's my chance to shine");
        assertThat(smartpayNotification.getTransactionId(), is("now's my chance to shine"));
    }

    @Test
    public void aSmartPayNotification_needsSomeFields() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(allOf(containsString("pspReference"), containsString("eventDate"), containsString("eventCode")));
        new SmartpayNotification(emptyMap());
    }

    @Test
    public void smartpayNotifications_ShouldCompareByDate() {
        SmartpayNotification earlyNotification = aNotificationWith("eventDate", "2015-10-08T13:48:30+02:00");
        SmartpayNotification laterNotification = aNotificationWith("eventDate", "2015-10-08T13:48:31+02:00");

        assertThat(earlyNotification, is(lessThanOrEqualTo(earlyNotification)));
        assertThat(earlyNotification, is(greaterThanOrEqualTo(earlyNotification)));

        assertThat(earlyNotification, is(lessThan(laterNotification)));
        assertThat(laterNotification, is(greaterThan(earlyNotification)));
    }

    private final static Map<String, Object> defaults = ImmutableMap.of(
            "pspReference", "1234-5678-9012",
            "eventCode", "eventCode",
            "eventDate", "2015-10-08T13:48:30+02:00");

    private static SmartpayNotification aNotificationWith(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Can't evenly split " + Joiner.on(",").join(keyValues));
        }

        Map<String, Object> builder = Maps.newHashMap(defaults);
        for (int i=0; i < keyValues.length; i+=2) {
            builder.put(keyValues[i], keyValues[i + 1]);
        }
        return new SmartpayNotification(builder);
    }

}