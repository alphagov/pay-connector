package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.gatewayaccount.dao.Worldpay3dsFlexCredentialsDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsRequest;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity.Worldpay3dsFlexCredentialsEntityBuilder.aWorldpay3dsFlexCredentialsEntity;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

public class Worldpay3dsFlexCredentialsService {

    private Worldpay3dsFlexCredentialsDao worldpay3dsFlexCredentialsDao;
    private GatewayAccountCredentialsService gatewayAccountCredentialsService;

    @Inject
    public Worldpay3dsFlexCredentialsService(Worldpay3dsFlexCredentialsDao worldpay3dsFlexCredentialsDao,
                                             GatewayAccountCredentialsService gatewayAccountCredentialsService) {
        this.worldpay3dsFlexCredentialsDao = worldpay3dsFlexCredentialsDao;
        this.gatewayAccountCredentialsService = gatewayAccountCredentialsService;
    }

    @Transactional
    public void setGatewayAccountWorldpay3dsFlexCredentials(Worldpay3dsFlexCredentialsRequest worldpay3DsFlexCredentialsRequest, GatewayAccountEntity gatewayAccountEntity) {
        worldpay3dsFlexCredentialsDao.findByGatewayAccountId(gatewayAccountEntity.getId()).ifPresentOrElse(worldpay3dsFlexCredentialsEntity -> {
            worldpay3dsFlexCredentialsEntity.setIssuer(worldpay3DsFlexCredentialsRequest.getIssuer());
            worldpay3dsFlexCredentialsEntity.setJwtMacKey(worldpay3DsFlexCredentialsRequest.getJwtMacKey());
            worldpay3dsFlexCredentialsEntity.setOrganisationalUnitId(worldpay3DsFlexCredentialsRequest.getOrganisationalUnitId());
            worldpay3dsFlexCredentialsDao.merge(worldpay3dsFlexCredentialsEntity);
        }, () -> {
            var newWorldpay3dsFlexCredentialsEntity = aWorldpay3dsFlexCredentialsEntity()
                    .withGatewayAccountId(gatewayAccountEntity.getId())
                    .withIssuer(worldpay3DsFlexCredentialsRequest.getIssuer())
                    .withJwtMacKey(worldpay3DsFlexCredentialsRequest.getJwtMacKey())
                    .withOrganisationalUnitId(worldpay3DsFlexCredentialsRequest.getOrganisationalUnitId())
                    .build();
            worldpay3dsFlexCredentialsDao.merge(newWorldpay3dsFlexCredentialsEntity);
        });

        //TODO: To move Flex credentials to gateway account credentials level so correct Worldpay credential can be updated (as part of PP-10143)
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = gatewayAccountEntity.getCurrentOrActiveGatewayAccountCredential()
                .orElseThrow(() -> new WebApplicationException(
                        serviceErrorResponse(format("Active or current credential not found for gateway account [%s]", gatewayAccountEntity.getId()))));

        gatewayAccountCredentialsService.updateStateForCredentials(gatewayAccountCredentialsEntity);
    }
}
