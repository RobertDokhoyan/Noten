package com.example.noten;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

public class MusicRecorderFragment extends Fragment {

    private static final String TAG = "MusicRecorderFragment";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int SAMPLE_RATE = 44100;
    private static final int AUDIO_BUFFER_SIZE = 8192;

    private boolean isRecording = false;
    private Handler handler = new Handler();
    private TextView noteDisplay;
    private Button startButton, stopButton;
    private AudioRecord audioRecord;
    private WebView webView;  // WebView для отображения нотного стана с VexFlow

    private double smoothedPitch = -1;
    private Spinner keySpinner, meterSpinner;
    private String selectedKey = "C";    // Значение по умолчанию
    private String selectedMeter = "4/4";  // Значение по умолчанию

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_music_recorder, container, false);

        noteDisplay = rootView.findViewById(R.id.noteDisplay);
        startButton = rootView.findViewById(R.id.startButton);
        stopButton = rootView.findViewById(R.id.stopButton);
        keySpinner = rootView.findViewById(R.id.keySpinner);
        meterSpinner = rootView.findViewById(R.id.chordSpinner);
        webView = rootView.findViewById(R.id.webView);

        // Настройка WebView
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        // Устанавливаем WebChromeClient для отладки и корректного отображения
        webView.setWebChromeClient(new WebChromeClient());
        // Загружаем локальный HTML-файл с VexFlow
        webView.loadUrl("file:///android_asset/music_display.html");

        startButton.setEnabled(false);

        // Настройка адаптера для спиннера тональности
        ArrayAdapter<CharSequence> keyAdapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.keys, android.R.layout.simple_spinner_item);
        keyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        keySpinner.setAdapter(keyAdapter);

        // Настройка адаптера для спиннера такта
        ArrayAdapter<CharSequence> meterAdapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.meters, android.R.layout.simple_spinner_item);
        meterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        meterSpinner.setAdapter(meterAdapter);

        // Обработчик выбора тональности
        keySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedKey = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "Selected key: " + selectedKey);
                updateDisplay();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        // Обработчик выбора такта
        meterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMeter = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "Selected meter: " + selectedMeter);
                startButton.setEnabled(true);
                updateDisplay();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        startButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                startRecording();
            } else {
                requestPermissions();
            }
        });

        stopButton.setOnClickListener(v -> stopRecording());

        return rootView;
    }

    // Вызывает JavaScript-функцию updateDisplay с параметрами тональности и такта
    private void updateDisplay() {
        String jsCommand = "javascript:updateDisplay('" + selectedKey + "', '" + selectedMeter + "')";
        Log.d(TAG, "JS command: " + jsCommand);
        webView.evaluateJavascript(jsCommand, null);
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(getActivity(), "Permission denied. Cannot record audio.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startRecording() {
        try {
            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize = Math.max(minBufferSize, AUDIO_BUFFER_SIZE);

            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording();
                isRecording = true;
                smoothedPitch = -1;
                handler.post(processAudio);
                Log.d(TAG, "Recording started");
            } else {
                Log.e(TAG, "Failed to initialize AudioRecord");
                Toast.makeText(getActivity(), "Failed to initialize AudioRecord", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in startRecording: " + e.getMessage());
            Toast.makeText(getActivity(), "Error starting recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (isRecording && audioRecord != null) {
            isRecording = false;
            try {
                audioRecord.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping AudioRecord: " + e.getMessage());
            }
            audioRecord.release();
            Log.d(TAG, "Recording stopped");
        }
    }

    private final Runnable processAudio = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                double[] audioData = captureAudio();
                if (audioData != null && audioData.length > 0) {
                    double detectedPitch = analyzePitch(audioData);
                    Log.d(TAG, "Raw detected pitch: " + detectedPitch + " Hz");

                    if (detectedPitch != -1) {
                        if (smoothedPitch == -1) {
                            smoothedPitch = detectedPitch;
                        } else {
                            smoothedPitch = 0.2 * detectedPitch + 0.8 * smoothedPitch;
                        }
                    } else {
                        smoothedPitch = -1;
                    }

                    String detectedNote = (smoothedPitch != -1) ? mapFrequencyToNote(smoothedPitch) : "No note detected";
                    if (!noteDisplay.getText().toString().equals(detectedNote)) {
                        noteDisplay.setText(detectedNote);
                    }
                }
                handler.postDelayed(this, 200);
            }
        }
    };

    private double[] captureAudio() {
        short[] buffer = new short[AUDIO_BUFFER_SIZE];
        double[] audioData = new double[AUDIO_BUFFER_SIZE];
        int shortsRead = audioRecord.read(buffer, 0, AUDIO_BUFFER_SIZE);
        if (shortsRead > 0) {
            for (int i = 0; i < shortsRead; i++) {
                audioData[i] = buffer[i] / 32768.0;
            }
            return audioData;
        } else {
            Log.e(TAG, "AudioRecord.read() returned " + shortsRead);
            return new double[0];
        }
    }

    private double analyzePitch(double[] audioData) {
        YINPitchDetector yinDetector = new YINPitchDetector();
        return yinDetector.getPitch(audioData, SAMPLE_RATE);
    }

    // Преобразует частоту в название ноты (диапазон C1–C8)
    private String mapFrequencyToNote(double frequency) {
        double[] noteFrequencies = {
                32.70, 34.65, 36.71, 38.89, 41.20, 43.65, 46.25, 49.00, 51.91, 55.00, 58.27, 61.74,
                65.41, 69.30, 73.42, 77.78, 82.41, 87.31, 92.50, 98.00, 103.83, 110.00, 116.54, 123.47,
                130.81, 138.59, 146.83, 155.56, 164.81, 174.61, 185.00, 196.00, 207.65, 220.00, 233.08, 246.94,
                261.63, 277.18, 293.66, 311.13, 329.63, 349.23, 369.99, 392.00, 415.30, 440.00, 466.16, 493.88,
                523.25, 554.37, 587.33, 622.25, 659.26, 698.46, 739.99, 783.99, 830.61, 880.00, 932.33, 987.77,
                1046.50, 1108.73, 1174.66, 1244.51, 1318.51, 1396.91, 1479.98, 1567.98, 1661.22, 1760.00, 1864.66, 1975.53,
                2093.00, 2217.46, 2349.32, 2489.02, 2637.02, 2793.83, 2959.96, 3135.96, 3322.44, 3520.00, 3729.31, 3951.07,
                4186.01
        };

        String[] noteNames = {
                "C1", "C#1", "D1", "D#1", "E1", "F1", "F#1", "G1", "G#1", "A1", "A#1", "B1",
                "C2", "C#2", "D2", "D#2", "E2", "F2", "F#2", "G2", "G#2", "A2", "A#2", "B2",
                "C3", "C#3", "D3", "D#3", "E3", "F3", "F#3", "G3", "G#3", "A3", "A#3", "B3",
                "C4", "C#4", "D4", "D#4", "E4", "F4", "F#4", "G4", "G#4", "A4", "A#4", "B4",
                "C5", "C#5", "D5", "D#5", "E5", "F5", "F#5", "G5", "G#5", "A5", "A#5", "B5",
                "C6", "C#6", "D6", "D#6", "E6", "F6", "F#6", "G6", "G#6", "A6", "A#6", "B6",
                "C7", "C#7", "D7", "D#7", "E7", "F7", "F#7", "G7", "G#7", "A7", "A#7", "B7",
                "C8"
        };

        int closestIndex = 0;
        double minDiff = Double.MAX_VALUE;
        for (int i = 0; i < noteFrequencies.length; i++) {
            double diff = Math.abs(frequency - noteFrequencies[i]);
            if (diff < minDiff) {
                minDiff = diff;
                closestIndex = i;
            }
        }
        return noteNames[closestIndex];
    }

    public static class YINPitchDetector {
        private static final double THRESHOLD = 0.15;

        public double getPitch(double[] buffer, int sampleRate) {
            int bufferSize = buffer.length;
            int tauMax = bufferSize / 2;
            double[] difference = new double[tauMax];

            for (int tau = 0; tau < tauMax; tau++) {
                double sum = 0;
                for (int j = 0; j < bufferSize - tau; j++) {
                    double delta = buffer[j] - buffer[j + tau];
                    sum += delta * delta;
                }
                difference[tau] = sum;
            }

            double[] cmnd = new double[tauMax];
            cmnd[0] = 1;
            double runningSum = 0;
            for (int tau = 1; tau < tauMax; tau++) {
                runningSum += difference[tau];
                cmnd[tau] = difference[tau] * tau / runningSum;
            }

            int tauEstimate = -1;
            for (int tau = 1; tau < tauMax; tau++) {
                if (cmnd[tau] < THRESHOLD) {
                    while (tau + 1 < tauMax && cmnd[tau + 1] < cmnd[tau]) {
                        tau++;
                    }
                    tauEstimate = tau;
                    break;
                }
            }

            if (tauEstimate == -1) {
                return -1;
            }

            int tau0 = (tauEstimate <= 1) ? tauEstimate : tauEstimate - 1;
            int tau2 = (tauEstimate + 1 < tauMax) ? tauEstimate + 1 : tauEstimate;
            double s0 = cmnd[tau0];
            double s1 = cmnd[tauEstimate];
            double s2 = cmnd[tau2];
            double betterTau = tauEstimate;
            double denominator = (2 * s1 - s2 - s0);
            if (denominator != 0) {
                betterTau = tauEstimate + (s2 - s0) / (2 * denominator);
            }
            return sampleRate / betterTau;
        }
    }
}
