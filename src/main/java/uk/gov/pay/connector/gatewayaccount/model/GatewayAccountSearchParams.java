package uk.gov.pay.connector.gatewayaccount.model;

import uk.gov.pay.connector.common.model.api.CommaDelimitedSetParameter;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GatewayAccountSearchParams {

    private static final String ACCOUNT_IDS_SQL_FIELD = "accountIds";
    private static final String ALLOW_MOTO_SQL_FIELD = "allowMoto";
    
    @DefaultValue("")
    @QueryParam("accountIds")
    private CommaDelimitedSetParameter accountIds;
    @QueryParam("moto_enabled")
    private Boolean motoEnabled;

    public void setAccountIds(CommaDelimitedSetParameter accountIds) {
        this.accountIds = accountIds;
    }

    public void setMotoEnabled(Boolean motoEnabled) {
        this.motoEnabled = motoEnabled;
    }

    public List<String> getFilterTemplates() {
        List<String> filters = new ArrayList<>();

        if (accountIds != null && accountIds.isNotEmpty()) {
            filters.add(" gae.id IN :" + ACCOUNT_IDS_SQL_FIELD);
        }
        if (motoEnabled != null) {
            filters.add(" gae.allowMoto = :" + ALLOW_MOTO_SQL_FIELD);
        }
        
        return List.copyOf(filters);
    }
    
    public Map<String, Object> getQueryMap() {
        HashMap<String, Object> queryMap = new HashMap<>();
        
        if (accountIds != null && accountIds.isNotEmpty()) {
            queryMap.put(ACCOUNT_IDS_SQL_FIELD, accountIds.getParameters());
        }
        if (motoEnabled != null) {
            queryMap.put(ALLOW_MOTO_SQL_FIELD, motoEnabled);
        }
        
        return queryMap;
    }
}
