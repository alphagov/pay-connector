package uk.gov.pay.connector.filters;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RestClientLoggingFilterTest {

    private RestClientLoggingFilter loggingFilter;

    @Mock
    private ClientRequestContext clientRequestContext;

    @Mock
    private ClientResponseContext clientResponseContext;

    private Appender<ILoggingEvent> mockAppender;

    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @Before
    public void setup() {
        loggingFilter = new RestClientLoggingFilter();
        Logger root = (Logger) LoggerFactory.getLogger(RestClientLoggingFilter.class);
        mockAppender = mock(Appender.class);
        root.addAppender(mockAppender);
    }

    @Test
    public void shouldLogEntryAndExitPointsOfEndPoints() throws Exception {

        URI requestUrl = URI.create("/connector-request");
        String requestMethod = "GET";

        when(clientRequestContext.getUri()).thenReturn(requestUrl);
        when(clientRequestContext.getMethod()).thenReturn(requestMethod);
        MultivaluedMap<String,Object> mockHeaders = new MultivaluedHashMap<>();
        MultivaluedMap<String,String> mockHeaders2 = new MultivaluedHashMap<>();

        when(clientRequestContext.getHeaders()).thenReturn(mockHeaders);
        when(clientResponseContext.getHeaders()).thenReturn(mockHeaders2);
        loggingFilter.filter(clientRequestContext);

        loggingFilter.filter(clientRequestContext,clientResponseContext);

        verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();

        assertThat(loggingEvents.get(0).getFormattedMessage(), is(format("%s to %s began", requestMethod, requestUrl)));
        String endLogMessage = loggingEvents.get(1).getFormattedMessage();
        assertThat(endLogMessage, containsString(format("%s to %s ended - total time ", requestMethod, requestUrl)));
        String[] timeTaken = StringUtils.substringsBetween(endLogMessage, "total time ", "ms");
        assertTrue(NumberUtils.isNumber(timeTaken[0]));

    }
}
