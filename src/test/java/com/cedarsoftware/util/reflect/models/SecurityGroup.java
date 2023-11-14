package com.cedarsoftware.util.reflect.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

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
