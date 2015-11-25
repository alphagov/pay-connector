package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import java.util.Map;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GatewayAccountDaoITest {
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private GatewayAccountDao gatewayAccountDao;

    @Before
    public void setUp() throws Exception {
        gatewayAccountDao = new GatewayAccountDao(app.getJdbi());
    }

    @Test
    public void insertANewChargeAndReturnTheId() throws Exception {
        String paymentProvider = "test provider";
        String gatewayAccountId = gatewayAccountDao.createGatewayAccount(paymentProvider);
        assertTrue(isNotBlank(gatewayAccountId));
    }

    @Test
    public void idIsMissingForMissingAccount() throws Exception {
        assertThat(gatewayAccountDao.idIsMissing("1"), is(true));
    }

    @Test
    public void shouldFindAccountInfoById() throws Exception {
        String paymentProvider = "test provider";
        String id = gatewayAccountDao.createGatewayAccount(paymentProvider);

        // We dont set any credentials, so the json document in the DB is: {}

        Optional<Map<String, Object>> gatewayAccountOpt = gatewayAccountDao.findById(id);

        assertTrue(gatewayAccountOpt.isPresent());
        Map<String, Object> gatewayAccountMap = gatewayAccountOpt.get();
        assertThat(gatewayAccountMap, hasEntry("payment_provider", paymentProvider));
        Map<String,String> credentialsMap = (Map<String, String>) gatewayAccountMap.get("credentials");
        assertThat(credentialsMap.size(), is(0));
    }

    @Test
    public void findByIdNoFound() throws Exception {
        assertFalse(gatewayAccountDao.findById("123").isPresent());
    }

    @Test
    public void verifyIfIdExists() throws Exception {
        String paymentProvider = "test provider";
        String id = gatewayAccountDao.createGatewayAccount(paymentProvider);
        assertThat(gatewayAccountDao.idIsMissing(id), is(false));
    }

    @Test
    public void shouldUpdateEmptyCredentials() {
        String paymentProvider = "test provider";
        String gatewayAccountId = gatewayAccountDao.createGatewayAccount(paymentProvider);

        String expectedJsonString = "{\"username\": \"Username\", \"password\": \"Password\"}";
        gatewayAccountDao.saveCredentials(expectedJsonString, gatewayAccountId);

        Optional<Map<String, Object>> gatewayAccountMaybe = gatewayAccountDao.findById(gatewayAccountId);
        assertThat(gatewayAccountMaybe.isPresent(), is(true));
        Map<String,String> credentialsMap = (Map<String, String>) gatewayAccountMaybe.get().get("credentials");
        assertThat(credentialsMap, hasEntry("username", "Username"));
        assertThat(credentialsMap, hasEntry("password", "Password"));
    }

    @Test
    public void shouldThrowExceptionWhenUpdatingCredentialsIfNotValidJson() {
        String paymentProvider = "test provider";
        String gatewayAccountId = gatewayAccountDao.createGatewayAccount(paymentProvider);

        String expectedJsonString = "[blah]";

        try {
            gatewayAccountDao.saveCredentials(expectedJsonString, gatewayAccountId);
            fail();
        } catch (RuntimeException e) {
            Optional<Map<String, Object>> gatewayAccountMaybe = gatewayAccountDao.findById(gatewayAccountId);
            assertThat(gatewayAccountMaybe.isPresent(), is(true));
            Map<String,String> credentialsMap = (Map<String, String>) gatewayAccountMaybe.get().get("credentials");
            assertThat(credentialsMap.size(), is(0));
        }
    }

}
