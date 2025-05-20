package com.example.noten;

import android.Manifest;
import android.app.AlertDialog;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MusicRecorderFragment extends Fragment {

    private static final String TAG = "MusicRecorderFragment";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int SAMPLE_RATE = 44100;
    private static final int AUDIO_BUFFER_SIZE = 4096;
    private static final double RMS_THRESHOLD = 0.05;
    private static final long SILENCE_GRACE_PERIOD = 200;

    private boolean isRecording = false;
    private Handler handler = new Handler();
    private AudioRecord audioRecord;

    private WebView webView;
    private Spinner keySpinner, meterSpinner;
    private Button startButton, stopButton;

    private String selectedKey = "C";
    private String selectedMeter = "4/4";

    private String currentStableNote = null;
    private long noteStartTime = 0;
    private long lastNoteEndTime = 0;
    private long silenceStartTime = 0;

    private static final double[] NOTE_FREQUENCIES = {
            32.70,34.65,36.71,38.89,41.20,43.65,46.25,49.00,51.91,55.00,58.27,61.74,
            65.41,69.30,73.42,77.78,82.41,87.31,92.50,98.00,103.83,110.00,116.54,123.47,
            130.81,138.59,146.83,155.56,164.81,174.61,185.00,196.00,207.65,220.00,233.08,246.94,
            261.63,277.18,293.66,311.13,329.63,349.23,369.99,392.00,415.30,440.00,466.16,493.88,
            523.25,554.37,587.33,622.25,659.26,698.46,739.99,783.99,830.61,880.00,932.33,987.77,
            1046.50,1108.73,1174.66,1244.51,1318.51,1396.91,1479.98,1567.98,1661.22,1760.00,1864.66,1975.53,
            2093.00,2217.46,2349.32,2489.02,2637.02,2793.83,2959.96,3135.96,3322.44,3520.00,3729.31,3951.07,
            4186.01
    };

    private static final String[] NOTE_NAMES = {
            "C1","C#1","D1","D#1","E1","F1","F#1","G1","G#1","A1","A#1","B1",
            "C2","C#2","D2","D#2","E2","F2","F#2","G2","G#2","A2","A#2","B2",
            "C3","C#3","D3","D#3","E3","F3","F#3","G3","G#3","A3","A#3","B3",
            "C4","C#4","D4","D#4","E4","F4","F#4","G4","G#4","A4","A#4","B4",
            "C5","C#5","D5","D#5","E5","F5","F#5","G5","G#5","A5","A#5","B5",
            "C6","C#6","D6","D#6","E6","F6","F#6","G6","G#6","A6","A#6","B6",
            "C7","C#7","D7","D#7","E7","F7","F#7","G7","G#7","A7","A#7","B7",
            "C8"
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_music_recorder, container, false);

        webView      = root.findViewById(R.id.webView);
        startButton  = root.findViewById(R.id.startButton);
        stopButton   = root.findViewById(R.id.stopButton);
        keySpinner   = root.findViewById(R.id.keySpinner);
        meterSpinner = root.findViewById(R.id.chordSpinner);

        // WebView setup
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                webView.evaluateJavascript("initStave('" + selectedKey + "','" + selectedMeter + "');", null);
            }
        });
        webView.loadUrl("file:///android_asset/music_display.html");

        // Key spinner
        ArrayAdapter<CharSequence> ka = ArrayAdapter.createFromResource(
                requireContext(), R.array.keys, android.R.layout.simple_spinner_item
        );
        ka.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        keySpinner.setAdapter(ka);
        keySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedKey = p.getItemAtPosition(pos).toString();
                webView.evaluateJavascript("updateDisplayWithDuration('" + selectedKey + "','" + selectedMeter + "','','');", null);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Meter spinner
        ArrayAdapter<CharSequence> ma = ArrayAdapter.createFromResource(
                requireContext(), R.array.meters, android.R.layout.simple_spinner_item
        );
        ma.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        meterSpinner.setAdapter(ma);
        meterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedMeter = p.getItemAtPosition(pos).toString();
                webView.evaluateJavascript("updateDisplayWithDuration('" + selectedKey + "','" + selectedMeter + "','','');", null);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        startButton.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("Start Recording")
                .setMessage("Do you want to start recording?")
                .setPositiveButton("Yes", (d,w) -> {
                    webView.evaluateJavascript("resetStaff('" + selectedKey + "','" + selectedMeter + "')", null);
                    if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED) {
                        startRecording();
                    } else {
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
                    }
                })
                .setNegativeButton("No", null)
                .show()
        );

        stopButton.setOnClickListener(v -> stopRecording());

        return root;
    }

    private final Runnable processAudio = new Runnable() {
        @Override public void run() {
            if (!isRecording) return;
            double[] audioData = captureAudio();
            long now = System.currentTimeMillis();
            double rms = 0;
            for (double s : audioData) rms += s*s;
            rms = Math.sqrt(rms / audioData.length);

            if (rms < RMS_THRESHOLD) {
                if (currentStableNote != null) {
                    if (silenceStartTime == 0) silenceStartTime = now;
                    else if (now - silenceStartTime >= SILENCE_GRACE_PERIOD) {
                        long d = now - noteStartTime;
                        String sym = getDurationSymbol(d);
                        webView.evaluateJavascript(
                                "updateDisplayWithDuration('" + selectedKey + "','" + selectedMeter + "','" +
                                        currentStableNote + "','" + sym + "');", null
                        );
                        lastNoteEndTime = now;
                        currentStableNote = null;
                        silenceStartTime = 0;
                    }
                }
            } else {
                silenceStartTime = 0;
                double freq = new YINPitchDetector().getPitch(audioData, SAMPLE_RATE);
                if (freq > 0) {
                    String note = mapFrequencyToNote(freq);
                    double expected = getExpectedFrequencyForNote(note);
                    if (expected > 0 && Math.abs(freq - expected) > expected * 0.05) {
                        handler.postDelayed(this, 50);
                        return;
                    }
                    if (currentStableNote == null) {
                        currentStableNote = note;
                        noteStartTime = now;
                    } else if (!note.equals(currentStableNote)) {
                        long d = now - noteStartTime;
                        String sym = getDurationSymbol(d);
                        webView.evaluateJavascript(
                                "updateDisplayWithDuration('" + selectedKey + "','" + selectedMeter + "','" +
                                        currentStableNote + "','" + sym + "');", null
                        );
                        currentStableNote = note;
                        noteStartTime = now;
                    }
                }
            }
            handler.postDelayed(this, 50);
        }
    };

    private void startRecording() {
        int minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        );
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBuf, AUDIO_BUFFER_SIZE)
        );
        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            audioRecord.startRecording();
            isRecording = true;
            currentStableNote = null;
            silenceStartTime = 0;
            handler.post(processAudio);
            Toast.makeText(getContext(), "Recording started", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Audio init failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        long now = System.currentTimeMillis();
        if (currentStableNote != null) {
            long d = now - noteStartTime;
            String sym = getDurationSymbol(d);
            webView.evaluateJavascript(
                    "updateDisplayWithDuration('" + selectedKey + "','" + selectedMeter + "','" +
                            currentStableNote + "','" + sym + "');", null
            );
            currentStableNote = null;
        }
        audioRecord.stop();
        audioRecord.release();
        showSaveDialog();
    }

    private void showSaveDialog() {
        EditText et = new EditText(getContext());
        new AlertDialog.Builder(getContext())
                .setTitle("Save Project")
                .setMessage("Enter project name:")
                .setView(et)
                .setPositiveButton("Save", (d,w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) name = "Project_" + System.currentTimeMillis();
                    saveProject(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveProject(String projectName) {
        // Capture bitmap
        Bitmap bmp = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Config.ARGB_8888);
        new Canvas(bmp).drawBitmap(bmp, 0, 0, null);

        // Save PDF
        File pdf = savePdfFromBitmap(bmp, "notes_" + System.currentTimeMillis() + ".pdf");
        if (pdf == null) { Toast.makeText(getContext(), "PDF save error", Toast.LENGTH_SHORT).show(); return; }

        // Save PNG
        File img = saveBitmapToFile(bmp, "notes_img_" + System.currentTimeMillis() + ".png");
        if (img == null) { Toast.makeText(getContext(), "Image save error", Toast.LENGTH_SHORT).show(); return; }

        // Upload to Firebase
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { Toast.makeText(getContext(), "Not signed in", Toast.LENGTH_SHORT).show(); return; }
        String uid = user.getUid(), pid = String.valueOf(System.currentTimeMillis());
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference ref = storage.getReference().child("projects").child(uid).child(pid + ".png");

        byte[] data;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
            data = baos.toByteArray();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Compress error", Toast.LENGTH_SHORT).show();
            return;
        }

        StorageMetadata metadata = new StorageMetadata.Builder().setContentType("image/png").build();
        ref.putBytes(data, metadata)
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show())
                .addOnSuccessListener(ts -> ref.getDownloadUrl()
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "URL failed: " + e.getMessage(), Toast.LENGTH_LONG).show())
                        .addOnSuccessListener(uri -> {
                            Map<String, Object> meta = new HashMap<>();
                            meta.put("name", projectName);
                            meta.put("imageUrl", uri.toString());
                            meta.put("pdfPath", pdf.getAbsolutePath());
                            meta.put("timestamp", FieldValue.serverTimestamp());
                            FirebaseFirestore.getInstance()
                                    .collection("users").document(uid)
                                    .collection("projects").document(pid)
                                    .set(meta)
                                    .addOnSuccessListener(a -> Toast.makeText(getContext(), "Saved!", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Meta error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        })
                );
    }

    private File savePdfFromBitmap(Bitmap bmp, String fileName) {
        PdfDocument doc = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(bmp.getWidth(), bmp.getHeight(), 1).create();
        PdfDocument.Page page = doc.startPage(info);
        page.getCanvas().drawBitmap(bmp, 0, 0, null);
        doc.finishPage(page);
        File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir != null && !dir.exists()) dir.mkdirs();
        File f = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            doc.writeTo(fos);
            return f;
        } catch (IOException e) {
            Log.e(TAG, "PDF write error", e);
            return null;
        } finally {
            doc.close();
        }
    }

    private File saveBitmapToFile(Bitmap bmp, String fileName) {
        File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir != null && !dir.exists()) dir.mkdirs();
        File f = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            if (bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)) return f;
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Image write error", e);
            return null;
        }
    }

    private String getDurationSymbol(long ms) {
        if (ms >= 1500) return "w";
        if (ms >= 750)  return "q";
        if (ms >= 375)  return "8";
        return "16";
    }

    private double[] captureAudio() {
        short[] buf = new short[AUDIO_BUFFER_SIZE];
        double[] data = new double[AUDIO_BUFFER_SIZE];
        int read = audioRecord.read(buf, 0, AUDIO_BUFFER_SIZE);
        if (read > 0) {
            for (int i = 0; i < read; i++) data[i] = buf[i] / 32768.0;
        }
        return data;
    }

    private String mapFrequencyToNote(double freq) {
        int idx = 0; double md = Double.MAX_VALUE;
        for (int i = 0; i < NOTE_FREQUENCIES.length; i++) {
            double d = Math.abs(freq - NOTE_FREQUENCIES[i]);
            if (d < md) { md = d; idx = i; }
        }
        return NOTE_NAMES[idx];
    }

    private double getExpectedFrequencyForNote(String note) {
        for (int i = 0; i < NOTE_NAMES.length; i++) {
            if (NOTE_NAMES[i].equals(note)) return NOTE_FREQUENCIES[i];
        }
        return -1;
    }

    public static class YINPitchDetector {
        private static final double THRESHOLD = 0.12;
        public double getPitch(double[] buf, int sr) {
            int n = buf.length, m = n/2;
            double[] diff = new double[m], cmnd = new double[m];
            for (int t = 0; t < m; t++) {
                double s = 0;
                for (int j = 0; j < n - t; j++) {
                    double d = buf[j] - buf[j+t];
                    s += d*d;
                }
                diff[t] = s;
            }
            cmnd[0] = 1; double rs = 0;
            for (int t = 1; t < m; t++) {
                rs += diff[t];
                cmnd[t] = diff[t]*t/rs;
            }
            int est = -1;
            for (int t = 1; t < m; t++) {
                if (cmnd[t] < THRESHOLD) {
                    while (t+1 < m && cmnd[t+1] < cmnd[t]) t++;
                    est = t; break;
                }
            }
            if (est < 0) return -1;
            int t0 = est>1?est-1:est, t2 = est+1<m?est+1:est;
            double s0 = cmnd[t0], s1 = cmnd[est], s2 = cmnd[t2];
            double denom = 2*s1 - s2 - s0;
            double better = denom!=0? est + (s2 - s0)/(2*denom): est;
            return sr / better;
        }
    }
}
