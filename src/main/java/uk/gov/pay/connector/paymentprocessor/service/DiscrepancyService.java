package uk.gov.pay.connector.paymentprocessor.service;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeExpiryService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.gateway.GatewayErrorException;
import uk.gov.pay.connector.report.model.GatewayStatusComparison;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class DiscrepancyService {
    private final ChargeService chargeService;
    private final QueryService queryService;
    private final ChargeExpiryService expiryService;

    @Inject
    public DiscrepancyService(ChargeService chargeService, QueryService queryService, ChargeExpiryService expiryService) {
        this.chargeService = chargeService;
        this.queryService = queryService;
        this.expiryService = expiryService;
    }
    
    public List<GatewayStatusComparison> listGatewayStatusComparisons(List<String> chargeIds) {
        return toGatewayStatusComparisonList(chargeIds)
                .collect(Collectors.toList());
    }

    public List<GatewayStatusComparison> resolveDiscrepancies(List<String> chargeIds) {
        return toGatewayStatusComparisonList(chargeIds)
                .filter(GatewayStatusComparison::hasExternalStatusMismatch)
                .map(this::resolve)
                .collect(Collectors.toList());
    }

    private Stream<GatewayStatusComparison> toGatewayStatusComparisonList(List<String> chargeIds) {
        return chargeIds.stream()
                .map(chargeService::findChargeById)
                .map(charge -> {
                    try {
                        return GatewayStatusComparison.from(charge, queryService.getChargeGatewayStatus(charge));
                    } catch(GatewayErrorException e) {
                        return GatewayStatusComparison.getEmpty(charge);
                    }
                });
    }
    
    private GatewayStatusComparison resolve(GatewayStatusComparison gatewayStatusComparison) {
        if (canBeCancelled(gatewayStatusComparison)) {
            expiryService.forceCancelWithGateway(gatewayStatusComparison.getCharge());
            gatewayStatusComparison.setProcessed(true);
        }
        
        return gatewayStatusComparison;
    }

    private boolean canBeCancelled(GatewayStatusComparison gatewayStatusComparison) {
        ExternalChargeState payExternalChargeStatus = gatewayStatusComparison.getPayStatus().toExternal();
        return gatewayStatusComparison.hasExternalStatusMismatch() &&
                !gatewayStatusComparison.getGatewayStatus().toExternal().isFinished() &&
                payExternalChargeStatus.isFinished() &&
                !payExternalChargeStatus.equals(ExternalChargeState.EXTERNAL_SUCCESS) &&
                chargeAgeInDaysIsGreaterThan(gatewayStatusComparison.getCharge(), 7);
    }

    private boolean chargeAgeInDaysIsGreaterThan(ChargeEntity charge, long minimumAge) {
        return charge.getCreatedDate().plusDays(minimumAge).isBefore(ZonedDateTime.now());
    }
}
