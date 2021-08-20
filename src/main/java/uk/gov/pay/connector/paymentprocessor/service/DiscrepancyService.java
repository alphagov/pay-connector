package uk.gov.pay.connector.paymentprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeExpiryService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.report.model.GatewayStatusComparison;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class DiscrepancyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscrepancyService.class);

    private final ChargeService chargeService;
    private final QueryService queryService;
    private final ChargeExpiryService expiryService;
    private final GatewayAccountService gatewayAccountService;

    @Inject
    public DiscrepancyService(ChargeService chargeService, QueryService queryService,
                              ChargeExpiryService expiryService, GatewayAccountService gatewayAccountService) {
        this.chargeService = chargeService;
        this.queryService = queryService;
        this.expiryService = expiryService;
        this.gatewayAccountService = gatewayAccountService;
    }

    public List<GatewayStatusComparison> listGatewayStatusComparisons(List<String> chargeIds) {
        return toGatewayStatusComparisonList(chargeIds)
                .collect(Collectors.toList());
    }

    public List<GatewayStatusComparison> resolveDiscrepancies(List<String> chargeIds) {
        return toGatewayStatusComparisonList(chargeIds)
                .filter(gatewayStatusComparison -> !gatewayStatusComparison.getCharge().isHistoric()) // exclude resolving expunged charges
                .filter(GatewayStatusComparison::hasExternalStatusMismatch)
                .map(this::resolve)
                .collect(Collectors.toList());
    }

    private Stream<GatewayStatusComparison> toGatewayStatusComparisonList(List<String> chargeIds) {
        return chargeIds.stream()
                .map(chargeExternalId -> chargeService.findCharge(chargeExternalId)
                        .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId)))
                .map(this::getGatewayStatusComparison);
    }

    private GatewayStatusComparison resolve(GatewayStatusComparison gatewayStatusComparison) {
        if (canBeCancelled(gatewayStatusComparison)) {
            ChargeEntity chargeEntity = chargeService.findChargeByExternalId(gatewayStatusComparison.getCharge().getExternalId());
            boolean cancelSuccess = expiryService.forceCancelWithGateway(chargeEntity);
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
                chargeAgeInDaysIsGreaterThan(gatewayStatusComparison.getCharge(), 2);
    }

    private boolean chargeAgeInDaysIsGreaterThan(Charge charge, long minimumAge) {
        return charge.getCreatedDate().plus(Duration.ofDays(minimumAge)).isBefore(Instant.now());
    }

    private GatewayStatusComparison getGatewayStatusComparison(Charge charge) {
        return gatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())
                .map(gatewayAccountEntity -> {
                    try {
                        return GatewayStatusComparison.from(charge, queryService.getChargeGatewayStatus(charge, gatewayAccountEntity));
                    } catch (GatewayException e) {
                        return GatewayStatusComparison.getEmpty(charge);
                    }
                }).orElse(GatewayStatusComparison.getEmpty(charge));
    }
}
