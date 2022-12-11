package com.simpleutils.quik.requests;

import com.simpleutils.quik.ClassSecCode;

import java.util.Set;

/**
 * Подписка на получение данных стаканов для набора инструментов.
 */
public class BulkQuoteLevel2SubscriptionRequest implements QuikRequest {

    private final Set<ClassSecCode> classSecCodes;

    public BulkQuoteLevel2SubscriptionRequest(final Set<ClassSecCode> classSecCodes) {
        this.classSecCodes = classSecCodes;
    }

    @Override
    public String getRequest() {
        final StringBuilder sb = new StringBuilder();
        sb.append("return {\n");
        int counter = 0;
        for (final ClassSecCode classSecCode : classSecCodes) {
            sb.append("  [").append(++counter).append("] ")
                    .append("= { classCode = \"").append(classSecCode.classCode())
                    .append("\", secCode = \"").append(classSecCode.secCode())
                    .append("\", subscribed = Subscribe_Level_II_Quotes(\"").append(classSecCode.classCode())
                    .append("\", \"").append(classSecCode.secCode()).append("\"), },\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
