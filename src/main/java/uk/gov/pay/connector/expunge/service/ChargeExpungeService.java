package uk.gov.pay.connector.expunge.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.app.config.ExpungeConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.tasks.service.ParityCheckService;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.SKIPPED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.service.payments.logging.LoggingKeys.MDC_REQUEST_ID_KEY;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class ChargeExpungeService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ChargeDao chargeDao;
    private final ExpungeConfig expungeConfig;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final ParityCheckService parityCheckService;
    private final ChargeService chargeService;
    private final List<PaymentGatewayName> expungeExemptedGateways = List.of(
            PaymentGatewayName.EPDQ,
            PaymentGatewayName.WORLDPAY,
            PaymentGatewayName.STRIPE);

    private final List<ChargeStatus> expungeExemptChargeStatuses = List.of(
            AUTHORISATION_ERROR,
            AUTHORISATION_TIMEOUT,
            AUTHORISATION_UNEXPECTED_ERROR);

    @Inject
    public ChargeExpungeService(ChargeDao chargeDao, ConnectorConfiguration connectorConfiguration,
                                ParityCheckService parityCheckService,
                                ChargeService chargeService) {
        this.chargeDao = chargeDao;
        expungeConfig = connectorConfiguration.getExpungeConfig();
        stripeGatewayConfig = connectorConfiguration.getStripeConfig();
        this.parityCheckService = parityCheckService;
        this.chargeService = chargeService;
    }

    private boolean inTerminalState(ChargeEntity chargeEntity) {
        long ageInDays = ChronoUnit.DAYS.between(chargeEntity.getCreatedDate(), ZonedDateTime.now());
        boolean chargeIsHistoric = ageInDays > expungeConfig.getMinimumAgeForHistoricChargeExceptions();
        ChargeStatus status = ChargeStatus.fromString(chargeEntity.getStatus());
        if (chargeIsHistoric && status.equals(ChargeStatus.CAPTURE_SUBMITTED)) {
            return true;
        }
        if (expungeExemptedGateways.contains(chargeEntity.getPaymentGatewayName()) &&
                expungeExemptChargeStatuses.contains(status)) {
            return false;
        }
        return status.isExpungeable();
    }

    public void expunge(Integer noOfChargesToExpunge) {
        if (!expungeConfig.isExpungeChargesEnabled()) {
            logger.info("Charge expunging feature is disabled. No charges have been expunged");
        } else {
            int minimumAgeOfChargeInDays = expungeConfig.getMinimumAgeOfChargeInDays();
            int createdWithinLast = expungeConfig.getExcludeChargesOrRefundsParityCheckedWithInDays();

            for (int number = 0; number < noOfChargesToExpunge; number++) {
                Optional<ChargeEntity> mayBeChargeToExpunge = chargeDao.findChargeToExpunge(minimumAgeOfChargeInDays, createdWithinLast);

                if (mayBeChargeToExpunge.isPresent()) {
                    ChargeEntity chargeEntity = mayBeChargeToExpunge.get();
                    MDC.put(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId());
                    logger.info(format("Attempting to expunge charge %s", chargeEntity.getExternalId()));
                    try {
                        parityCheckAndExpungeIfMet(chargeEntity);
                    } catch (OptimisticLockException error) {
                        logger.info("Expunging process conflicted with an already running process, exit");
                        MDC.remove(MDC_REQUEST_ID_KEY);
                        MDC.remove(PAYMENT_EXTERNAL_ID);
                        throw error;
                    }
                    MDC.remove(PAYMENT_EXTERNAL_ID);
                } else {
                    break;
                }
            }
        }
    }

    private void parityCheckAndExpungeIfMet(ChargeEntity chargeEntity) {
        boolean hasChargeBeenParityCheckedBefore = chargeEntity.getParityCheckDate() != null;

        if (!inTerminalState(chargeEntity)) {
            chargeService.updateChargeParityStatus(chargeEntity.getExternalId(), SKIPPED);
            logger.info("Charge not expunged because it is not in a terminal state",
                    kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()));
        } else if (chargeEntity.getPaymentGatewayName() == STRIPE && isStripePaymentMissingFees(chargeEntity)) {
            chargeService.updateChargeParityStatus(chargeEntity.getExternalId(), SKIPPED);
            logger.info("Charge not expunged because it is a Stripe payment that requires fees to be collected, but " +
                            "fees have not yet been processed.",
                    kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()));
        } else if (parityCheckService.parityCheckChargeForExpunger(chargeEntity)) {
            expungeCharge(chargeEntity);
            logger.info("Charge expunged from connector {}", kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()));
        } else {
            if (hasChargeBeenParityCheckedBefore) {
                logger.error("Charge cannot be expunged because parity check with ledger repeatedly failed",
                        kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()));
            } else {
                logger.info("Charge cannot be expunged because parity check with ledger failed",
                        kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()));
            }
        }
    }

    private boolean isStripePaymentMissingFees(ChargeEntity chargeEntity) {
        // We collect fees asynchronously for failed Stripe payments that we have attempted to authorise with Stripe -
        // and so have a gateway transaction id. We want to prevent these payments from being expunged if we have not
        // yet collected the fee.
        return isNotBlank(chargeEntity.getGatewayTransactionId()) &&
                chargeEntity.getFees().isEmpty();
    }

    @Transactional
    public void expungeCharge(ChargeEntity chargeEntity) {
        chargeDao.expungeCharge(chargeEntity.getId(), chargeEntity.getExternalId());
    }

}
