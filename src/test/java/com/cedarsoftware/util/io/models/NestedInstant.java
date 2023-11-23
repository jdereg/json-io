package com.cedarsoftware.util.io.models;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NestedInstant {
    private final Instant instant1;
    private final Instant instant2;
}
