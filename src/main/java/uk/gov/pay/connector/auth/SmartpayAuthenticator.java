package uk.gov.pay.connector.auth;

import com.google.common.base.Optional;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.util.HashUtil;

public class SmartpayAuthenticator implements Authenticator<BasicCredentials, BasicAuthUser> {
    private GatewayAccountDao gatewayAccountDao;
    private HashUtil hashUtil;
    public SmartpayAuthenticator(GatewayAccountDao gatewayAccountDao, HashUtil hashUtil) {
        this.gatewayAccountDao = gatewayAccountDao;
        this.hashUtil = hashUtil;

    }

    @Override
    public Optional<BasicAuthUser> authenticate(BasicCredentials basicCredentials) throws AuthenticationException {

        java.util.Optional<GatewayAccountEntity> gatewayAccountEntityMaybe = gatewayAccountDao
                .findByNotificationCredentialsUsername(basicCredentials.getUsername());

        if (gatewayAccountEntityMaybe.isPresent()) {
            GatewayAccountEntity gatewayAccountEntity = gatewayAccountEntityMaybe.get();
            if (hashUtil.check(basicCredentials.getPassword(),
                    gatewayAccountEntity.getNotificationCredentials().getPassword())) {
                return Optional.of(gatewayAccountEntity.getNotificationCredentials().toBasicAuthUser());
            }
        }

        return Optional.absent();
    }
}
