package com.cedarsoftware.util.reflect.factories;

import com.cedarsoftware.util.reflect.AccessorFactory;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractAccessFactoryTest {

    protected AccessorFactory factory;

    @BeforeEach()
    public void beforeEach() {
        this.factory = provideAccessorFactory();
    }

    protected abstract AccessorFactory provideAccessorFactory();
}
