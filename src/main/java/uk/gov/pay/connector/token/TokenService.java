package uk.gov.pay.connector.token;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.charge.exception.motoapi.OneTimeTokenAlreadyUsedException;
import uk.gov.pay.connector.charge.exception.motoapi.OneTimeTokenInvalidException;
import uk.gov.pay.connector.charge.exception.motoapi.OneTimeTokenUsageInvalidForMotoApiException;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;

import jakarta.inject.Inject;

import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

public class TokenService {

    private final TokenDao tokenDao;

    @Inject
    public TokenService(TokenDao tokenDao) {
        this.tokenDao = tokenDao;
    }

    @Transactional
    public TokenEntity validateAndMarkTokenAsUsedForMotoApi(String oneTimeToken) {
        return tokenDao.findByTokenId(oneTimeToken)
                .map((TokenEntity tokenEntity) -> {
                    if (tokenEntity.isUsed()) {
                        throw new OneTimeTokenAlreadyUsedException();
                    }

                    if (tokenEntity.getChargeEntity() != null &&
                            tokenEntity.getChargeEntity().getAuthorisationMode() == MOTO_API) {
                        tokenEntity.setUsed(true);
                        return tokenEntity;
                    }

                    throw new OneTimeTokenUsageInvalidForMotoApiException();
                })
                .orElseThrow(OneTimeTokenInvalidException::new);
    }
}
