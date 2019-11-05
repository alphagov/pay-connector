package uk.gov.pay.connector.gatewayaccount.service;

import uk.gov.pay.connector.gatewayaccount.dao.Worldpay3dsFlexCredentialsDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayUpdate3dsFlexCredentialsRequest;

import javax.inject.Inject;

import static uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity.Worldpay3dsFlexCredentialsEntityBuilder.aWorldpay3dsFlexCredentialsEntity;

public class Worldpay3dsFlexCredentialsService {

    private Worldpay3dsFlexCredentialsDao worldpay3dsFlexCredentialsDao;

    @Inject
    public Worldpay3dsFlexCredentialsService(Worldpay3dsFlexCredentialsDao worldpay3dsFlexCredentialsDao) {
        this.worldpay3dsFlexCredentialsDao = worldpay3dsFlexCredentialsDao;
    }

    public void setGatewayAccountWorldpay3dsFlexCredentials(WorldpayUpdate3dsFlexCredentialsRequest worldpayUpdate3dsFlexCredentialsRequest, GatewayAccountEntity gatewayAccountEntity) {
        worldpay3dsFlexCredentialsDao.findByGatewayAccountId(gatewayAccountEntity.getId()).ifPresentOrElse(worldpay3dsFlexCredentialsEntity -> {
            worldpay3dsFlexCredentialsEntity.setIssuer(worldpayUpdate3dsFlexCredentialsRequest.getIssuer());
            worldpay3dsFlexCredentialsEntity.setJwtMacKey(worldpayUpdate3dsFlexCredentialsRequest.getJwtMacKey());
            worldpay3dsFlexCredentialsEntity.setOrganisationalUnitId(worldpayUpdate3dsFlexCredentialsRequest.getOrganisationalUnitId());
            worldpay3dsFlexCredentialsDao.merge(worldpay3dsFlexCredentialsEntity);
        }, () -> {
            var newWorldpay3dsFlexCredentialsEntity = aWorldpay3dsFlexCredentialsEntity()
                    .withGatewayAccountId(gatewayAccountEntity.getId())
                    .withIssuer(worldpayUpdate3dsFlexCredentialsRequest.getIssuer())
                    .withJwtMacKey(worldpayUpdate3dsFlexCredentialsRequest.getJwtMacKey())
                    .withOrganisationalUnitId(worldpayUpdate3dsFlexCredentialsRequest.getOrganisationalUnitId())
                    .build();
            worldpay3dsFlexCredentialsDao.merge(newWorldpay3dsFlexCredentialsEntity);
        });
    }
}
