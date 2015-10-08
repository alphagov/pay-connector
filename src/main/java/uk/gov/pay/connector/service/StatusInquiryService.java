package uk.gov.pay.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.StatusResponse;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.smartpay.SmartpayNotification;
import uk.gov.pay.connector.service.smartpay.SmartpayNotificationList;
import uk.gov.pay.connector.service.worldpay.WorldpayNotification;

import javax.xml.bind.JAXBException;

import java.io.IOException;
import java.util.List;

import static java.lang.String.format;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.WORLDPAY_PROVIDER;
import static uk.gov.pay.connector.service.worldpay.WorldpayStatusesMapper.mapToChargeStatus;
import static uk.gov.pay.connector.util.XMLUnmarshaller.unmarshall;

public class StatusInquiryService {

    private GatewayAccountDao accountDao;
    private final ChargeDao chargeDao;
    private PaymentProviders providers;

    private static final Logger logger = LoggerFactory.getLogger(StatusInquiryService.class);

    public StatusInquiryService(GatewayAccountDao accountDao, ChargeDao chargeDao, PaymentProviders providers) {
        this.accountDao = accountDao;
        this.chargeDao = chargeDao;
        this.providers = providers;
    }

    public void handleSmartpayNotification(String notification) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            SmartpayNotificationList smartpayNotificationList = mapper.readValue(notification, SmartpayNotificationList.class);
            System.out.println("event code = " + smartpayNotificationList.getNotifications().get(0).getEventCode());
            //TODO: inquire the charge status to smartpay
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean handleWorldpayNotification(String notification) {
        try {
            WorldpayNotification chargeNotification = unmarshall(notification, WorldpayNotification.class);
            PaymentProvider worldpayProvider = providers.resolve(WORLDPAY_PROVIDER);
            StatusResponse statusResponse = worldpayProvider.enquire(chargeNotification);

            return updateChargeStatus(statusResponse);
        } catch (JAXBException e) {
            logger.error(format("Could not deserialise worldpay response %s", notification), e);
            return false;
        }
    }

    //TODO: we should play different stories to check the initial status and the new one and act accordingly
    private boolean updateChargeStatus(StatusResponse statusResponse) {
        String worldpayStatus = statusResponse.getStatus();
        ChargeStatus newChargeStatus = mapToChargeStatus(worldpayStatus);
        if (newChargeStatus != null) {
            chargeDao.updateStatusWithGatewayInfo(statusResponse.getTransactionId(), newChargeStatus);
            return true;
        } else if (statusResponse.getStatus() == null) {
            return false;
        } else {
            logger.error(format("Could not map worldpay status %s to our internal status.", worldpayStatus));
            return true;
        }
    }
}
