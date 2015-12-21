package uk.gov.pay.connector.it.dao;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import java.util.Map;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

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

        Optional<GatewayAccount> gatewayAccountOpt = gatewayAccountDao.findById(id);

        assertTrue(gatewayAccountOpt.isPresent());
        GatewayAccount gatewayAccount = gatewayAccountOpt.get();
        assertThat(gatewayAccount.getGatewayName(), is(paymentProvider));
        Map<String, String> credentialsMap = gatewayAccount.getCredentials();
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

        Optional<GatewayAccount> serviceAccountMaybe = gatewayAccountDao.findById(gatewayAccountId);
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", "Username"));
        assertThat(credentialsMap, hasEntry("password", "Password"));
    }

    @Test
    public void shouldUpdateAndRetrieveCredentialsWithSpecialCharacters() throws Exception {
        String paymentProvider = "test provider";
        String gatewayAccountId = gatewayAccountDao.createGatewayAccount(paymentProvider);

        String aUserNameWithSpecialChars = "someone@some{[]where&^%>?\\/";
        String aPasswordWithSpecialChars = "56g%%Bqv\\>/<wdUpi@#bh{[}]6JV+8w";
        ImmutableMap<String, String> credMap = ImmutableMap.of("username", aUserNameWithSpecialChars, "password", aPasswordWithSpecialChars);
        String expectedJsonString = new Gson().toJson(credMap);

        gatewayAccountDao.saveCredentials(expectedJsonString, gatewayAccountId);

        Optional<GatewayAccount> serviceAccountMaybe = gatewayAccountDao.findById(gatewayAccountId);
        assertThat(serviceAccountMaybe.isPresent(), is(true));
        Map<String, String> credentialsMap = serviceAccountMaybe.get().getCredentials();
        assertThat(credentialsMap, hasEntry("username", aUserNameWithSpecialChars));
        assertThat(credentialsMap, hasEntry("password", aPasswordWithSpecialChars));
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
            Optional<GatewayAccount> gatewayAccountMaybe = gatewayAccountDao.findById(gatewayAccountId);
            assertThat(gatewayAccountMaybe.isPresent(), is(true));
            Map<String, String> credentialsMap = gatewayAccountMaybe.get().getCredentials();
            assertThat(credentialsMap.size(), is(0));
        }
    }

}
