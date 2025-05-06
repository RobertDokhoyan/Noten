package com.example.noten;

public class SuperpoweredPitchDetector {
    static {
        // Загрузка нативной библиотеки, которая содержит реализацию detectPitch
        System.loadLibrary("SuperpoweredPitchDetectorLib");
    }

    /**
     * Нативный метод, который принимает аудиоданные (float[]) и частоту дискретизации,
     * и возвращает определённую частоту (в Гц) или -1, если определение не удалось.
     */
    public native float detectPitch(float[] audioData, int sampleRate);
}
