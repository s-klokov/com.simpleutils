package com.simpleutils.quik.requests;

public class CandlesRequest implements QuikRequest {

    private final String classCode;
    private final String secCode;
    private final int interval;
    private final int maxSize;

    public CandlesRequest(final String classCode, final String secCode, final int interval, final int maxSize) {
        this.classCode = classCode;
        this.secCode = secCode;
        this.interval = interval;
        this.maxSize = maxSize;
    }

    @Override
    public String getRequest() {
        return "return getCandles(\"%s\", \"%s\", %d, %d)".formatted(classCode, secCode, interval, maxSize);
    }
}
