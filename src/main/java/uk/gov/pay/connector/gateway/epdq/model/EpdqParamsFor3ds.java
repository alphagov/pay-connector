package uk.gov.pay.connector.gateway.epdq.model;

import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;

public class EpdqParamsFor3ds implements GatewayParamsFor3ds {

    private final String htmlOut;

    public EpdqParamsFor3ds(String htmlOut) {
        this.htmlOut = htmlOut;
    }

    @Override
    public Auth3dsRequiredEntity toAuth3dsRequiredEntity() {
        Auth3dsRequiredEntity auth3DsRequiredEntity = new Auth3dsRequiredEntity();
        auth3DsRequiredEntity.setHtmlOut(htmlOut);
        return auth3DsRequiredEntity;
    }
}
