package uk.gov.pay.connector.it.dao;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    public void findById() throws Exception {
        String paymentProvider = "test provider";
        String id = gatewayAccountDao.insertProviderAndReturnNewId(paymentProvider);
        assertTrue(gatewayAccountDao.findById(id).isPresent());
        assertThat(gatewayAccountDao.findById(id).get(), hasEntry("payment_provider", paymentProvider));
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
}
