package uk.gov.pay.connector.model;

public class Session {
    private final String shopperIPAddress;
    private final String id;

    public Session(String shopperIPAddress, String id) {
        this.shopperIPAddress = shopperIPAddress;
        this.id = id;
    }

    public String getShopperIPAddress() {
        return shopperIPAddress;
    }

    public String getId() {
        return id;
    }
}
