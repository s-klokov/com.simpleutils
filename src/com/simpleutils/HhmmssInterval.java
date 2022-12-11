package com.simpleutils;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Реализация интервала времени.
 *
 * @param type       тип (открытый, замкнутый или полуинтервал)
 * @param hhmmssFrom время начала в формате hhmmss
 * @param hhmmssTill время окончания в формате hhmmss
 */
public record HhmmssInterval(HhmmssInterval.Type type, int hhmmssFrom, int hhmmssTill) {

    public enum Type {
        Open,
        LeftOpen,
        RightOpen,
        Closed,
    }

    public HhmmssInterval(final Type type, final int hhmmssFrom, final int hhmmssTill) {
        if (!isValid(hhmmssFrom)) {
            throw new IllegalArgumentException("Illegal hhmmssFrom=" + hhmmssFrom);
        }
        if (!isValid(hhmmssTill)) {
            throw new IllegalArgumentException("Illegal hhmmssTill=" + hhmmssTill);
        }
        this.type = Objects.requireNonNull(type);
        this.hhmmssFrom = hhmmssFrom;
        this.hhmmssTill = hhmmssTill;
    }

    public static HhmmssInterval of(final Type type, final int hhmmssFrom, final int hhmmssTill) {
        return new HhmmssInterval(type, hhmmssFrom, hhmmssTill);
    }

    public static HhmmssInterval open(final int hhmmssFrom, final int hhmmssTill) {
        return new HhmmssInterval(Type.Open, hhmmssFrom, hhmmssTill);
    }

    public static HhmmssInterval leftOpen(final int hhmmssFrom, final int hhmmssTill) {
        return new HhmmssInterval(Type.LeftOpen, hhmmssFrom, hhmmssTill);
    }

    public static HhmmssInterval rightOpen(final int hhmmssFrom, final int hhmmssTill) {
        return new HhmmssInterval(Type.RightOpen, hhmmssFrom, hhmmssTill);
    }

    public static HhmmssInterval closed(final int hhmmssFrom, final int hhmmssTill) {
        return new HhmmssInterval(Type.Closed, hhmmssFrom, hhmmssTill);
    }

    private static final Pattern PATTERN = Pattern.compile("([\\[(\\]])(\\d{6})\\s*[;,\\-]\\s*(\\d{6})([\\[)\\]])");

    /**
     * Получить интервал времени по его строковому представлению вида [123015-174459].<br>
     * Для замкнутого интервала используются квадратные скобки '[' и ']',
     * для открытого -- круглые скобки '(' и ')' или развёрнутые квадратные скобки ']' и '[',
     * для полуинтервалов для открытого конца используется круглая скобка или развёрнутая квадратная скобка.<br>
     * Моменты начала и конца разделяются одним из знаков: '-', ',' или ';'.
     *
     * @param s строковое представление
     * @return интервал времени
     */
    public static HhmmssInterval parse(final String s) {
        final Matcher matcher = PATTERN.matcher(s);
        final Type type;
        if (matcher.matches()) {
            final String leftBracket = matcher.group(1);
            final int hhmmssFrom = Integer.parseInt(matcher.group(2));
            final int hhmmssTill = Integer.parseInt(matcher.group(3));
            final String rightBracket = matcher.group(4);
            if ("[".equals(leftBracket)) {
                if ("]".equals(rightBracket)) {
                    type = Type.Closed;
                } else {
                    type = Type.RightOpen;
                }
            } else {
                if ("]".equals(rightBracket)) {
                    type = Type.LeftOpen;
                } else {
                    type = Type.Open;
                }
            }
            return new HhmmssInterval(type, hhmmssFrom, hhmmssTill);
        } else {
            throw new IllegalArgumentException(s);
        }
    }

    public boolean contains(final int hhmmss) {
        if (hhmmssFrom <= hhmmssTill) {
            return switch (type) {
                case Open -> hhmmssFrom < hhmmss && hhmmss < hhmmssTill;
                case LeftOpen -> hhmmssFrom < hhmmss && hhmmss <= hhmmssTill;
                case RightOpen -> hhmmssFrom <= hhmmss && hhmmss < hhmmssTill;
                case Closed -> hhmmssFrom <= hhmmss && hhmmss <= hhmmssTill;
            };
        } else {
            return switch (type) {
                case Open -> hhmmss > hhmmssFrom || hhmmss < hhmmssTill;
                case LeftOpen -> hhmmss > hhmmssFrom || hhmmss <= hhmmssTill;
                case RightOpen -> hhmmss >= hhmmssFrom || hhmmss < hhmmssTill;
                case Closed -> hhmmss >= hhmmssFrom || hhmmss <= hhmmssTill;
            };
        }
    }

    public boolean contains(final String hhmmss) {
        return contains(Integer.parseInt(hhmmss));
    }

    private static boolean isValid(final int hhmmss) {
        if (hhmmss < 0) {
            return false;
        }
        final int ss = hhmmss % 100;
        if (ss > 59) {
            return false;
        }
        final int mm = (hhmmss / 100) % 100;
        if (mm > 59) {
            return false;
        }
        final int hh = hhmmss / 10000;
        return hh <= 23;
    }
}
