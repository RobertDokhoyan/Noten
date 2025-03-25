package com.example.noten;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class MusicRecorderFragment extends Fragment {

    private static final String TAG = "MusicRecorderFragment";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int SAMPLE_RATE = 44100;
    // Размер буфера для быстрого отклика
    private static final int AUDIO_BUFFER_SIZE = 4096;
    // Задержка между итерациями – для быстрой игры установлена в 20 мс
    private static final int PROCESS_DELAY_MS = 20;
    // Минимальное время для фиксации изменения ноты (в мс)
    private static final int MIN_NOTE_DURATION_MS = 100;
    // Допустимая относительная погрешность (5%) для определения, что нота не изменилась
    private static final double FREQUENCY_TOLERANCE = 0.05;

    private boolean isRecording = false;
    private Handler handler = new Handler();
    private TextView noteDisplay;
    private Button startButton, stopButton;
    private AudioRecord audioRecord;
    private WebView webView; // WebView для отображения нот через VexFlow

    // Настройки тональности и такта
    private Spinner keySpinner, meterSpinner;
    private String selectedKey = "C";
    private String selectedMeter = "4/4";

    // Переменные для определения длительности ноты
    private String lastRecordedNote = "";
    private long lastNoteStartTime = 0;
    // Флаг, фиксирующий окончание ноты
    private boolean noteEnded = false;
    // Последняя зафиксированная частота для сравнения
    private double lastDetectedFreq = -1;

    // Буфер для сглаживания (медианный фильтр) – размер 5 измерений
    private static final int PITCH_BUFFER_SIZE = 5;
    private double[] pitchBuffer = new double[PITCH_BUFFER_SIZE];
    private int pitchBufferIndex = 0;
    private boolean bufferFilled = false;

    // Основной метод анализа аудио – использует оптимизированный алгоритм YIN
    private double analyzePitch(double[] audioData) {
        return OptimizedYIN.getPitch(audioData, SAMPLE_RATE);
    }

    // Метод сглаживания – возвращает медианное значение из буфера
    private double getSmoothedPitch(double[] audioData) {
        double currentPitch = analyzePitch(audioData);
        if (currentPitch <= 0) {
            return -1;
        }
        pitchBuffer[pitchBufferIndex] = currentPitch;
        pitchBufferIndex = (pitchBufferIndex + 1) % PITCH_BUFFER_SIZE;
        if (pitchBufferIndex == 0) {
            bufferFilled = true;
        }
        if (!bufferFilled) {
            return currentPitch;
        }
        double[] copy = Arrays.copyOf(pitchBuffer, pitchBuffer.length);
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    // Преобразование частоты в название ноты
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

    // Захват аудио с микрофона
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

    // Метод для сопоставления длительности (в мс) с обозначением длительности ноты для VexFlow.
    private String getDurationSymbol(long durationMs) {
        if (durationMs >= 2000) {
            return "w";
        } else if (durationMs >= 1000) {
            return "q";
        } else if (durationMs >= 500) {
            return "8";
        } else {
            return "16";
        }
    }

    // Основной цикл обработки аудио
    private final Runnable processAudio = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                double[] audioData = captureAudio();
                // Вычисляем RMS амплитуду
                double rms = 0;
                for (double sample : audioData) {
                    rms += sample * sample;
                }
                rms = Math.sqrt(rms / audioData.length);
                long currentTime = System.currentTimeMillis();

                // Если звук слишком тихий – фиксируем длительность предыдущей ноты и сбрасываем состояние
                if (rms < 0.05) {
                    if (!lastRecordedNote.isEmpty()) {
                        long durationMs = currentTime - lastNoteStartTime;
                        String durationSymbol = getDurationSymbol(durationMs);
                        String jsCommand = "javascript:updateDisplayWithDuration('"
                                + selectedKey + "', '" + selectedMeter + "', '"
                                + lastRecordedNote + "', '" + durationSymbol + "')";
                        webView.evaluateJavascript(jsCommand, null);
                        lastRecordedNote = "";
                        lastDetectedFreq = -1;
                    }
                    handler.postDelayed(this, PROCESS_DELAY_MS);
                    return;
                }

                // Получаем сглаженное значение частоты
                double detectedFreq = getSmoothedPitch(audioData);
                Log.d(TAG, "Detected frequency: " + detectedFreq + " Hz, RMS: " + rms);
                if (detectedFreq != -1) {
                    // Если предыдущая частота не установлена, сохраняем текущее значение
                    if (lastDetectedFreq < 0) {
                        lastDetectedFreq = detectedFreq;
                    }
                    // Вычисляем относительную разницу между текущей и предыдущей частотой
                    double relativeDiff = Math.abs(detectedFreq - lastDetectedFreq) / lastDetectedFreq;

                    String detectedNote = mapFrequencyToNote(detectedFreq);

                    // Если разница превышает порог и прошло минимум MIN_NOTE_DURATION_MS, фиксируем предыдущую ноту
                    if (relativeDiff > FREQUENCY_TOLERANCE && (currentTime - lastNoteStartTime) >= MIN_NOTE_DURATION_MS) {
                        if (!lastRecordedNote.isEmpty()) {
                            long durationMs = currentTime - lastNoteStartTime;
                            String durationSymbol = getDurationSymbol(durationMs);
                            String jsCommand = "javascript:updateDisplayWithDuration('"
                                    + selectedKey + "', '" + selectedMeter + "', '"
                                    + lastRecordedNote + "', '" + durationSymbol + "')";
                            webView.evaluateJavascript(jsCommand, null);
                        }
                        lastRecordedNote = detectedNote;
                        lastNoteStartTime = currentTime;
                        lastDetectedFreq = detectedFreq;
                    } else {
                        // Если та же нота, обновляем длительность без создания нового события
                        // (можно реализовать дополнительную логику для длительных нот, если потребуется)
                    }
                }
                handler.postDelayed(this, PROCESS_DELAY_MS);
            }
        }
    };

    // Запуск записи аудио
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
                lastRecordedNote = "";
                lastNoteStartTime = System.currentTimeMillis();
                lastDetectedFreq = -1;
                pitchBufferIndex = 0;
                bufferFilled = false;
                handler.post(processAudio);
                Log.d(TAG, "Recording started");
                Toast.makeText(getActivity(), "Recording started", Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "AudioRecord initialization error");
                Toast.makeText(getActivity(), "Error initializing recording", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage());
            Toast.makeText(getActivity(), "Error starting recording", Toast.LENGTH_SHORT).show();
        }
    }

    // Остановка записи аудио. Если нота всё ещё звучала – фиксируем её длительность.
    private void stopRecording() {
        if (isRecording && audioRecord != null) {
            isRecording = false;
            long currentTime = System.currentTimeMillis();
            if (!lastRecordedNote.isEmpty()) {
                long durationMs = currentTime - lastNoteStartTime;
                String durationSymbol = getDurationSymbol(durationMs);
                String jsCommand = "javascript:updateDisplayWithDuration('"
                        + selectedKey + "', '" + selectedMeter + "', '"
                        + lastRecordedNote + "', '" + durationSymbol + "')";
                webView.evaluateJavascript(jsCommand, null);
                lastRecordedNote = "";
                lastDetectedFreq = -1;
            }
            try {
                audioRecord.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping recording: " + e.getMessage());
            }
            audioRecord.release();
            Log.d(TAG, "Recording stopped");
            showSaveDialog();
        }
    }

    // Диалог "Save?" с вводом имени проекта.
    private void showSaveDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Save Project?");
        builder.setMessage("Do you want to save the recorded notes?");
        final EditText input = new EditText(getActivity());
        input.setHint("Enter project name");
        builder.setView(input);
        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String projectName = input.getText().toString().trim();
                if (projectName.isEmpty()) {
                    projectName = "Project_" + System.currentTimeMillis();
                }
                saveProject(projectName);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                webView.evaluateJavascript("javascript:resetStaff('" + selectedKey + "', '" + selectedMeter + "')", null);
            }
        });
        builder.show();
    }

    // Генерация PDF и сохранение изображения, затем создание записи проекта.
    private void saveProject(String projectName) {
        Bitmap bitmap = captureWebViewBitmap();
        if (bitmap == null) {
            Toast.makeText(getActivity(), "Error capturing notes", Toast.LENGTH_SHORT).show();
            return;
        }
        String pdfFileName = "notes_" + System.currentTimeMillis() + ".pdf";
        File pdfFile = savePdfFromBitmap(bitmap, pdfFileName);
        if (pdfFile == null) {
            Toast.makeText(getActivity(), "Error saving PDF", Toast.LENGTH_SHORT).show();
            return;
        }
        String imageFileName = "notes_image_" + System.currentTimeMillis() + ".png";
        File imageFile = saveBitmapToFile(bitmap, imageFileName);
        if (imageFile == null) {
            Toast.makeText(getActivity(), "Error saving image", Toast.LENGTH_SHORT).show();
            return;
        }
        Project project = new Project(projectName, pdfFile.getAbsolutePath(), imageFile.getAbsolutePath());
        ProjectManager.addProject(project);
        Toast.makeText(getActivity(), "Project saved", Toast.LENGTH_SHORT).show();
    }

    // Захват Bitmap из WebView
    private Bitmap captureWebViewBitmap() {
        try {
            Bitmap bitmap = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            webView.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error capturing WebView bitmap: " + e.getMessage());
            return null;
        }
    }

    // Генерация PDF из Bitmap
    private File savePdfFromBitmap(Bitmap bitmap, String fileName) {
        PdfDocument pdfDocument = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(bitmap.getWidth(), bitmap.getHeight(), 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas pdfCanvas = page.getCanvas();
        pdfCanvas.drawBitmap(bitmap, 0, 0, null);
        pdfDocument.finishPage(page);
        File pdfDir = getActivity().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (pdfDir != null && !pdfDir.exists()) {
            pdfDir.mkdirs();
        }
        File pdfFile = new File(pdfDir, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(pdfFile);
            pdfDocument.writeTo(fos);
            fos.close();
            return pdfFile;
        } catch (IOException e) {
            Log.e(TAG, "Error writing PDF file: " + e.getMessage());
            return null;
        } finally {
            pdfDocument.close();
        }
    }

    // Сохранение Bitmap в PNG-файл
    private File saveBitmapToFile(Bitmap bitmap, String fileName) {
        File imageDir = getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (imageDir != null && !imageDir.exists()) {
            imageDir.mkdirs();
        }
        File imageFile = new File(imageDir, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(imageFile);
            if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                fos.close();
                return imageFile;
            } else {
                fos.close();
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing image file: " + e.getMessage());
            return null;
        }
    }

    // Проверка разрешения на запись аудио
    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Запрос разрешений
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
                Toast.makeText(getActivity(), "No permission to record audio", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Основной метод onCreateView для этого фрагмента
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_music_recorder, container, false);
        startButton = rootView.findViewById(R.id.startButton);
        stopButton = rootView.findViewById(R.id.stopButton);
        keySpinner = rootView.findViewById(R.id.keySpinner);
        meterSpinner = rootView.findViewById(R.id.chordSpinner);
        webView = rootView.findViewById(R.id.webView);

        // Принудительно используем программный рендеринг для WebView
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "HTML page loaded: " + url);
                String initCommand = "javascript:initStave('" + selectedKey + "', '" + selectedMeter + "')";
                webView.evaluateJavascript(initCommand, null);
            }
        });
        webView.loadUrl("file:///android_asset/music_display.html");

        ArrayAdapter<CharSequence> keyAdapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.keys, android.R.layout.simple_spinner_item);
        keyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        keySpinner.setAdapter(keyAdapter);
        keySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedKey = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "Selected key: " + selectedKey);
                webView.evaluateJavascript("javascript:updateDisplayWithDuration('" + selectedKey + "', '" + selectedMeter + "', '', '')", null);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        ArrayAdapter<CharSequence> meterAdapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.meters, android.R.layout.simple_spinner_item);
        meterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        meterSpinner.setAdapter(meterAdapter);
        meterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMeter = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "Selected meter: " + selectedMeter);
                webView.evaluateJavascript("javascript:updateDisplayWithDuration('" + selectedKey + "', '" + selectedMeter + "', '', '')", null);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        startButton.setOnClickListener(v -> {
            new AlertDialog.Builder(getActivity())
                    .setTitle("Start Recording")
                    .setMessage("Do you really want to start recording?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        webView.evaluateJavascript("javascript:resetStaff('" + selectedKey + "', '" + selectedMeter + "')", null);
                        if (checkPermissions()) {
                            startRecording();
                        } else {
                            requestPermissions();
                        }
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        stopButton.setOnClickListener(v -> stopRecording());

        return rootView;
    }

    // Оптимизированный алгоритм YIN для определения частоты
    public static class OptimizedYIN {
        private static final double THRESHOLD = 0.12;
        // Ограничения tau для диапазона частот пианино
        private static int tauMin(int sampleRate) {
            return Math.max(2, sampleRate / 4200);  // примерно 10 при 44100 Гц
        }
        private static int tauMax(int sampleRate, int bufferSize) {
            int max = sampleRate / 25;  // примерно 1764 при 44100 Гц
            return Math.min(bufferSize / 2, max);
        }

        public static double getPitch(double[] buffer, int sampleRate) {
            int bufferSize = buffer.length;
            int tauMin = tauMin(sampleRate);
            int tauMax = tauMax(sampleRate, bufferSize);
            double[] difference = new double[tauMax + 1];
            double[] cmnd = new double[tauMax + 1];

            // Вычисляем разности
            for (int tau = tauMin; tau <= tauMax; tau++) {
                double sum = 0;
                for (int j = 0; j < bufferSize - tau; j++) {
                    double delta = buffer[j] - buffer[j + tau];
                    sum += delta * delta;
                }
                difference[tau] = sum;
            }

            cmnd[0] = 1;
            double runningSum = 0;
            for (int tau = tauMin; tau <= tauMax; tau++) {
                runningSum += difference[tau];
                cmnd[tau] = difference[tau] * tau / runningSum;
            }

            int tauEstimate = -1;
            for (int tau = tauMin; tau <= tauMax; tau++) {
                if (cmnd[tau] < THRESHOLD) {
                    while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) {
                        tau++;
                    }
                    tauEstimate = tau;
                    break;
                }
            }
            if (tauEstimate == -1) {
                return -1;
            }
            int tau0 = Math.max(tauMin, tauEstimate - 1);
            int tau2 = Math.min(tauMax, tauEstimate + 1);
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
