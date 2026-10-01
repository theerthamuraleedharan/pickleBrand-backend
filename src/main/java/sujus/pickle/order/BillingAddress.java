package sujus.pickle.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import sujus.pickle.profile.Address;

@Embeddable
public class BillingAddress {
    @Column(name = "billing_recipient_name", nullable = false, length = 150)
    private String recipientName;
    @Column(name = "billing_phone", nullable = false, length = 30)
    private String phone;
    @Column(name = "billing_address_line_1", nullable = false, length = 255)
    private String addressLine1;
    @Column(name = "billing_address_line_2", length = 255)
    private String addressLine2;
    @Column(name = "billing_city", nullable = false, length = 100)
    private String city;
    @Column(name = "billing_state", length = 100)
    private String state;
    @Column(name = "billing_postal_code", nullable = false, length = 20)
    private String postalCode;
    @Column(name = "billing_country", nullable = false, length = 100)
    private String country;

    protected BillingAddress() { }

    public BillingAddress(Address address) {
        recipientName = address.getRecipientName();
        phone = address.getPhone();
        addressLine1 = address.getAddressLine1();
        addressLine2 = address.getAddressLine2();
        city = address.getCity();
        state = address.getState();
        postalCode = address.getPostalCode();
        country = address.getCountry();
    }

    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
}
