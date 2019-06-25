package uk.gov.pay.connector.paymentprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeExpiryService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.report.model.GatewayStatusComparison;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class DiscrepancyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscrepancyService.class);

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
                    } catch (GatewayException e) {
                        return GatewayStatusComparison.getEmpty(charge);
                    }
                });
    }

    private GatewayStatusComparison resolve(GatewayStatusComparison gatewayStatusComparison) {
        if (canBeCancelled(gatewayStatusComparison)) {
            Boolean cancelSuccess = expiryService.forceCancelWithGateway(gatewayStatusComparison.getCharge());
            if (cancelSuccess) {
                LOGGER.info("Successfully resolved discrepancy for charge {} ", gatewayStatusComparison.getChargeId());
                gatewayStatusComparison.setProcessed(true);
            } else {
                LOGGER.info("Failed to resolve discrepancy for charge {} ", gatewayStatusComparison.getChargeId());
            }
        } else {
            LOGGER.info("Could not resolve discrepancy for charge {} ", gatewayStatusComparison.getChargeId());
        }

        return gatewayStatusComparison;
    }

    private boolean canBeCancelled(GatewayStatusComparison gatewayStatusComparison) {
        ExternalChargeState payExternalChargeStatus = gatewayStatusComparison.getPayStatus().toExternal();
        return gatewayStatusComparison.hasExternalStatusMismatch() &&
                gatewayStatusComparison.getGatewayStatus()
                        .map(chargeStatus -> !chargeStatus.toExternal().isFinished())
                        .orElse(false) &&
                payExternalChargeStatus.isFinished() &&
                !payExternalChargeStatus.equals(ExternalChargeState.EXTERNAL_SUCCESS) &&
                chargeAgeInDaysIsGreaterThan(gatewayStatusComparison.getCharge(), 7);
    }

    private boolean chargeAgeInDaysIsGreaterThan(ChargeEntity charge, long minimumAge) {
        return charge.getCreatedDate().plusDays(minimumAge).isBefore(ZonedDateTime.now());
    }
}
