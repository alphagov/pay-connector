package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
        String name = "test account";
        gatewayAccountDao.insertNameAndReturnNewId(name);
    }

    @Test
    public void findByIdForMissingAccount() throws Exception {
        Optional<String> result = gatewayAccountDao.findNameById(1L);
        assertThat(result, is(Optional.empty()));
    }

    @Test
    public void findByIdForAccount() throws Exception {
        String name = "test account";
        Long id = gatewayAccountDao.insertNameAndReturnNewId(name);

        Optional<String> result = gatewayAccountDao.findNameById(id);
        assertThat(result, is(Optional.of(name)));
    }
}
