package com.cedarsoftware.util.reflect.filters;

import com.cedarsoftware.util.io.MetaUtils;

public class ThrowableFilter extends IncludeFieldsFilter {

    public ThrowableFilter() {
        super(MetaUtils.setOf("detailMessage", "stackTrace", "cause"));
    }
}

