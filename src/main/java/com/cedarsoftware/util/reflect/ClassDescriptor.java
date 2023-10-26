package com.cedarsoftware.util.reflect;

import java.util.Map;

public interface ClassDescriptor {

    Map<String, Accessor> getAccessors();

    Map<String, Injector> getInjectors();
}
