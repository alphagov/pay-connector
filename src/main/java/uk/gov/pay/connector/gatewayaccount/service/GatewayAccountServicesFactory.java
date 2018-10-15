package uk.gov.pay.connector.gatewayaccount.service;


import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountUpdater;

public interface GatewayAccountServicesFactory {

    GatewayAccountUpdater getUpdateService();
}
