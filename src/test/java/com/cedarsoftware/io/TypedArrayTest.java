package com.cedarsoftware.io;

import com.cedarsoftware.io.models.array.Customer;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

class TypedArrayTest {

    @Test
    void testCreateCustomer() {
        String json = ClassUtilities.loadResourceAsString("customer/customer.json");
        Customer customer = TestUtil.toObjects(json, Customer.class);
        String json2 = TestUtil.toJson(customer);
        Customer customer2 = TestUtil.toObjects(json2, Customer.class);
        // Test Java graphs are equivalent
        assert DeepEquals.deepEquals(customer, customer2);

        json = JsonIo.formatJson(json);
        json2 = JsonIo.formatJson(json2);
        // Test JSON strigns are equivalent
        assert json.equals(json2);

        // Booger up one field deep in the POJO hierarchy
        customer.getLocations()[0].getBuildings()[0].getAssets()[0].setValue(1.1d);
        assert !DeepEquals.deepEquals(customer, customer2);  // no longer equal
    }
}
