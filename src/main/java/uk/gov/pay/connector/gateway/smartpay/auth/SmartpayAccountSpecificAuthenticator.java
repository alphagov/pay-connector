package uk.gov.pay.connector.gateway.smartpay.auth;

import com.google.inject.Inject;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.HashUtil;

import java.util.Optional;

import static java.lang.String.format;

public class SmartpayAccountSpecificAuthenticator implements Authenticator<BasicCredentials, BasicAuthUser> {
    private GatewayAccountDao gatewayAccountDao;
    private HashUtil hashUtil;
    private static final Logger logger = LoggerFactory.getLogger(SmartpayAccountSpecificAuthenticator.class);

    @Inject
    public SmartpayAccountSpecificAuthenticator(GatewayAccountDao gatewayAccountDao, HashUtil hashUtil) {
        this.gatewayAccountDao = gatewayAccountDao;
        this.hashUtil = hashUtil;

    }

    @Override
    public Optional<BasicAuthUser> authenticate(BasicCredentials basicCredentials) {

        return gatewayAccountDao.findByNotificationCredentialsUsername(basicCredentials.getUsername())
                .filter((gatewayAccountEntity) -> matchCredentials(basicCredentials, gatewayAccountEntity))
                .map(gatewayAccountEntity -> Optional.ofNullable(gatewayAccountEntity.getNotificationCredentials().toBasicAuthUser()))
                .orElseGet(() -> {
                    logger.error(format("Authentication failure: failed for smartpay username %s", basicCredentials));
                    return Optional.empty();
                });
    }

    private boolean matchCredentials(BasicCredentials basicCredentials, GatewayAccountEntity gatewayAccountEntity) {
        return hashUtil.check(basicCredentials.getPassword(), gatewayAccountEntity.getNotificationCredentials().getPassword());
    }
}
