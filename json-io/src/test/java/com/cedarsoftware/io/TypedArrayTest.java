package com.cedarsoftware.io;

import com.cedarsoftware.io.models.array.Customer;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

class TypedArrayTest {

    @Test
    void testCreateCustomer() {
        String json = ClassUtilities.loadResourceAsString("customer/customer.json");
        Customer customer = TestUtil.toJava(json, null).asClass(Customer.class);
        String json2 = TestUtil.toJson(customer);
        Customer customer2 = TestUtil.toJava(json2, null).asClass(Customer.class);
        // Test Java graphs are equivalent
        assert DeepEquals.deepEquals(customer, customer2);

        Object tree1 = JsonIo.toJava(json, null).asClass(null);
        Object tree2 = JsonIo.toJava(json2, null).asClass(null);

        json = JsonIo.toJson(tree1, null);
        json2 = JsonIo.toJson(tree2, null);
        // Test JSON strigns are equivalent
        assert json.equals(json2);

        // Booger up one field deep in the POJO hierarchy
        customer.getLocations()[0].getBuildings()[0].getAssets()[0].setValue(1.1d);
        assert !DeepEquals.deepEquals(customer, customer2);  // no longer equal
    }
}
