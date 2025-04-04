package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.gatewayaccount.dao.Worldpay3dsFlexCredentialsDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsRequest;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;

import jakarta.inject.Inject;

import static uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity.Worldpay3dsFlexCredentialsEntityBuilder.aWorldpay3dsFlexCredentialsEntity;

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
            gatewayAccountEntity.setWorldpay3dsFlexCredentialsEntity(newWorldpay3dsFlexCredentialsEntity);
            worldpay3dsFlexCredentialsDao.merge(newWorldpay3dsFlexCredentialsEntity);
        });

        gatewayAccountCredentialsService.updateStatePostFlexCredentialsUpdate(gatewayAccountEntity);
    }
}
