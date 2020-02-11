package uk.gov.pay.connector.gatewayaccount.model;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.common.model.api.CommaDelimitedSetParameter;

import javax.validation.constraints.Pattern;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GatewayAccountSearchParams {

    private static final String ACCOUNT_IDS_SQL_FIELD = "accountIds";
    private static final String ALLOW_MOTO_SQL_FIELD = "allowMoto";
    
    @QueryParam("accountIds")
    private CommaDelimitedSetParameter accountIds;
    
    // This is a string value rather than boolean as if the parameter isn't provided, it should not filter by
    // moto enabled/disabled
    @QueryParam("moto_enabled")
    @Pattern(regexp = "true|false",
            message = "Parameter [moto_enabled] must be true or false")
    private String motoEnabled;

    public void setAccountIds(CommaDelimitedSetParameter accountIds) {
        this.accountIds = accountIds;
    }

    public void setMotoEnabled(String motoEnabled) {
        this.motoEnabled = motoEnabled;
    }

    public List<String> getFilterTemplates() {
        List<String> filters = new ArrayList<>();

        if (accountIds != null && accountIds.isNotEmpty()) {
            filters.add(" gae.id IN :" + ACCOUNT_IDS_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(motoEnabled)) {
            filters.add(" gae.allowMoto = :" + ALLOW_MOTO_SQL_FIELD);
        }
        
        return List.copyOf(filters);
    }
    
    public Map<String, Object> getQueryMap() {
        HashMap<String, Object> queryMap = new HashMap<>();
        
        if (accountIds != null && accountIds.isNotEmpty()) {
            queryMap.put(ACCOUNT_IDS_SQL_FIELD, accountIds.getParameters());
        }
        if (StringUtils.isNotEmpty(motoEnabled)) {
            queryMap.put(ALLOW_MOTO_SQL_FIELD, Boolean.valueOf(motoEnabled));
        }
        
        return queryMap;
    }

    @Override
    public String toString() {
        return "accountIds=" + accountIds +
                ", moto_enabled=" + motoEnabled;
    }
}
