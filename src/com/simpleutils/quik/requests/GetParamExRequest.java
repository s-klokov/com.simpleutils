package com.simpleutils.quik.requests;

import com.simpleutils.quik.ClassSecCode;

import java.util.Collection;

/**
 * Получение параметров из таблицы текущих торгов для указанных кода класса и кода инструмента.
 */
public class GetParamExRequest implements QuikRequest {

    private final String classCode;
    private final String secCode;
    private final Collection<String> parameters;

    public GetParamExRequest(final String classCode, final String secCode, final Collection<String> parameters) {
        this.classCode = classCode;
        this.secCode = secCode;
        this.parameters = parameters;
    }

    public GetParamExRequest(final ClassSecCode classSecCode, final Collection<String> parameters) {
        this(classSecCode.classCode(), classSecCode.secCode(), parameters);
    }

    @Override
    public String getRequest() {
        final StringBuilder sb = new StringBuilder();
        sb.append("local t = {}\n");
        sb.append("local p\n");
        final String prefix = "p = getParamEx(\"%s\", \"%s\", \"".formatted(classCode, secCode);
        for (final String param : parameters) {
            sb.append(prefix).append(param).append("\")\n");
            sb.append("if type(p) == \"table\" and p.result == \"1\" then\n");
            sb.append("  t[\"").append(param).append("\"] = p.param_value\n");
            sb.append("end\n");
        }
        sb.append("return t");
        return sb.toString();
    }
}
