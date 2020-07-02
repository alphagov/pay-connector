package uk.gov.pay.connector.gateway.epdq;

import uk.gov.pay.connector.gateway.OrderRequestBuilder;

public class EpdqTemplateData extends OrderRequestBuilder.TemplateData {

    private String orderId;
    private String password;
    private String userId;
    private String amount;
    private String frontendBaseUrl;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String getAmount() {
        return amount;
    }

    @Override
    public void setAmount(String amount) {
        this.amount = amount;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendBaseUrl = frontendUrl;
    }

    public String getFrontendUrl() {
        return frontendBaseUrl;
    }
}
