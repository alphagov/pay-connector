package uk.gov.pay.connector.chargeevent.model.domain;

import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.time.ZonedDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class LocalDateTimeConverterTest {

    private LocalDateTimeConverter localDateTimeConverter;
    
    @Before
    public void setUp(){
        localDateTimeConverter = new LocalDateTimeConverter();
    }

    @Test
    public void convertsToUTC() {
        ZonedDateTime dateTime = ZonedDateTime.parse("2019-10-10T10:55:56Z");

        Timestamp convertedTimestamp = localDateTimeConverter.convertToDatabaseColumn(dateTime);
        
        assertThat(convertedTimestamp.toString(), is("2019-10-10 10:55:56.0"));
    }

    @Test
    public void convertsFromUTC() {
        Timestamp timestamp = Timestamp.valueOf("2019-10-10 10:55:56");

        ZonedDateTime convertedDateTime = localDateTimeConverter.convertToEntityAttribute(timestamp);
        
        assertThat(convertedDateTime.toString(), is("2019-10-10T10:55:56Z[UTC]"));
    }
}
