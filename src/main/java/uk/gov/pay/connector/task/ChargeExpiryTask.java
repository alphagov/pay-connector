package uk.gov.pay.connector.task;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import fj.data.Either;
import io.dropwizard.servlets.tasks.Task;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.CardService;

import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class ChargeExpiryTask extends Task {

    public static final String EXPIRED_CHARGES_SWEEP = "expired-charges-sweep";
    public static final String CHARGE_EXPIRY_WINDOW = "CHARGE_EXPIRY_WINDOW_SECONDS";
    public static final int ONE_HOUR = 3600;
    public static final ArrayList<ChargeStatus> NON_TERMINAL_STATUSES = Lists.newArrayList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUBMITTED, AUTHORISATION_SUCCESS);

    private ChargeDao chargeDao;
    private CardService cardService;

    private final Logger logger = LoggerFactory.getLogger(ChargeExpiryTask.class);

    @Inject
    public ChargeExpiryTask(ChargeDao chargeDao, CardService cardService) {
        super(EXPIRED_CHARGES_SWEEP);
        this.chargeDao = chargeDao;
        this.cardService = cardService;
    }

    @Override
    @Transactional
    public void execute(ImmutableMultimap<String, String> immutableMultimap, PrintWriter printWriter) throws Exception {
        ZonedDateTime expiryWindow = ZonedDateTime.now().minusSeconds(getChargeExpiryWindowSeconds(immutableMultimap));
        List<ChargeEntity> charges = chargeDao.findBeforeDateWithStatusIn(expiryWindow, NON_TERMINAL_STATUSES);
        logger.info(format("number of charges found for expiring: %s", charges.size()));

        List<ChargeEntity> expiredCharges = charges.stream()
                .filter(this::filterExpirableCharges)
                .map(chargeEntity -> {
                    chargeEntity.setStatus(ChargeStatus.EXPIRED);
                    return chargeEntity;
                }).collect(Collectors.toList());

        logger.info(format("%s charges expired out of total %s charges found", expiredCharges.size(), charges.size()));
    }

    private int getChargeExpiryWindowSeconds(ImmutableMultimap<String, String> immutableMultimap) {
        //default expiry window, can be overridden by env var, which can be further overridden by query param for ease of testing
        int chargeExpiryWindowSeconds = ONE_HOUR;
        if (StringUtils.isNotBlank(System.getenv(CHARGE_EXPIRY_WINDOW))) {
            chargeExpiryWindowSeconds = Integer.parseInt(System.getenv(CHARGE_EXPIRY_WINDOW));
        }
        ImmutableList<String> expiryWindowQueryParam = immutableMultimap.get(CHARGE_EXPIRY_WINDOW).asList();
        if (expiryWindowQueryParam.size() > 0 && StringUtils.isNotBlank(expiryWindowQueryParam.get(0))) {
            chargeExpiryWindowSeconds = Integer.parseInt(expiryWindowQueryParam.get(0));
        }
        return chargeExpiryWindowSeconds;
    }

    private boolean filterExpirableCharges(ChargeEntity ce) {
        if (ce.getStatus().equals(AUTHORISATION_SUCCESS.getValue())) {
            logger.debug(format("sending a cancel request for charge ID: %s", ce.getId()));
            Either<ErrorResponse, GatewayResponse> cancelResponse = cardService.doCancel(ce.getId().toString(), ce.getGatewayAccount().getId());

            if (cancelResponse.isLeft()) {
                logger.error(format("gateway error: %s %s, while cancelling the charge ID %s",
                        cancelResponse.left().value().getMessage(),
                        cancelResponse.left().value().getErrorType(),
                        ce.getId()));
                return false;
            }
            if (!cancelResponse.right().value().isSuccessful()) {
                logger.error(format("gateway unsuccessful response: %s, while cancelling Charge ID: %s", cancelResponse.right().value().getError(), ce.getId()));
                return false;
            }
        }
        return true;
    }
}
