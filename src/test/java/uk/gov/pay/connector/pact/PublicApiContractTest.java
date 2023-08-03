package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.junit.IgnoreNoPactsToVerify;
import au.com.dius.pact.provider.junit.PactRunner;
import au.com.dius.pact.provider.junit.Provider;
import au.com.dius.pact.provider.junit.loader.PactBroker;
import au.com.dius.pact.provider.junit.loader.PactBrokerAuth;
import org.junit.runner.RunWith;

@RunWith(PactRunner.class)
@Provider("connector")
@PactBroker(scheme = "https", host = "${PACT_BROKER_HOST:pact-broker.deploy.payments.service.gov.uk}", tags = {"${PACT_CONSUMER_TAG}", "test-fargate"},
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USERNAME}", password = "${PACT_BROKER_PASSWORD}"),
        consumers = {"publicapi"})
@IgnoreNoPactsToVerify
public class PublicApiContractTest extends ContractTest {
}
