package uk.gov.pay.connector.resources;

import static uk.gov.pay.connector.resources.GatewayAccountResource.ACCOUNT_API_RESOURCE;

public interface ApiPaths {
    String CHARGES_RESOURCE = "/charges";
    String CHARGE_RESOURCE = CHARGES_RESOURCE + "/{chargeId}";
    String FRONTEND_RESOURCE = "/v1/frontend";

    String FRONTEND_CHARGE_RESOURCE = FRONTEND_RESOURCE + CHARGE_RESOURCE;

    String FRONTEND_AUTHORIZATION_RESOURCE = FRONTEND_RESOURCE + CHARGE_RESOURCE + "/cards";
    String FRONTEND_CAPTURE_RESOURCE = FRONTEND_RESOURCE + CHARGE_RESOURCE + "/capture";
    String FRONTEND_CANCEL_RESOURCE = FRONTEND_RESOURCE + CHARGE_RESOURCE + "/cancel";

    String CHARGE_API_RESOURCE = ACCOUNT_API_RESOURCE + CHARGE_RESOURCE;
    String CHARGES_API_RESOURCE = ACCOUNT_API_RESOURCE + CHARGES_RESOURCE;
    String CANCEL_CHARGE_RESOURCE = CHARGE_API_RESOURCE + "/cancel";

    String CHARGE_EVENTS_API_RESOURCE = CHARGE_API_RESOURCE + "/events";
    String EXPIRE_CHARGES = "/v1/tasks/expired-charges-sweep";
}
