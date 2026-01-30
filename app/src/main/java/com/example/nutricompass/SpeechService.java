package com.example.nutricompass;

import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class SpeechService implements TextToSpeech.OnInitListener {
    private static final String TAG = "SpeechService";
    private static SpeechService instance;

    private TextToSpeech tts;
    private Context context;
    private boolean isInitialized = false;
    private boolean isSpeaking = false;
    private int currentStepIndex = 0;
    private List<String> cookingSteps = new ArrayList<>();
    private ImageButton voiceButton;
    private SpeechCallback callback;

    public interface SpeechCallback {
        void onSpeechStart(int stepIndex);
        void onSpeechDone(int stepIndex);
        void onSpeechError(String error);
        void onSpeechStopped();
    }

    private SpeechService(Context context) {
        this.context = context.getApplicationContext();
        initializeTTS();
    }

    public static synchronized SpeechService getInstance(Context context) {
        if (instance == null) {
            instance = new SpeechService(context);
        }
        return instance;
    }

    private void initializeTTS() {
        tts = new TextToSpeech(context, this);
        if (tts != null) {
            tts.setSpeechRate(0.9f);
            tts.setPitch(1.0f);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.CHINA);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "不支持中文语音");
                Toast.makeText(context, "不支持中文语音，请安装语音包", Toast.LENGTH_LONG).show();
            } else {
                isInitialized = true;
                Log.d(TAG, "TTS初始化成功");
                setupUtteranceListener();
            }
        } else {
            Log.e(TAG, "TTS初始化失败");
            Toast.makeText(context, "语音功能初始化失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupUtteranceListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    handleSpeechStart(utteranceId);
                }

                @Override
                public void onDone(String utteranceId) {
                    handleSpeechDone(utteranceId);
                }

                @Override
                public void onError(String utteranceId) {
                    handleSpeechError(utteranceId);
                }
            });
        } else {
            tts.setOnUtteranceCompletedListener(utteranceId -> handleSpeechDone(utteranceId));
        }
    }

    private void handleSpeechStart(String utteranceId) {
        Log.d(TAG, "开始朗读: " + utteranceId);
        isSpeaking = true;
        if (callback != null && utteranceId.startsWith("step_")) {
            int stepIndex = Integer.parseInt(utteranceId.split("_")[1]);
            callback.onSpeechStart(stepIndex);
        }
        updateButtonState();
    }

    private void handleSpeechDone(String utteranceId) {
        Log.d(TAG, "朗读完成: " + utteranceId);
        isSpeaking = false;

        if (utteranceId.startsWith("step_")) {
            int stepIndex = Integer.parseInt(utteranceId.split("_")[1]);
            if (callback != null) {
                callback.onSpeechDone(stepIndex);
            }

            if (stepIndex < cookingSteps.size() - 1) {
                currentStepIndex = stepIndex + 1;
                speakStep(currentStepIndex);
            } else {
                currentStepIndex = 0;
                if (callback != null) {
                    callback.onSpeechDone(-1);
                }
                updateButtonState();
            }
        }
    }

    private void handleSpeechError(String utteranceId) {
        Log.e(TAG, "朗读错误: " + utteranceId);
        isSpeaking = false;
        if (callback != null) {
            callback.onSpeechError("朗读失败");
        }
        updateButtonState();
    }

    public void setCookingSteps(List<String> steps) {
        this.cookingSteps.clear();
        if (steps != null) {
            this.cookingSteps.addAll(steps);
        }
        currentStepIndex = 0;
    }

    public void startSpeakingSteps() {
        if (!isInitialized) {
            Toast.makeText(context, "语音功能未准备好", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cookingSteps.isEmpty()) {
            Toast.makeText(context, "没有烹饪步骤可朗读", Toast.LENGTH_SHORT).show();
            return;
        }

        currentStepIndex = 0;
        speakStep(currentStepIndex);
    }

    private void speakStep(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= cookingSteps.size()) {
            return;
        }

        String text = cookingSteps.get(stepIndex);
        String utteranceId = "step_" + stepIndex;
        String stepText = "第" + (stepIndex + 1) + "步，" + text;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(stepText, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            tts.speak(stepText, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    public void stopSpeaking() {
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
            isSpeaking = false;
            if (callback != null) {
                callback.onSpeechStopped();
            }
            updateButtonState();
        }
    }

    public void pauseOrResume() {
        if (!isInitialized) return;

        if (tts.isSpeaking()) {
            tts.stop();
            isSpeaking = false;
        } else if (!cookingSteps.isEmpty()) {
            speakStep(currentStepIndex);
        }
        updateButtonState();
    }

    public void setVoiceButton(ImageButton button) {
        this.voiceButton = button;
        // 初始设置按钮颜色
        if (voiceButton != null) {
            voiceButton.post(() -> {
                voiceButton.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_orange_dark));
            });
        }
    }

    private void updateButtonState() {
        if (voiceButton != null) {
            voiceButton.post(() -> {
                if (isSpeaking) {
                    voiceButton.setImageResource(android.R.drawable.ic_media_pause);
                    voiceButton.setContentDescription("暂停朗读");
                    voiceButton.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_red_light));
                } else {
                    voiceButton.setImageResource(android.R.drawable.ic_btn_speak_now);
                    voiceButton.setContentDescription("开始朗读");
                    voiceButton.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_orange_dark));
                }
            });
        }
    }

    public void setCallback(SpeechCallback callback) {
        this.callback = callback;
    }

    public boolean isSpeaking() {
        return isSpeaking;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        isSpeaking = false;
        currentStepIndex = 0;
        cookingSteps.clear();
    }
}