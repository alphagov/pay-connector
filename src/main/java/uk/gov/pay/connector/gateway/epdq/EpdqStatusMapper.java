package uk.gov.pay.connector.gateway.epdq;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EpdqStatusMapper {
    private EpdqStatusMapper() {
        
    }
    
    private static  Map<String, ChargeStatus> epdqStatusMap;
    
    static {
        Map<String, ChargeStatus> aMap = new HashMap<>();
        aMap.put("5", ChargeStatus.AUTHORISATION_SUCCESS);
        aMap.put("51", ChargeStatus.AUTHORISATION_SUCCESS);
        aMap.put("46", ChargeStatus.AUTHORISATION_3DS_REQUIRED);
        aMap.put("50", ChargeStatus.AUTHORISATION_SUBMITTED);
        aMap.put("51", ChargeStatus.AUTHORISATION_SUBMITTED);
        aMap.put("2", ChargeStatus.AUTHORISATION_REJECTED);
        aMap.put("6", ChargeStatus.USER_CANCELLED);
        aMap.put("61", ChargeStatus.AUTHORISATION_SUBMITTED);
        aMap.put("9", ChargeStatus.CAPTURED);
        aMap.put("91", ChargeStatus.CAPTURED);

        epdqStatusMap = Collections.unmodifiableMap(aMap);
    }
     
    public static ChargeStatus map(String epdqStatus) {
        return epdqStatusMap.get(epdqStatus);
    }
}
