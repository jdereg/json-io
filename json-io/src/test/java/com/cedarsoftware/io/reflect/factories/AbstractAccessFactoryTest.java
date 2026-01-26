package com.cedarsoftware.io.reflect.factories;

import com.cedarsoftware.io.reflect.AccessorFactory;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractAccessFactoryTest {

    protected AccessorFactory factory;

    @BeforeEach()
    public void beforeEach() {
        this.factory = provideAccessorFactory();
    }

    protected abstract AccessorFactory provideAccessorFactory();
}
