package com.simpleutils.quik.requests;

import com.simpleutils.quik.ClassSecCode;

import java.util.Collection;

/**
 * Подписка на получение параметров из таблицы текущих торгов для указанных кода класса и кода инструмента.
 */
public class ParamSubscriptionRequest implements QuikRequest {

    private final String classCode;
    private final String secCode;
    private final Collection<String> parameters;

    public ParamSubscriptionRequest(final String classCode, final String secCode, final Collection<String> parameters) {
        this.classCode = classCode;
        this.secCode = secCode;
        this.parameters = parameters;
    }

    public ParamSubscriptionRequest(final ClassSecCode classSecCode, final Collection<String> parameters) {
        this(classSecCode.classCode(), classSecCode.secCode(), parameters);
    }

    @Override
    public String getRequest() {
        final StringBuilder sb = new StringBuilder();
        sb.append("local b = false\n");
        String prefix = "b = b or ParamRequest(\"%s\", \"%s\", \"".formatted(classCode, secCode);
        for (final String param : parameters) {
            sb.append(prefix).append(param).append("\")\n");
        }
        prefix = "getParamEx(\"%s\", \"%s\", \"".formatted(classCode, secCode);
        for (final String param : parameters) {
            sb.append(prefix).append(param).append("\")\n");
        }
        sb.append("return b\n");
        return sb.toString();
    }
}
