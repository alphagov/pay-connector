package uk.gov.pay.connector.resources;

import static uk.gov.pay.connector.resources.GatewayAccountResource.ACCOUNT_API_RESOURCE;

public interface ApiPaths {
    String CHARGES_RESOURCE = "/charges";
    String CHARGE_RESOURCE = CHARGES_RESOURCE + "/{chargeId}";
    String FRONTEND_RESOURCE = "/v1/frontend";

    String CHARGES_FRONTEND_PATH = "/v1/frontend/charges/";
    String GET_CHARGE_FRONTEND_PATH = FRONTEND_RESOURCE + CHARGE_RESOURCE;


    String FRONTEND_AUTHORIZATION_RESOURCE = FRONTEND_RESOURCE + CHARGE_RESOURCE + "/cards";
    String FRONTEND_CAPTURE_RESOURCE = FRONTEND_RESOURCE + CHARGE_RESOURCE + "/capture";

    String CHARGE_API_PATH = ACCOUNT_API_RESOURCE + CHARGE_RESOURCE;
    String CHARGES_API_PATH = ACCOUNT_API_RESOURCE + CHARGES_RESOURCE;
    String CANCEL_CHARGE_PATH = CHARGE_API_PATH + "/cancel";
}
