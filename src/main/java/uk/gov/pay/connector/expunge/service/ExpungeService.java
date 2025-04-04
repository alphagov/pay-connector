package uk.gov.pay.connector.expunge.service;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.ExpungeConfig;

import jakarta.inject.Inject;

public class ExpungeService {

    private ChargeExpungeService chargeExpungeService;
    private RefundExpungeService refundExpungeService;
    private ExpungeConfig expungeConfig;

    @Inject
    public ExpungeService(ChargeExpungeService chargeExpungeService, RefundExpungeService refundExpungeService,
                          ConnectorConfiguration connectorConfiguration) {
        this.chargeExpungeService = chargeExpungeService;
        this.refundExpungeService = refundExpungeService;
        expungeConfig = connectorConfiguration.getExpungeConfig();
    }

    public void expunge(Integer noOfChargesToExpungeQueryParam, Integer noOfRefundsToExpungeQueryParam) {
        int noOfChargesToExpunge = getNumberOfChargesToExpunge(noOfChargesToExpungeQueryParam);
        chargeExpungeService.expunge(noOfChargesToExpunge);

        int noOfRefundsToExpunge = getNumberOfRefundsToExpunge(noOfRefundsToExpungeQueryParam);
        refundExpungeService.expunge(noOfRefundsToExpunge);
    }

    private int getNumberOfChargesToExpunge(Integer noOfChargesToExpungeQueryParam) {
        if (noOfChargesToExpungeQueryParam != null && noOfChargesToExpungeQueryParam > 0) {
            return noOfChargesToExpungeQueryParam;
        }
        return expungeConfig.getNumberOfChargesToExpunge();
    }

    private int getNumberOfRefundsToExpunge(Integer noOfRefundsToExpungeQueryParam) {
        if (noOfRefundsToExpungeQueryParam != null && noOfRefundsToExpungeQueryParam > 0) {
            return noOfRefundsToExpungeQueryParam;
        }
        return expungeConfig.getNumberOfRefundsToExpunge();
    }
}
