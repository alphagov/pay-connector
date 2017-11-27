package uk.gov.pay.connector.service.notify;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.NotifySettingsUpdateRequest;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import javax.inject.Inject;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.NotifySettingsUpdateRequest.OP_REMOVE;
import static uk.gov.pay.connector.model.NotifySettingsUpdateRequest.OP_REPLACE;

public class NotifySettingsUpdateService {

    private final GatewayAccountDao gatewayAccountDao;

    @Inject
    public NotifySettingsUpdateService(GatewayAccountDao gatewayAccountDao) {
        this.gatewayAccountDao = gatewayAccountDao;
    }

    @Transactional
    public Optional<GatewayAccountEntity> update(Long gatewayAccountId, NotifySettingsUpdateRequest updateRequest) {
        return gatewayAccountDao.findById(gatewayAccountId)
                .map(gatewayAccountEntity -> {
                    if(updateRequest.getOp().equals(OP_REPLACE)) {
                        gatewayAccountEntity.setNotifySettings(updateRequest.getValue());
                    } else if (updateRequest.getOp().equals(OP_REMOVE)) {
                        gatewayAccountEntity.setNotifySettings(null);
                    } else {
                        throw new RuntimeException(format("Invalid Notify Settings Update operation [%s]", updateRequest.getOp()));
                    }
                    gatewayAccountDao.merge(gatewayAccountEntity);

                    return Optional.of(gatewayAccountEntity);
                })
        .orElseGet(() -> Optional.empty());
    }

}
