package com.cedarsoftware.util.reflect.models;

import java.util.Objects;

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

    @Override
    public int hashCode()
    {
        return id.intValue() * 333;
    }

    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }

        Permission other = (Permission) obj;
        if (!Objects.equals(id, other.id)) {
            return false;
        }
        if (!Objects.equals(code, other.code)) {
            return false;
        }
        return Objects.equals(description, other.description);
    }
}
