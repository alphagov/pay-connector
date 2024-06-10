package uk.gov.pay.connector.pact;

import com.google.common.collect.ImmutableSetMultimap;
import junit.framework.JUnit4TestAdapter;
import junit.framework.TestSuite;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;
import uk.gov.service.payments.commons.testing.pact.provider.CreateTestSuite;

@RunWith(AllTests.class)
public class ContractTestSuite {
    
    public static TestSuite suite() {
        ImmutableSetMultimap<String, JUnit4TestAdapter> consumerToJUnitTest = ImmutableSetMultimap.of(
//                "frontend", new JUnit4TestAdapter(FrontendContractTest.class),
                "ledger", new JUnit4TestAdapter(LedgerQueueMessageContractTest.class),
                "adminusers", new JUnit4TestAdapter(AdminusersQueueMessageContractTest.class),
                "publicapi", new JUnit4TestAdapter(PublicApiContractTest.class),
                "selfservice", new JUnit4TestAdapter(SelfServiceContractTest.class));
        return CreateTestSuite.create(consumerToJUnitTest);
    }
}
