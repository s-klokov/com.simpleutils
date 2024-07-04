package com.simpleutils;

/**
 * Вычисление среднего и стандартного отклонения при экспоненциальном усреднении.
 */
public class ExponentialStatistics {

    /**
     * Коэффициент lambda = 2 / (N + 1), где N -- период экспоненциального усреднения.
     */
    private final double lambda;
    /**
     * Коэффициент 1 - lambda для более эффективных вычислений.
     */
    private final double q;
    /**
     * Среднее.
     */
    private double average;
    /**
     * Квадрат стандартного отклонения.
     */
    private double sigma2;

    /**
     * Конструктор.
     *
     * @param period период экспоненциального усреднения
     */
    public ExponentialStatistics(final int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period=" + period + "<1");
        }
        lambda = 2.0 / (period + 1);
        q = 1.0 - lambda;
        average = Double.NaN;
        sigma2 = Double.NaN;
    }

    /**
     * Добавить выборочное значение и пересчитать среднее и отклонение при экспоненциальном усреднении.
     *
     * @param value выборочное значение
     */
    public void add(final double value) {
        if (Double.isNaN(average)) {
            average = value;
            sigma2 = 0.0;
        } else {
            final double a = average;
            average = q * average + lambda * value;
            final double y = value - average;
            final double z = a - average;
            sigma2 = q * sigma2 + lambda * y * y + q * z * z;
        }
    }

    /**
     * @return среднее
     */
    public double average() {
        return average;
    }

    /**
     * @return квадрат стандартного отклонения
     */
    public double sigma2() {
        return sigma2;
    }

    /**
     * @return стандартное отклонение
     */
    public double sigma() {
        return Math.sqrt(sigma2);
    }
}
