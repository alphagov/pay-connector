package uk.gov.pay.connector.service.search;

import uk.gov.pay.connector.dao.ChargeSearchParams;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

public interface SearchStrategy {

    Response search(ChargeSearchParams searchParams, UriInfo uriInfo);

}
