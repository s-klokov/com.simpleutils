package com.simpleutils.quik;

import java.util.Objects;

public record ClassSecCode(String classCode, String secCode) {

    public static ClassSecCode of(final String classCode, final String secCode) {
        return new ClassSecCode(Objects.requireNonNull(classCode), Objects.requireNonNull(secCode));
    }

    @Override
    public String toString() {
        return classCode + ":" + secCode;
    }
}
