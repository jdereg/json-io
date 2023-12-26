package com.cedarsoftware.util.reflect.filters.models;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Entity {
    Long id;
    Date updated;
    Date created;
}
