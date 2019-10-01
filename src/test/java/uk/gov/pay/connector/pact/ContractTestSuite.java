package uk.gov.pay.connector.pact;

import junit.framework.JUnit4TestAdapter;
import junit.framework.TestSuite;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static java.lang.String.format;

@RunWith(AllTests.class)
public class ContractTestSuite {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContractTestSuite.class);

    private static Map<String, JUnit4TestAdapter> map = Map.of(
            "frontend", new JUnit4TestAdapter(FrontendContractTest.class),
            "ledger", new JUnit4TestAdapter(QueueMessageContractTest.class),
            "publicapi", new JUnit4TestAdapter(PublicApiContractTest.class),
            "selfservice", new JUnit4TestAdapter(SelfServiceContractTest.class));

    public static TestSuite suite() {
        String consumer = System.getProperty("CONSUMER");
        TestSuite suite = new TestSuite();
        
        if (consumer == null || consumer.isBlank()) {
            LOGGER.info("Running all contract tests.");
            map.forEach((key, value) -> suite.addTest(value));
        } else if (map.containsKey(consumer)) {
            LOGGER.info("Running {}-connector contract tests only.", consumer);
            suite.addTest(map.get(consumer));
        } else {
            throw new RuntimeException(format("Error running provider contract tests. ${CONSUMER} system property was %s.", consumer));
        }
        
        return suite;
    }
}
