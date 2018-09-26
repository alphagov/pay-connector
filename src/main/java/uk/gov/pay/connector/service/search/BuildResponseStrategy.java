package uk.gov.pay.connector.service.search;

import uk.gov.pay.connector.dao.SearchParams;

import javax.ws.rs.core.UriInfo;
import java.util.List;

public interface BuildResponseStrategy<T, R> {

    long getTotalFor(SearchParams params);

    List<T> findAllBy(SearchParams params);

    R buildResponse(UriInfo uriInfo, T entity);
}
