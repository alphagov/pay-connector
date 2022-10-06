package uk.gov.pay.connector.pact;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        FrontendContractTest.class,
        LedgerQueueMessageContractTest.class,
        AdminusersQueueMessageContractTest.class,
        PublicApiContractTest.class,
        SelfServiceContractTest.class
})
public class ContractTestSuite {
}
