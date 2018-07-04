package uk.gov.pay.connector.events;

import com.google.common.eventbus.EventBus;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class EventCommandHandler {

    private static final List<String> CHARGE_SUCCESS_STATUSES = Arrays.stream(ChargeStatus.values())
            .filter(status -> status.toExternal() == ExternalChargeState.EXTERNAL_SUCCESS)
            .map(chargeStatus -> chargeStatus.getValue())
            .collect(toList());
    
    private final EventBus eventBus;

    @Inject
    public EventCommandHandler(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void handleSuccessfulChargeEvent(ChargeEntity chargeEntity) {
        if (CHARGE_SUCCESS_STATUSES.contains(chargeEntity.getStatus())) {
            eventBus.post(new SuccessfulChargeEvent(chargeEntity.getGatewayAccount().getId(), chargeEntity.getCreatedDate(), chargeEntity.getAmount(), chargeEntity.getExternalId()));
        }
    }
}
