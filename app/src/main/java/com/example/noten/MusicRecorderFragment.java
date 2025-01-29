package com.example.noten;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MusicRecorderFragment extends Fragment {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 2048;

    private Button startButton;
    private Button stopButton;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Interpreter interpreter;
    private Thread recordingThread;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_music_recorder, container, false);

        startButton = view.findViewById(R.id.startButton);
        stopButton = view.findViewById(R.id.stopButton);

        startButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                startRecording();
            } else {
                requestPermissions();
            }
        });

        stopButton.setOnClickListener(v -> stopRecording());

        return view;
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Разрешение предоставлено", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Разрешение отклонено", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void startRecording() {
        if (!checkPermissions()) {
            Toast.makeText(requireContext(), "Разрешение не предоставлено", Toast.LENGTH_SHORT).show();
            return;
        }

        // Загрузка модели TensorFlow Lite
        try {
            interpreter = new Interpreter(loadModelFile());
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Ошибка при загрузке модели", Toast.LENGTH_SHORT).show();
            return;
        }

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE);

        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            audioRecord.startRecording();
            isRecording = true;

            recordingThread = new Thread(() -> {
                byte[] audioBuffer = new byte[BUFFER_SIZE];
                while (isRecording) {
                    int read = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                    if (read > 0) {
                        processAudioData(audioBuffer);
                    }
                }
            });
            recordingThread.start();

            Toast.makeText(requireContext(), "Запись началась", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "Ошибка инициализации записи", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (isRecording && audioRecord != null) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            if (recordingThread != null) {
                recordingThread.interrupt();
                recordingThread = null;
            }
            Toast.makeText(requireContext(), "Запись остановлена", Toast.LENGTH_SHORT).show();
        }
    }

    private void processAudioData(byte[] audioData) {
        // Преобразуем байты в данные с плавающей точкой (нормализуем)
        FloatBuffer floatBuffer = ByteBuffer.wrap(audioData)
                .asFloatBuffer();

        // Подготовим входные данные для модели
        float[] inputData = new float[BUFFER_SIZE];
        for (int i = 0; i < audioData.length; i++) {
            inputData[i] = floatBuffer.get(i) / 32768.0f;  // Нормализация
        }

        // Применяем модель
        float[][] output = new float[1][88];  // Размер для 88 нот (например, от C0 до C8)

        interpreter.run(inputData, output);

        // Получаем наиболее вероятную ноту
        int predictedNoteIndex = argMax(output[0]);
        String note = indexToNote(predictedNoteIndex);

        handler.post(() -> Toast.makeText(requireContext(), "Detected Note: " + note, Toast.LENGTH_SHORT).show());
    }

    private String indexToNote(int index) {
        String[] notes = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int octave = index / 12;
        int noteIndex = index % 12;
        return notes[noteIndex] + octave;
    }

    private int argMax(float[] array) {
        int maxIndex = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[maxIndex]) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    // Загрузка модели TensorFlow Lite
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getContext().getAssets().openFd("model.tflite");
        FileInputStream inputStream = fileDescriptor.createInputStream();
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
