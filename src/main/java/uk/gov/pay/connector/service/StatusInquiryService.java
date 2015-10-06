package uk.gov.pay.connector.service;

import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.StatusResponse;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.worldpay.WorldpayNotification;
import uk.gov.pay.connector.service.worldpay.WorldpayStatusesMapper;
import uk.gov.pay.connector.util.XMLUnmarshaller;

import javax.xml.bind.JAXBException;

import static uk.gov.pay.connector.resources.PaymentProviderValidator.WORLDPAY_PROVIDER;

public class StatusInquiryService {

    private GatewayAccountDao accountDao;
    private final ChargeDao chargeDao;
    private PaymentProviders providers;

    public StatusInquiryService(GatewayAccountDao accountDao, ChargeDao chargeDao, PaymentProviders providers) {
        this.accountDao = accountDao;
        this.chargeDao = chargeDao;
        this.providers = providers;
    }

    public Boolean handleWorldpayNotification(String notification) throws JAXBException {
        WorldpayNotification chargeNotification = XMLUnmarshaller.unmarshall(notification, WorldpayNotification.class);

        PaymentProvider worldpayProvider = providers.resolve(WORLDPAY_PROVIDER);
        StatusResponse statusResponse = worldpayProvider.enquire(chargeNotification);

        System.out.println("chargeNotification.getStatus() = " + chargeNotification.getStatus());
        ChargeStatus newChargeStatus = WorldpayStatusesMapper.getChargeStatus(chargeNotification.getStatus());
        chargeDao.updateStatusWithGatewayInfo(chargeNotification.getTransactionId(), newChargeStatus);
        return statusResponse.getStatus() != null;
    }
}
