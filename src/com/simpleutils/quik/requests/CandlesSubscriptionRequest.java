package com.simpleutils.quik.requests;

import java.util.Collection;

/**
 * Подписка на получение свечных данных для указанных кода класса и кода инструмента.
 */
public class CandlesSubscriptionRequest implements QuikRequest {

    private final String classCode;
    private final String secCode;
    private final Collection<Integer> intervals;

    public CandlesSubscriptionRequest(final String classCode, final String secCode, final Collection<Integer> intervals) {
        this.classCode = classCode;
        this.secCode = secCode;
        this.intervals = intervals;
    }

    @Override
    public String getRequest() {
        final StringBuilder sb = new StringBuilder();
        sb.append("local t = { classCode = \"%s\", secCode = \"%s\", }\n".formatted(classCode, secCode));
        for (final int interval : intervals) {
            sb.append("t[%d] = initDataSource(\"%s\", \"%s\", %d)\n".formatted(interval, classCode, secCode, interval));
        }
        sb.append("return t\n");
        return sb.toString();
    }
}
