package com.simpleutils.quik.requests;

import com.simpleutils.quik.ClassSecCode;

/**
 * Подписка на получение данных стакана для указанных кода класса и кода инструмента.
 */
public class QuoteLevel2SubscriptionRequest implements QuikRequest {

    private final String classCode;
    private final String secCode;

    public QuoteLevel2SubscriptionRequest(final String classCode, final String secCode) {
        this.classCode = classCode;
        this.secCode = secCode;
    }

    public QuoteLevel2SubscriptionRequest(final ClassSecCode classSecCode) {
        this(classSecCode.classCode(), classSecCode.secCode());
    }

    @Override
    public String getRequest() {
        return "return Subscribe_Level_II_Quotes(\"%s\", \"%s\")".formatted(classCode, secCode);
    }
}
