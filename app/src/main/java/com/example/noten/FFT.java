package com.example.noten;
public class FFT {
    private int n, m, bitmasks[];
    private double[] cos, sin, window;

    public FFT(int n) {
        this.n = n;
        this.m = (int) (Math.log(n) / Math.log(2));
        this.bitmasks = new int[n];
        this.cos = new double[n / 2];
        this.sin = new double[n / 2];
        this.window = new double[n];

        // Создание косинусов и синусов для FFT
        for (int i = 0; i < n / 2; i++) {
            cos[i] = Math.cos(-2 * Math.PI * i / n);
            sin[i] = Math.sin(-2 * Math.PI * i / n);
        }

        // Заполнение маски битов для перестановки
        int j = 0;
        for (int i = 0; i < n; i++) {
            bitmasks[i] = j;
            int mask = n;
            while (j >= (mask >>= 1)) {
                j -= mask;
            }
            j += mask;
        }
    }

    public void fft(double[] real, double[] imag) {
        // Перестановка
        for (int i = 0; i < n; i++) {
            int j = bitmasks[i];
            if (j > i) {
                double tempReal = real[i];
                double tempImag = imag[i];
                real[i] = real[j];
                imag[i] = imag[j];
                real[j] = tempReal;
                imag[j] = tempImag;
            }
        }

        // Основной цикл FFT
        for (int size = 2; size <= n; size <<= 1) {
            int halfSize = size >> 1;
            int tableStep = n / size;
            for (int i = 0; i < n; i += size) {
                for (int j = 0; j < halfSize; j++) {
                    int evenIndex = i + j;
                    int oddIndex = i + j + halfSize;
                    double cosValue = cos[tableStep * j];
                    double sinValue = sin[tableStep * j];
                    double tempReal = real[oddIndex] * cosValue - imag[oddIndex] * sinValue;
                    double tempImag = real[oddIndex] * sinValue + imag[oddIndex] * cosValue;
                    real[oddIndex] = real[evenIndex] - tempReal;
                    imag[oddIndex] = imag[evenIndex] - tempImag;
                    real[evenIndex] += tempReal;
                    imag[evenIndex] += tempImag;
                }
            }
        }
    }
}
