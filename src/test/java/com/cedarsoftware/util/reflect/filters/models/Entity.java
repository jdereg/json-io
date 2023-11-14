package com.cedarsoftware.util.reflect.filters.models;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class Entity {
    Long id;
    Date updated;
    Date created;
}
