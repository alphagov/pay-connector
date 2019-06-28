package uk.gov.pay.connector.pact;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)

@Suite.SuiteClasses({
        QueueMessageContractTest.class,
        TransactionsApiContractTest.class,
        WalletApiContractTest.class
})
public class ContractTestSuite {

}
