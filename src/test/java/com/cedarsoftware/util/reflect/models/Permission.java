package com.cedarsoftware.util.reflect.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Permission {
    private Long id;
    private String code;
    private String description;
}
