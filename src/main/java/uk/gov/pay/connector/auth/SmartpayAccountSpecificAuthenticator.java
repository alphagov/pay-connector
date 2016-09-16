package uk.gov.pay.connector.auth;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.util.HashUtil;

public class SmartpayAccountSpecificAuthenticator implements Authenticator<BasicCredentials, BasicAuthUser> {
    private GatewayAccountDao gatewayAccountDao;
    private HashUtil hashUtil;

    @Inject
    public SmartpayAccountSpecificAuthenticator(GatewayAccountDao gatewayAccountDao, HashUtil hashUtil) {
        this.gatewayAccountDao = gatewayAccountDao;
        this.hashUtil = hashUtil;

    }

    @Override
    public Optional<BasicAuthUser> authenticate(BasicCredentials basicCredentials) throws AuthenticationException {

        return gatewayAccountDao.findByNotificationCredentialsUsername(basicCredentials.getUsername())
                .map(gatewayAccountEntity -> {
                    BasicAuthUser basicAuthUser = null;
                    if (hashUtil.check(basicCredentials.getPassword(),
                            gatewayAccountEntity.getNotificationCredentials().getPassword())) {
                        basicAuthUser = gatewayAccountEntity.getNotificationCredentials().toBasicAuthUser();
                    }
                    return basicAuthUser;
                })
                .map(Optional::fromNullable)
                .orElseGet(Optional::absent);
    }
}
