package uk.gov.pay.connector.model.domain;

public class AddressFixture {
    private String line1 = "125 Kingsway";
    private String line2 = "Aviation House";
    private String postcode = "WC2B 6NH";
    private String city = "London";
    private String county = "London";
    private String country = "United Kingdom";

    public static AddressFixture aValidAddress() {
        return new AddressFixture();
    }

    public AddressEntity build() {
        AddressEntity addressEntity = new AddressEntity();
        addressEntity.setLine1(line1);
        addressEntity.setLine2(line2);
        addressEntity.setPostcode(postcode);
        addressEntity.setCity(city);
        addressEntity.setCounty(county);
        addressEntity.setCountry(country);
        return addressEntity;
    }

    public AddressFixture withLine1(String line1) {
        this.line1 = line1;
        return this;
    }

    public AddressFixture withLine2(String line2) {
        this.line2 = line2;
        return this;
    }

    public AddressFixture withPostcode(String postcode) {
        this.postcode = postcode;
        return this;
    }

    public AddressFixture withCity(String city) {
        this.city = city;
        return this;
    }

    public AddressFixture withCounty(String county) {
        this.county = county;
        return this;
    }

    public AddressFixture withCountry(String country) {
        this.country = country;
        return this;
    }
}
