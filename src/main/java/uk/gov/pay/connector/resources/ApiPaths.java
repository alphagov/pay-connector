package uk.gov.pay.connector.resources;

import static uk.gov.pay.connector.resources.GatewayAccountResource.ACCOUNT_API_RESOURCE;

public interface ApiPaths {
    String CHARGE_RESOURCE = "/charges/{chargeId}";
    String FRONTEND_RESOURCE = "/v1/frontend";

    String OLD_CHARGES_API_PATH = "/v1/api/charges/";
    String OLD_GET_CHARGE_API_PATH = OLD_CHARGES_API_PATH + "{chargeId}";

    String OLD_CHARGES_FRONTEND_PATH = "/v1/frontend/charges/";
    String OLD_GET_CHARGE_FRONTEND_PATH = FRONTEND_RESOURCE + CHARGE_RESOURCE;


    String FRONTEND_AUTHORIZATION_RESOURCE = FRONTEND_RESOURCE + CHARGE_RESOURCE + "/cards";
    String FRONTEND_CAPTURE_RESOURCE = FRONTEND_RESOURCE + CHARGE_RESOURCE + "/capture";

    String CHARGES_API_PATH = ACCOUNT_API_RESOURCE + CHARGE_RESOURCE;
    String CANCEL_CHARGE_PATH = CHARGES_API_PATH + "/cancel";
}
