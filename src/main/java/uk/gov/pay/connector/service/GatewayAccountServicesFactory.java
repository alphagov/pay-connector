package uk.gov.pay.connector.service;

import uk.gov.pay.connector.service.notify.NotifySettingsUpdateService;

public interface GatewayAccountServicesFactory {

    NotifySettingsUpdateService getNotifySettingsUpdateService();
}
