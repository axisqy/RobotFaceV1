package com.robot.face;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private SharedPreferences prefs;

    // File chooser
    private ValueCallback<Uri[]> uploadMessage;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1234;

    private static final int RECORD_AUDIO_PERMISSION_CODE = 1001;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );

        prefs = getSharedPreferences("robot_face_prefs", MODE_PRIVATE);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // === FILE PICKER SUPPORT ===
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                uploadMessage = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(Intent.createChooser(intent, "Select GGUF Model"), FILE_CHOOSER_REQUEST_CODE);
                return true;
            }
        });

        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);

        setupTextToSpeech();
        requestMicPermission();
    }

    // === HANDLE FILE PICKER RESULT ===
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (uploadMessage == null) return;

            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
            uploadMessage.onReceiveValue(results);
            uploadMessage = null;
        }
    }

    private void setupTextToSpeech() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                tts.setLanguage(Locale.getDefault());
            }
        });

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                runOnUiThread(() -> evalJs("window.onSpeechStart && window.onSpeechStart();"));
            }

            @Override
            public void onDone(String utteranceId) {
                runOnUiThread(() -> evalJs("window.onSpeechDone && window.onSpeechDone();"));
            }

            @Override
            @Deprecated
            public void onError(String utteranceId) {
                runOnUiThread(() -> evalJs("window.onSpeechDone && window.onSpeechDone();"));
            }

            @Override
            public void onRangeStart(String utteranceId, int start, int end, int frame) {
                runOnUiThread(() -> evalJs(
                        "window.onWordBoundary && window.onWordBoundary(" + start + "," + end + ");"));
            }
        });
    }

    private void requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_CODE);
        }
    }

    private void evalJs(String script) {
        if (webView != null) {
            webView.evaluateJavascript(script, null);
        }
    }

    private void nativeStartListening() {
        runOnUiThread(() -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestMicPermission();
                evalJs("window.onSpeechError && window.onSpeechError('no-permission');");
                return;
            }

            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                evalJs("window.onSpeechError && window.onSpeechError('unavailable');");
                return;
            }

            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    evalJs("window.onListenStart && window.onListenStart();");
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    evalJs("window.onListenLevel && window.onListenLevel(" + rmsdB + ");");
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                }

                @Override
                public void onError(int error) {
                    evalJs("window.onSpeechError && window.onSpeechError('code_" + error + "');");
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String safe = JSONObject.quote(matches.get(0));
                        evalJs("window.onSpeechResult && window.onSpeechResult(" + safe + ");");
                    } else {
                        evalJs("window.onSpeechError && window.onSpeechError('no-match');");
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            speechRecognizer.startListening(intent);
        });
    }

    private void nativeSpeak(String text) {
        runOnUiThread(() -> {
            if (tts != null && text != null && !text.trim().isEmpty()) {
                Bundle params = new Bundle();
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utt_" + System.currentTimeMillis());
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    /** Exposed to JS as window.Android */
    public class Bridge {

        @JavascriptInterface
        public void startListening() {
            nativeStartListening();
        }

        @JavascriptInterface
        public void speak(String text) {
            nativeSpeak(text);
        }

        @JavascriptInterface
        public void saveApiKey(String key) {
            prefs.edit().putString("gemini_api_key", key).apply();
        }

        @JavascriptInterface
        public String getApiKey() {
            return prefs.getString("gemini_api_key", "");
        }

        @JavascriptInterface
        public void saveSystemPrompt(String prompt) {
            prefs.edit().putString("system_prompt", prompt).apply();
        }

        @JavascriptInterface
        public String getSystemPrompt() {
            return prefs.getString("system_prompt", "");
        }

        @JavascriptInterface
        public void saveGgufPath(String path) {
            prefs.edit().putString("gguf_path", path).apply();
        }

        @JavascriptInterface
        public String getGgufPath() {
            return prefs.getString("gguf_path", "");
        }

        @JavascriptInterface
        public void saveLlmProvider(String provider) {
            prefs.edit().putString("llm_provider", provider).apply();
        }

        @JavascriptInterface
        public String getLlmProvider() {
            return prefs.getString("llm_provider", "gemini");
        }

        @JavascriptInterface
        public void loadGguf(String path) {
            android.util.Log.d("Bridge", "loadGguf called with path: " + path);
        }

        @JavascriptInterface
        public String runLocalInference(String prompt, String text) {
            return "Local inference not implemented yet. Please use Gemini for now.";
        }
    }
}