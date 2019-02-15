package uk.gov.pay.connector.gateway.smartpay;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;

public class SmartpayNotificationTest {

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Test
    public void simpleFieldsShould_simplyMap() {
        SmartpayNotification smartpayNotification = aNotificationWith(
                "originalReference", "originalReference",
                "pspReference", "pspReference",
                "eventCode", "eventCode",
                "success", "true",
                "eventDate", "2015-10-08T13:48:30+02:00");
        assertThat(smartpayNotification.getTransactionId(), is("pspReference"));
        assertThat(smartpayNotification.getOriginalReference(), is("originalReference"));
        assertThat(smartpayNotification.getPspReference(), is("pspReference"));
        assertThat(smartpayNotification.getEventCode(), is("eventCode"));
        assertThat(smartpayNotification.isSuccessFul(), is(true));

        assertThat(smartpayNotification.getEventDate(), is(ZonedDateTime.of(2015, 10, 8, 13, 48, 30, 0, ZoneOffset.ofHours(2))));
    }

    @Test
    public void successValuesThatArentTrueShouldBeFalse() {
        SmartpayNotification smartpayNotification = aNotificationWith("success", "sausages");
        assertThat(smartpayNotification.isSuccessFul(), is(false));
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
