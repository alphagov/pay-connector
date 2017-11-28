package uk.gov.pay.connector.service;


public interface GatewayAccountServicesFactory {

    GatewayAccountUpdater getUpdateService();
}
