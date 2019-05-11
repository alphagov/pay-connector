package uk.gov.pay.connector.gateway.epdq;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.util.Map;

public class EpdqStatusMapper {
    private EpdqStatusMapper() { }
    
    private static Map<String, ChargeStatus> epdqStatusMap;
    
    static {
        epdqStatusMap = Map.of(
                "2",  ChargeStatus.AUTHORISATION_REJECTED,
                "46", ChargeStatus.AUTHORISATION_3DS_REQUIRED,
                "5",  ChargeStatus.AUTHORISATION_SUCCESS,
                "50", ChargeStatus.AUTHORISATION_SUBMITTED,
                "51", ChargeStatus.AUTHORISATION_SUBMITTED,
                "52", ChargeStatus.AUTHORISATION_SUBMITTED,
                "6",  ChargeStatus.USER_CANCELLED,
                "61", ChargeStatus.USER_CANCEL_SUBMITTED,
                "9",  ChargeStatus.CAPTURED,
                "91", ChargeStatus.CAPTURED);
    }
     
    public static ChargeStatus map(String epdqStatus) {
        return epdqStatusMap.getOrDefault(epdqStatus, null);
    }
}
