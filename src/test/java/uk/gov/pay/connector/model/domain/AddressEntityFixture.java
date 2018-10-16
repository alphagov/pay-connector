package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.charge.model.AddressEntity;

public final class AddressEntityFixture {

    private String line1 = "The White Chapel Building";
    private String line2 = "10 Whitechapel High Street";
    private String postcode = "E1 8QS";
    private String city = "London";
    private String county = "Greater London";
    private String country = "United Kingdom";

    private AddressEntityFixture() {
    }

    public static AddressEntityFixture aValidAddressEntity() {
        return new AddressEntityFixture();
    }

    public AddressEntityFixture withLine1(String line1) {
        this.line1 = line1;
        return this;
    }

    public AddressEntityFixture withLine2(String line2) {
        this.line2 = line2;
        return this;
    }

    public AddressEntityFixture withPostcode(String postcode) {
        this.postcode = postcode;
        return this;
    }

    public AddressEntityFixture withCity(String city) {
        this.city = city;
        return this;
    }

    public AddressEntityFixture withCounty(String county) {
        this.county = county;
        return this;
    }

    public AddressEntityFixture withCountry(String country) {
        this.country = country;
        return this;
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
}
