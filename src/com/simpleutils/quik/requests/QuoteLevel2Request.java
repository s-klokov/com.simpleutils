package com.simpleutils.quik.requests;

import com.simpleutils.quik.ClassSecCode;

/**
 * Запрос на получение данных стакана по указанным коду класса и коду инструмента.
 */
public class QuoteLevel2Request implements QuikRequest {

    private final String classCode;
    private final String secCode;

    public QuoteLevel2Request(final String classCode, final String secCode) {
        this.classCode = classCode;
        this.secCode = secCode;
    }

    public QuoteLevel2Request(final ClassSecCode classSecCode) {
        this(classSecCode.classCode(), classSecCode.secCode());
    }

    @Override
    public String getRequest() {
        return "return getQuoteLevel2(\"%s\", \"%s\")".formatted(classCode, secCode);
    }
}
