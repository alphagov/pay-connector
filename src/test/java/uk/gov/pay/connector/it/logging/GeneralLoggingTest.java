package uk.gov.pay.connector.it.logging;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import ch.qos.logback.classic.Logger;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;

import static uk.gov.pay.connector.filters.LoggingFilter.HEADER_REQUEST_ID;

public class GeneralLoggingTest {

  DropwizardTestSupport<ConnectorConfiguration> app;

  @Test
  public void shouldLogRequestIdForAnyLoggingInvocation() {

    app = new DropwizardTestSupport<>(ConnectorApp.class,
        ResourceHelpers.resourceFilePath("config/config.yaml"));
    app.before();

    String requestId = UUID.randomUUID().toString();

    Logger logger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    MDC.put(HEADER_REQUEST_ID, requestId);

    // reassigning standard out so that we can grab the log message and test against it
    ByteArrayOutputStream pipeOut = new ByteArrayOutputStream();
    PrintStream old_out = System.out;
    System.setOut(new PrintStream(pipeOut));

    logger.info("This is a test logging invocation");
    logger.detachAndStopAllAppenders(); // this should force an appender flush

    // restoring standard out to the previous state
    System.setOut(old_out);

    String output = new String(pipeOut.toByteArray());

    assertThat(output, containsString(requestId));
  }

}
