package com.cedarsoftware.io.reflect.models;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SecurityGroup {
    private Long id;
    private String type;
    private String name;
    private Set<Permission> permissions;
}
