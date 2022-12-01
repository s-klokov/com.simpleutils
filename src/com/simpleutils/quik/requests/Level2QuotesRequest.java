package com.simpleutils.quik.requests;

import com.simpleutils.quik.ClassSecCode;

/**
 * Запрос на получение данных стакана по указанным коду класса и коду инструмента.
 */
public class Level2QuotesRequest implements QuikRequest {

    private final String classCode;
    private final String secCode;

    public Level2QuotesRequest(final String classCode, final String secCode) {
        this.classCode = classCode;
        this.secCode = secCode;
    }

    public Level2QuotesRequest(final ClassSecCode classSecCode) {
        this(classSecCode.classCode(), classSecCode.secCode());
    }

    @Override
    public String getRequest() {
        return "return getQuoteLevel2(\"%s\", \"%s\")".formatted(classCode, secCode);
    }
}
