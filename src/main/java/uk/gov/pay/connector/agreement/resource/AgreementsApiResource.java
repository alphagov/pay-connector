package uk.gov.pay.connector.agreement.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.agreement.service.AgreementService;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
public class AgreementsApiResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgreementsApiResource.class);

    private static final String ACCOUNT_ID = "accountId";

    private final AgreementService agreementService;

    @Inject
    public AgreementsApiResource(AgreementService agreementService) {
        this.agreementService = agreementService;
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/agreements")
    @Produces(APPLICATION_JSON)
    public AgreementResponse createNewCharge(
            @PathParam(ACCOUNT_ID) Long accountId,
            @NotNull @Valid AgreementCreateRequest agreementCreateRequest,
            @Context UriInfo uriInfo
    ) {
        LOGGER.info("Creating new agreement for gateway account ID {}", accountId);

        return agreementService.create(agreementCreateRequest, accountId)
                .orElseThrow(NotFoundException::new);
    }
}
