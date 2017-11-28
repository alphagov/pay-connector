package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.GatewayAccountRequest;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Optional;
import java.util.Map;
import java.util.function.BiConsumer;

import static uk.gov.pay.connector.resources.GatewayAccountRequestValidator.FIELD_NOTIFY_SETTINGS;

public class GatewayAccountUpdater {

    private final GatewayAccountDao gatewayAccountDao;

    private final Map<String, BiConsumer<GatewayAccountRequest, GatewayAccountEntity>> attributeUpdater =
            new HashMap<String, BiConsumer<GatewayAccountRequest, GatewayAccountEntity>>() {{
                put(FIELD_NOTIFY_SETTINGS, updateNotifySettings());
            }};

    @Inject
    public GatewayAccountUpdater(GatewayAccountDao gatewayAccountDao) {
        this.gatewayAccountDao = gatewayAccountDao;
    }

    @Transactional
    public Optional<GatewayAccount> doPatch(Long gatewayAccountId, GatewayAccountRequest gatewayAccountRequest) {
        return gatewayAccountDao.findById(gatewayAccountId)
                .flatMap(gatewayAccountEntity -> {
                    attributeUpdater.get(gatewayAccountRequest.getPath())
                            .accept(gatewayAccountRequest, gatewayAccountEntity);
                    gatewayAccountDao.merge(gatewayAccountEntity);
                    return Optional.of(GatewayAccount.valueOf(gatewayAccountEntity));
                });
    }

    private BiConsumer<GatewayAccountRequest,GatewayAccountEntity> updateNotifySettings() {
        return (gatewayAccountRequest, gatewayAccountEntity) -> {
            gatewayAccountEntity.setNotifySettings(gatewayAccountRequest.valueAsObject());
        };
    }
}
