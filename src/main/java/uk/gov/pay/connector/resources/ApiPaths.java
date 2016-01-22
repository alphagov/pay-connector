package uk.gov.pay.connector.resources;

import static uk.gov.pay.connector.resources.GatewayAccountResource.ACCOUNT_API_RESOURCE;

public interface ApiPaths {
    //TODO: For backwards compatible purposes only. This commit will be reverted.
    String CHARGES_RESOURCE = "/charges";
    String CHARGE_RESOURCE = CHARGES_RESOURCE + "/{chargeId}";
    String FRONTEND_RESOURCE = "/v1/frontend";

    String CHARGES_FRONTEND_PATH = FRONTEND_RESOURCE + CHARGES_RESOURCE;
    String CHARGE_FRONTEND_PATH = FRONTEND_RESOURCE + CHARGE_RESOURCE;


    String FRONTEND_AUTHORIZATION_RESOURCE = FRONTEND_RESOURCE + CHARGE_RESOURCE + "/cards";
    String FRONTEND_CAPTURE_RESOURCE = FRONTEND_RESOURCE + CHARGE_RESOURCE + "/capture";

    String CHARGES_SEARCH_API_PATH = ACCOUNT_API_RESOURCE + "/search";
    String CHARGE_API_PATH = ACCOUNT_API_RESOURCE + CHARGE_RESOURCE;
    String CHARGES_API_PATH = ACCOUNT_API_RESOURCE + CHARGES_RESOURCE;
    String CANCEL_CHARGE_PATH = CHARGE_API_PATH + "/cancel";

    String CHARGE_EVENTS_API_PATH = CHARGE_API_PATH + "/events";
}
