package uk.gov.pay.connector.agreement.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.agreement.service.AgreementService;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import static org.apache.http.HttpStatus.SC_CREATED;

@Path("/")
public class AgreementsApiResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgreementsApiResource.class);

    private final AgreementService agreementService;

    @Inject
    public AgreementsApiResource(AgreementService agreementService) {
        this.agreementService = agreementService;
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/agreements")
    @Produces("application/json")
    @Consumes("application/json")
    public Response createAgreement(
            @PathParam("accountId") Long accountId,
            @Valid AgreementCreateRequest agreementCreateRequest
    ) {
        LOGGER.info("Creating new agreement for gateway account ID {}", accountId);
        AgreementResponse agreementResponse = agreementService.create(agreementCreateRequest, accountId).orElseThrow(NotFoundException::new);
        return Response.status(SC_CREATED).entity(agreementResponse).build();
    }
}
