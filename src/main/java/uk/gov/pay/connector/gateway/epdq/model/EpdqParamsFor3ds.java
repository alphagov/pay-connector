package uk.gov.pay.connector.gateway.epdq.model;

import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;

public class EpdqParamsFor3ds implements GatewayParamsFor3ds {

    private final String htmlOut;

    public EpdqParamsFor3ds(String htmlOut) {
        this.htmlOut = htmlOut;
    }

    @Override
    public Auth3dsDetailsEntity toAuth3dsDetailsEntity() {
        Auth3dsDetailsEntity auth3dsDetailsEntity = new Auth3dsDetailsEntity();
        auth3dsDetailsEntity.setHtmlOut(htmlOut);
        return auth3dsDetailsEntity;
    }
}
