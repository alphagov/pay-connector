package uk.gov.pay.connector.gatewayaccountcredentials.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
import uk.gov.pay.connector.it.dao.DaoITestBase;

import java.util.Map;

import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class GatewayAccountCredentialsHistoryDaoIT extends DaoITestBase {

    private GatewayAccountCredentialsDao gatewayAccountCredentialsDao;
    private GatewayAccountCredentialsHistoryDao gatewayAccountCredentialsHistoryDao;
    private GatewayAccountDao gatewayAccountDao;

    @Before
    public void setUp() {
        gatewayAccountCredentialsDao = env.getInstance(GatewayAccountCredentialsDao.class);
        gatewayAccountDao = env.getInstance(GatewayAccountDao.class);
        gatewayAccountCredentialsHistoryDao = env.getInstance(GatewayAccountCredentialsHistoryDao.class);
    }
    
    @Test
    public void deleteGatewayAccountCredentialsHistory() {
        String serviceId = "archived-service-id";
        var gatewayAccountEntity = createAGatewayAccount(serviceId);
        persistTwoGatewayAccountCredentialsHistoryRows(gatewayAccountEntity);
        
        var anotherGatewayAccountEntity = createAGatewayAccount(serviceId);
        persistTwoGatewayAccountCredentialsHistoryRows(anotherGatewayAccountEntity);

        assertThat(gatewayAccountCredentialsHistoryDao.delete(serviceId), is(4));
        assertTrue(databaseTestHelper.getGatewayAccountCredentialsHistoryForAccount(gatewayAccountEntity.getId()).isEmpty());
        assertTrue(databaseTestHelper.getGatewayAccountCredentialsHistoryForAccount(anotherGatewayAccountEntity.getId()).isEmpty());
    }
    
    private GatewayAccountEntity createAGatewayAccount(String serviceId) {
        long gatewayAccountId = nextLong();
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withServiceId(serviceId)
                .build());
        return gatewayAccountDao.findById(gatewayAccountId).get();
    }

    private void persistTwoGatewayAccountCredentialsHistoryRows(GatewayAccountEntity gatewayAccountEntity) {
        Map<String, Object> credentials = Map.of(ONE_OFF_CUSTOMER_INITIATED, 
                Map.of(CREDENTIALS_MERCHANT_CODE, "a-merchant-code-1", CREDENTIALS_USERNAME, "a-merchant-code-1", CREDENTIALS_PASSWORD, "passw0rd1"));
        String externalCredentialId = randomUuid();
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(credentials)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .withExternalId(externalCredentialId)
                .build();
        gatewayAccountCredentialsDao.persist(gatewayAccountCredentialsEntity);

        gatewayAccountCredentialsEntity.setState(RETIRED);
        WorldpayCredentials updatedCredentials = new WorldpayCredentials();
        updatedCredentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials("a-merchant-code-1", "<DELETED>", "<DELETED>"));
        gatewayAccountCredentialsEntity.setCredentials(updatedCredentials);
        gatewayAccountCredentialsDao.merge(gatewayAccountCredentialsEntity);
    }
}
