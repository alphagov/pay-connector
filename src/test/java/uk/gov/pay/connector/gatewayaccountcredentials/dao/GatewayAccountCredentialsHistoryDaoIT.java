package uk.gov.pay.connector.gatewayaccountcredentials.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.secureRandomLong;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class GatewayAccountCredentialsHistoryDaoIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private GatewayAccountCredentialsDao gatewayAccountCredentialsDao;
    private GatewayAccountCredentialsHistoryDao gatewayAccountCredentialsHistoryDao;
    private GatewayAccountDao gatewayAccountDao;

    @BeforeEach
    void setUp() {
        gatewayAccountCredentialsDao = app.getInstanceFromGuiceContainer(GatewayAccountCredentialsDao.class);
        gatewayAccountDao = app.getInstanceFromGuiceContainer(GatewayAccountDao.class);
        gatewayAccountCredentialsHistoryDao = app.getInstanceFromGuiceContainer(GatewayAccountCredentialsHistoryDao.class);
    }

    @Test
    void deleteGatewayAccountCredentialsHistory() {
        String serviceId = "archived-service-id";
        var gatewayAccountEntity = createAGatewayAccount(serviceId);
        // delete credentials history as DatabaseTestHelper creates some by default
        gatewayAccountCredentialsHistoryDao.delete(serviceId);
        persistTwoGatewayAccountCredentialsHistoryRows(gatewayAccountEntity, serviceId);

        var anotherGatewayAccountEntity = createAGatewayAccount(serviceId);
        persistTwoGatewayAccountCredentialsHistoryRows(anotherGatewayAccountEntity, serviceId);

        assertThat(gatewayAccountCredentialsHistoryDao.delete(serviceId), is(4));
        assertThat(app.getDatabaseTestHelper().getGatewayAccountCredentialsHistoryForAccount(gatewayAccountEntity.getId()).isEmpty(), is(true));
        assertThat(app.getDatabaseTestHelper().getGatewayAccountCredentialsHistoryForAccount(anotherGatewayAccountEntity.getId()).isEmpty(), is(true));
    }

    private GatewayAccountEntity createAGatewayAccount(String serviceId) {
        long gatewayAccountId = secureRandomLong();
        app.getDatabaseTestHelper()
                .addGatewayAccount(anAddGatewayAccountParams()
                        .withAccountId(String.valueOf(gatewayAccountId))
                        .withServiceId(serviceId)
                        .build());
        return gatewayAccountDao.findById(gatewayAccountId).get();
    }

    private void persistTwoGatewayAccountCredentialsHistoryRows(GatewayAccountEntity gatewayAccountEntity, String serviceId) {
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
