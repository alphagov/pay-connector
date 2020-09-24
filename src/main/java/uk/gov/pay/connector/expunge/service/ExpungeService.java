package uk.gov.pay.connector.expunge.service;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.ExpungeConfig;

import javax.inject.Inject;

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

    public void expunge(Integer noOfChargesOrRefundsToExpungeQueryParam) {
        int noOfRecordsToExpunge = getNumberOfRecordsToExpunge(noOfChargesOrRefundsToExpungeQueryParam);
        chargeExpungeService.expunge(noOfRecordsToExpunge);
        refundExpungeService.expunge(noOfRecordsToExpunge);
    }

    private int getNumberOfRecordsToExpunge(Integer noOfChargesToExpungeQueryParam) {
        if (noOfChargesToExpungeQueryParam != null && noOfChargesToExpungeQueryParam > 0) {
            return noOfChargesToExpungeQueryParam;
        }
        return expungeConfig.getNumberOfChargesOrRefundsToExpunge();
    }
}
