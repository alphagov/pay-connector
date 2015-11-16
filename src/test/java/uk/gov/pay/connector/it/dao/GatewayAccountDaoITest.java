package uk.gov.pay.connector.it.dao;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import java.util.Map;
import java.util.Optional;

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
        String gatewayAccountId = gatewayAccountDao.insertProviderAndReturnNewId(paymentProvider);
        assertTrue(StringUtils.isNotBlank(gatewayAccountId));
    }

    @Test
    public void idIsMissingForMissingAccount() throws Exception {
        assertThat(gatewayAccountDao.idIsMissing("1"), is(true));
    }

    @Test
    public void shouldFindGatewayAccountDetailsById() throws Exception {
        String paymentProvider = "test provider";
        String id = gatewayAccountDao.insertProviderAndReturnNewId(paymentProvider);

        Optional<Map<String, Object>> retrievedAccount = gatewayAccountDao.findById(id);

        assertTrue(retrievedAccount.isPresent());
        assertThat(retrievedAccount.get(), hasEntry("payment_provider", paymentProvider));
        assertThat(retrievedAccount.get(), hasEntry("credentials", "{}"));
    }

    @Test
    public void findByIdNoFound() throws Exception {
        assertFalse(gatewayAccountDao.findById("123").isPresent());
    }

    @Test
    public void verifyIfIdExists() throws Exception {
        String paymentProvider = "test provider";
        String id = gatewayAccountDao.insertProviderAndReturnNewId(paymentProvider);
        assertThat(gatewayAccountDao.idIsMissing(id), is(false));
    }

    @Test
    public void shouldUpdateEmptyCredentials() {
        String paymentProvider = "test provider";
        String gatewayAccountId = gatewayAccountDao.insertProviderAndReturnNewId(paymentProvider);

        String expectedJsonString = "{\"username\": \"Username\", \"password\": \"Password\"}";
        gatewayAccountDao.saveCredentials(expectedJsonString, gatewayAccountId);

        Optional<Map<String, Object>> retrievedGatewayAccount = gatewayAccountDao.findById(gatewayAccountId);
        String credentialsJsonString = (String) retrievedGatewayAccount.get().get("credentials");
        JsonObject retrievedJsonObject = new Gson().fromJson(credentialsJsonString, JsonObject.class);
        assertThat(retrievedJsonObject, is(new Gson().fromJson(expectedJsonString, JsonObject.class)));
    }

    @Test
    public void shouldThrowExceptionWhenUpdatingCredentialsIfNotValidJson() {
        String paymentProvider = "test provider";
        String gatewayAccountId = gatewayAccountDao.insertProviderAndReturnNewId(paymentProvider);

        String expectedJsonString = "[blah]";

        try {
            gatewayAccountDao.saveCredentials(expectedJsonString, gatewayAccountId);
            fail();
        } catch (RuntimeException e) {
            Optional<Map<String, Object>> retrievedGatewayAccount = gatewayAccountDao.findById(gatewayAccountId);
            String credentialsJsonString = (String) retrievedGatewayAccount.get().get("credentials");
            assertThat(credentialsJsonString, is("{}"));
        }
    }
}
