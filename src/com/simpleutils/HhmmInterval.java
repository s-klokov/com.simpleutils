package com.simpleutils;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Реализация интервала времени.
 *
 * @param hhmmFrom время начала в формате hhmm
 * @param hhmmTill время окончания в формате hhmm
 * @param type     тип (открытый, замкнутый или полуинтервал)
 */
public record HhmmInterval(int hhmmFrom, int hhmmTill, HhmmInterval.Type type) {

    public enum Type {
        Open,
        LeftOpen,
        RightOpen,
        Closed,
    }

    public HhmmInterval(final int hhmmFrom, final int hhmmTill, final Type type) {
        if (!isValid(hhmmFrom)) {
            throw new IllegalArgumentException("Illegal hhmmFrom=" + hhmmFrom);
        }
        if (!isValid(hhmmTill)) {
            throw new IllegalArgumentException("Illegal hhmmTill=" + hhmmTill);
        }
        this.type = Objects.requireNonNull(type);
        this.hhmmFrom = hhmmFrom;
        this.hhmmTill = hhmmTill;
    }

    public static HhmmInterval of(final int hhmmFrom, final int hhmmTill, final Type type) {
        return new HhmmInterval(hhmmFrom, hhmmTill, type);
    }

    public static HhmmInterval open(final int hhmmFrom, final int hhmmTill) {
        return new HhmmInterval(hhmmFrom, hhmmTill, Type.Open);
    }

    public static HhmmInterval leftOpen(final int hhmmFrom, final int hhmmTill) {
        return new HhmmInterval(hhmmFrom, hhmmTill, Type.LeftOpen);
    }

    public static HhmmInterval rightOpen(final int hhmmFrom, final int hhmmTill) {
        return new HhmmInterval(hhmmFrom, hhmmTill, Type.RightOpen);
    }

    public static HhmmInterval closed(final int hhmmFrom, final int hhmmTill) {
        return new HhmmInterval(hhmmFrom, hhmmTill, Type.Closed);
    }

    private static final Pattern PATTERN = Pattern.compile("([\\[(\\]])(\\d{4})\\s*[;,\\-]\\s*(\\d{4})([\\[)\\]])");

    /**
     * Получить интервал времени по его строковому представлению вида [1230-1744].<br>
     * Для замкнутого интервала используются квадратные скобки '[' и ']',
     * для открытого -- круглые скобки '(' и ')' или развёрнутые квадратные скобки ']' и '[',
     * для полуинтервалов для открытого конца используется круглая скобка или развёрнутая квадратная скобка.<br>
     * Моменты начала и конца разделяются одним из знаков: '-', ',' или ';', по краям могут стоять пробелы.
     *
     * @param s строковое представление
     * @return интервал времени
     */
    public static HhmmInterval parse(final String s) {
        final Matcher matcher = PATTERN.matcher(s);
        final Type type;
        if (matcher.matches()) {
            final String leftBracket = matcher.group(1);
            final int hhmmFrom = Integer.parseInt(matcher.group(2));
            final int hhmmTill = Integer.parseInt(matcher.group(3));
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
            return new HhmmInterval(hhmmFrom, hhmmTill, type);
        } else {
            throw new IllegalArgumentException(s);
        }
    }

    public boolean contains(final int hhmm) {
        if (!isValid(hhmm)) {
            throw new IllegalArgumentException("Illegal hhmm=" + hhmm);
        }
        if (hhmmFrom <= hhmmTill) {
            return switch (type) {
                case Open -> hhmmFrom < hhmm && hhmm < hhmmTill;
                case LeftOpen -> hhmmFrom < hhmm && hhmm <= hhmmTill;
                case RightOpen -> hhmmFrom <= hhmm && hhmm < hhmmTill;
                case Closed -> hhmmFrom <= hhmm && hhmm <= hhmmTill;
            };
        } else {
            return switch (type) {
                case Open -> hhmm > hhmmFrom || hhmm < hhmmTill;
                case LeftOpen -> hhmm > hhmmFrom || hhmm <= hhmmTill;
                case RightOpen -> hhmm >= hhmmFrom || hhmm < hhmmTill;
                case Closed -> hhmm >= hhmmFrom || hhmm <= hhmmTill;
            };
        }
    }

    public boolean contains(final String hhmm) {
        return contains(Integer.parseInt(hhmm));
    }

    private static boolean isValid(final int hhmm) {
        if (hhmm < 0) {
            return false;
        }
        final int mm = hhmm % 100;
        if (mm > 59) {
            return false;
        }
        final int hh = hhmm / 100;
        return hh <= 23;
    }
}
