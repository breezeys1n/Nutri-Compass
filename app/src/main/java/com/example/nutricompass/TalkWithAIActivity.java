package com.example.nutricompass;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TalkWithAIActivity extends AppCompatActivity implements VoskRecognitionHelper.RecognitionCallback, SpeechService.SpeechCallback {
    private static final String TAG = "TalkWithAI_Log";
    private static final String WAKE_WORD_REGEX = ".*(大厨|大叔|大初|大出).*";

    private TextView tvStatus, tvResult, tvPartial;
    private View rippleEffect;
    private VoskRecognitionHelper voskHelper;
    private OllamaApiClient ollamaClient;
    private SpeechService speechService;
    private ToneGenerator toneGenerator;

    private boolean isAiResponding = false;
    private boolean isWaitingForQuestion = false;

    // --- 菜谱上下文 ---
    private String recipeName;
    private List<String> recipeSteps;

    // --- 记忆管理：最近5轮 ---
    private LinkedList<String> chatHistory = new LinkedList<>();
    private static final int MAX_HISTORY_ROUNDS = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_talk_with_ai);

        // 1. 获取数据
        recipeName = getIntent().getStringExtra("recipe_name");
        recipeSteps = getIntent().getStringArrayListExtra("recipe_steps");

        // 2. 初始化 UI
        BackButtonUtil.setupBackButton(this);
        tvStatus = findViewById(R.id.tv_status);
        tvResult = findViewById(R.id.tv_result);
        tvPartial = findViewById(R.id.tv_partial);
        rippleEffect = findViewById(R.id.ripple_effect);
        Button btnFinish = findViewById(R.id.btn_finish_cooking);

        btnFinish.setOnClickListener(v -> {
            Intent intent = new Intent(this, NutritionReviewActivity.class);
            startActivity(intent);
            finish();
        });

        // 3. 服务初始化
        ollamaClient = new OllamaApiClient();
        voskHelper = new VoskRecognitionHelper(this, this);
        speechService = SpeechService.getInstance(this);
        speechService.setCallback(this);

        try {
            // 尖锐提示音
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (Exception e) {
            Log.e(TAG, "ToneGenerator 异常");
        }

        // 4. 加载模型（加载成功后会通过 onStatus 触发欢迎词）
        tvStatus.setText("状态: 大厨正在穿围裙...");
        voskHelper.initModel();
    }

    /**
     * 【新功能】自动生成并播报欢迎词
     */
    private void sayWelcomeMessage() {
        isAiResponding = true; // 先锁住，防止播报欢迎词时用户说话
        String welcomeText = "你好！我是你的 AI 大厨。今天我们要一起做" +
                (recipeName != null ? recipeName : "美食") +
                "，我已经准备好回答你的任何问题了，喊我'大厨大厨'即可！";

        runOnUiThread(() -> {
            tvStatus.setText("状态: 欢迎中...");
            tvResult.setText("大厨: " + welcomeText);
            startRippleAnimation();
            speechService.speak(welcomeText); // 开始播报
        });
    }

    private void playWakeTone() {
        if (toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_DTMF_S, 150);
        }
    }

    @Override
    public void onResult(String text) {
        runOnUiThread(() -> {
            if (text == null || text.isEmpty() || isAiResponding) return;

            if (isWaitingForQuestion) {
                isWaitingForQuestion = false;
                askAi(text);
                return;
            }

            if (text.matches(WAKE_WORD_REGEX)) {
                playWakeTone();
                showVisualFeedback(true);
                String question = text.replaceAll(".*(大厨大厨|大叔大叔|大厨|大叔)", "").trim();
                if (question.length() > 1) {
                    askAi(question);
                } else {
                    isWaitingForQuestion = true;
                    tvStatus.setText("状态: 在呢，请讲...");
                }
            }
            tvPartial.setText("");
        });
    }

    private void askAi(String question) {
        isAiResponding = true;
        isWaitingForQuestion = false;
        voskHelper.stopRecording();

        runOnUiThread(() -> {
            tvStatus.setText("状态: 大厨思考中...");
            tvResult.setText("问: " + question + "\n答: ");
            startRippleAnimation();
        });

        // 组装 Prompt (上下文 + 5轮记忆)
        StringBuilder sb = new StringBuilder();
        sb.append("你是大厨。当前菜谱:").append(recipeName).append("。最近对话:\n");
        for (String h : chatHistory) sb.append(h).append("\n");
        sb.append("用户问:").append(question);

        ollamaClient.streamGenerate(sb.toString(), new OllamaApiClient.StreamResponseCallback() {
            @Override
            public void onNewToken(String token) {
                runOnUiThread(() -> tvResult.append(token));
            }

            @Override
            public void onComplete(String fullResponse) {
                if (chatHistory.size() >= MAX_HISTORY_ROUNDS) chatHistory.removeFirst();
                chatHistory.add("问:" + question + "|答:" + fullResponse);
                runOnUiThread(() -> speechService.speak(fullResponse));
            }

            @Override
            public void onError(String error) { resetToListening(); }
        });
    }

    private void resetToListening() {
        isAiResponding = false;
        isWaitingForQuestion = false;
        runOnUiThread(() -> {
            showVisualFeedback(false);
            tvStatus.setText("状态: 待命 (请说'大厨大厨')");
            voskHelper.startRecording();
        });
    }

    private void showVisualFeedback(boolean show) {
        if (rippleEffect != null) rippleEffect.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void startRippleAnimation() {
        if (!isAiResponding || rippleEffect == null || rippleEffect.getVisibility() != View.VISIBLE) return;
        rippleEffect.animate().scaleX(1.3f).scaleY(1.3f).alpha(0.3f).setDuration(800).withEndAction(() -> {
            rippleEffect.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(800).withEndAction(this::startRippleAnimation).start();
        }).start();
    }

    // --- 完整回调 ---
    @Override
    public void onSpeechDone(int stepIndex) {
        // 欢迎词读完或回答读完，都回到监听状态
        new Handler(Looper.getMainLooper()).postDelayed(this::resetToListening, 500);
    }

    @Override
    public void onStatus(String status) {
        if (status.contains("成功")) {
            // 模型初始化成功，立刻触发欢迎词
            runOnUiThread(this::sayWelcomeMessage);
        }
    }

    @Override public void onPartialResult(String text) { runOnUiThread(() -> tvPartial.setText("正在听: " + text)); }
    @Override protected void onDestroy() { super.onDestroy(); if (toneGenerator != null) toneGenerator.release(); }
    @Override public void onSpeechStart(int stepIndex) { runOnUiThread(() -> tvStatus.setText("状态: 大厨播报中...")); }
    @Override public void onSpeechError(String error) { resetToListening(); }
    @Override public void onSpeechStopped() { resetToListening(); }
    @Override public void onRecordingStarted() {}
    @Override public void onRecordingStopped() {}
    @Override public void onError(String error) { Log.e(TAG, error); }
}