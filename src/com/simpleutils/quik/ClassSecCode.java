package com.simpleutils.quik;

public record ClassSecCode(String classCode, String secCode) {

    public static ClassSecCode of(final String classCode, final String secCode) {
        return new ClassSecCode(classCode, secCode);
    }

    @Override
    public String toString() {
        return classCode + ":" + secCode;
    }
}
