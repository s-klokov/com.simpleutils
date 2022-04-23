package com.simpleutils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Ограничитель интенсивности.
 */
public class Throttle {

    /**
     * Скорость увеличения общего ресурса, ед/сек.
     */
    private final double totalRate;
    /**
     * Граница, по достижению которой увеличение общего ресурса останавливается.
     */
    private final double totalBound;
    /**
     * Скорость увеличения индивидуального ресурса, ед/сек.
     */
    private final double individualRate;
    /**
     * Граница, по достижению которой увеличение индивидуального ресурса останавливается.
     */
    private final double individualBound;
    /**
     * Количество общего ресурса на текущий момент времени.
     */
    private double totalCount = 0;
    /**
     * Информация о количестве индивидуальных ресурсов.
     */
    private final Map<Long, Double> map = new HashMap<>();
    /**
     * Текущее время.
     */
    private long nanotime = System.nanoTime();

    /**
     * Конструктор.
     *
     * @param totalRate       скорость увеличения общего ресурса
     * @param totalBound      граница общего ресурса
     * @param individualRate  скорость увеличения индивидуального ресурса
     * @param individualBound граница индивидуального ресурса
     */
    public Throttle(final double totalRate,
                    final double totalBound,
                    final double individualRate,
                    final double individualBound) {
        this.totalRate = totalRate;
        this.totalBound = totalBound;
        this.individualRate = individualRate;
        this.individualBound = individualBound;
    }

    /**
     * Увеличить значения ресурсов со временем.
     * Этот метод должен вызываться достаточно часто.
     */
    public void increment() {
        final long now = System.nanoTime();
        final long duration = now - nanotime;
        if (duration <= 0) {
            return;
        }
        totalCount = Math.min(totalBound, totalCount + totalRate * duration / 1.0e9);
        for (final Iterator<Map.Entry<Long, Double>> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
            final Map.Entry<Long, Double> entry = iterator.next();
            final double oldIndividualCount = entry.getValue();
            final double newIndividualCount = Math.min(individualBound, oldIndividualCount + individualRate * duration / 1.0e9);
            if (newIndividualCount == individualBound) {
                iterator.remove();
            } else {
                entry.setValue(newIndividualCount);
            }
        }
        nanotime = now;
    }

    /**
     * Уменьшить значения индивидуального и общего ресурса.
     *
     * @param id идентификатор
     */
    public void decrement(final long id) {
        totalCount -= 1.0;
        Double individualCount = map.get(id);
        if (individualCount == null) {
            individualCount = individualBound;
        }
        map.put(id, individualCount - 1.0);
    }

    /**
     * Разрешено ли использование ресурса.
     *
     * @param id идентификатор
     * @return {@code true}, если ресурс есть, иначе {@code false}
     */
    public boolean isAllowed(final long id) {
        if (totalCount <= 0) {
            return false;
        }
        final Double individualCount = map.get(id);
        return individualCount == null || individualCount > 0;
    }
}
