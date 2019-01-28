package uk.gov.pay.connector.report.service;

import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.paymentprocessor.service.QueryService;
import uk.gov.pay.connector.report.model.GatewayStatusComparison;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;


public class DiscrepancyService {
    private final ChargeService chargeService;
    private final QueryService queryService;

    @Inject
    public DiscrepancyService(ChargeService chargeService, QueryService queryService) {
        this.chargeService = chargeService;
        this.queryService = queryService;
    }
    
    public List<GatewayStatusComparison> listGatewayStatusComparisons(List<String> chargeIds) {
        return chargeIds.stream()
                .map(chargeService::findChargeById)
                .map(charge -> GatewayStatusComparison.from(charge, queryService.getChargeGatewayStatus(charge)))
                .collect(Collectors.toList());
    }
}
