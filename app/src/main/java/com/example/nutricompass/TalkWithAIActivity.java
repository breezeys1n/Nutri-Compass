package com.example.nutricompass;

import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class TalkWithAIActivity extends AppCompatActivity implements VoskRecognitionHelper.RecognitionCallback, SpeechService.SpeechCallback {
    private static final String TAG = "TalkWithAI_Log";
    private static final String WAKE_WORD_REGEX = ".*(大厨).*";
    private static final Pattern SENTENCE_END_PATTERN = Pattern.compile(".*[。！？.!?\\n]\\s*$");
    private TextView tvStatus, tvResult, tvPartial;
    private View rippleEffect;
    private VoskRecognitionHelper voskHelper;
    private OllamaApiClient ollamaClient;
    private SpeechService speechService;
    private ToneGenerator toneGenerator;
    private boolean isAiResponding = false;
    private boolean isWaitingForQuestion = false;
    private StringBuilder aiResponseBuffer = new StringBuilder();
    private boolean isStreaming = false;
    private boolean isSpeakingCurrentResponse = false;
    private Handler responseHandler = new Handler(Looper.getMainLooper());

    // --- 菜谱上下文 ---
    private String recipeName;
    private List<String> recipeSteps;

    // --- 记忆管理：最近5轮 ---
    private LinkedList<String> chatHistory = new LinkedList<>();
    private static final int MAX_HISTORY_ROUNDS = 5;

    // TalkWithAIActivity.java - 修改onCreate方法中的初始化部分

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

        // 3. 服务初始化 - 传入context
        ollamaClient = new OllamaApiClient(this);  // 关键修改：传入this
        voskHelper = new VoskRecognitionHelper(this, this);
        speechService = SpeechService.getInstance(this);
        speechService.setCallback(this);

        try {
            //提示音
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (Exception e) {
            Log.e(TAG, "ToneGenerator 异常");
        }

        // 4. 加载模型
        tvStatus.setText("状态: 大厨正在穿围裙...");
        voskHelper.initModel();
    }

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
        isStreaming = true;
        isSpeakingCurrentResponse = false;
        voskHelper.stopRecording();
        // 清空之前的响应缓冲
        aiResponseBuffer.setLength(0);
        runOnUiThread(() -> {
            tvStatus.setText("状态: 大厨思考中...");
            tvResult.setText("问: " + question + "\n\n答: ");
            startRippleAnimation();
        });
        // 组装 Prompt (上下文 + 5轮记忆 + 食谱步骤)
        StringBuilder sb = new StringBuilder();
        sb.append("【系统指令】\n");
        sb.append("你是一位专业大厨，正在指导用户烹饪。用户当前正在做菜，正在烹饪的食谱是：").append(recipeName).append("\n\n");

        // 添加完整的食谱步骤
        if (recipeSteps != null && !recipeSteps.isEmpty()) {
            sb.append("【完整烹饪步骤】\n");
            for (int i = 0; i < recipeSteps.size(); i++) {
                sb.append(i + 1).append(". ").append(recipeSteps.get(i)).append("\n");
            }
            sb.append("\n");
        }

        // 添加聊天历史
        if (!chatHistory.isEmpty()) {
            sb.append("【最近对话记录】\n");
            for (String h : chatHistory) {
                sb.append(h).append("\n");
            }
            sb.append("\n");
        }

        sb.append("【用户当前提问】\n");
        sb.append(question).append("\n\n");

        sb.append("【重要规则】\n");
        sb.append("1. 用户正在做饭，回答要简练、实用\n");
        sb.append("2. 参考上面的完整烹饪步骤，不要随意发挥\n");
        sb.append("3. 语气亲切，像一个经验丰富的大厨在厨房现场指导\n");
        sb.append("4. 如果用户问的问题不在食谱范围内，可以适当发挥，但提醒用户注意安全\n");
        sb.append("5. 回答控制在3-5句话内，不要长篇大论\n\n");

        sb.append("请根据以上信息，为用户提供专业的烹饪指导：");

        Log.d(TAG, "准备发送给AI的提示词长度: " + sb.length());
        Log.d(TAG, "提示词内容: " + sb.toString());
        ollamaClient.streamGenerate(sb.toString(), new OllamaApiClient.StreamResponseCallback() {
            @Override
            public void onNewToken(String token) {
                runOnUiThread(() -> {
                    // 更新UI显示
                    tvResult.append(token);
                    // 缓冲token
                    aiResponseBuffer.append(token);
                    // 检查是否到达句子结束点
                    String currentText = aiResponseBuffer.toString();
                    if (SENTENCE_END_PATTERN.matcher(currentText).matches() && !isSpeakingCurrentResponse) {
                        // 到达句子结束点，开始语音合成
                        String sentenceToSpeak = aiResponseBuffer.toString().trim();
                        if (!sentenceToSpeak.isEmpty()) {
                            isSpeakingCurrentResponse = true;
                            speechService.speak(sentenceToSpeak);
                            // 清空缓冲区，为下一句做准备
                            aiResponseBuffer.setLength(0);
                        }
                    }
                });
            }

            @Override
            public void onComplete(String fullResponse) {
                isStreaming = false;
                // 处理缓冲中剩余的内容（最后一句可能没有结束标点）
                runOnUiThread(() -> {
                    String remainingText = aiResponseBuffer.toString().trim();
                    if (!remainingText.isEmpty() && !isSpeakingCurrentResponse) {
                        speechService.speak(remainingText);
                    } else if (remainingText.isEmpty() && !isSpeakingCurrentResponse) {
                        // 如果缓冲区为空且当前没有在说话，直接调用resetToListening
                        resetToListening();
                    }
                    // 保存到历史
                    if (chatHistory.size() >= MAX_HISTORY_ROUNDS) chatHistory.removeFirst();
                    chatHistory.add("问:" + question + "|答:" + fullResponse);
                });
            }

            @Override
            public void onError(String error) {
                isStreaming = false;
                isAiResponding = false;
                runOnUiThread(() -> {
                    tvStatus.setText("状态: 出错了，请重试");
                    tvResult.append("\n[错误: " + error + "]");
                    new Handler(Looper.getMainLooper()).postDelayed(() -> resetToListening(), 2000);
                });
            }
        });
    }

    private void resetToListening() {
        isAiResponding = false;
        isWaitingForQuestion = false;
        isStreaming = false;
        isSpeakingCurrentResponse = false;
        aiResponseBuffer.setLength(0);

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
        // 当前句子读完了
        isSpeakingCurrentResponse = false;

        // 检查是否还有缓冲内容需要读
        String remainingText = aiResponseBuffer.toString().trim();
        if (!remainingText.isEmpty() && !isStreaming) {
            // 流式生成已完成，但还有剩余内容
            speechService.speak(remainingText);
            aiResponseBuffer.setLength(0);
        } else if (remainingText.isEmpty() && !isStreaming) {
            // 流式生成已完成且所有内容都已读完，回到监听状态
            new Handler(Looper.getMainLooper()).postDelayed(this::resetToListening, 500);
        }
        // 如果 isStreaming 为 true，说明还在生成中，等待下一个句子结束点
    }

    @Override
    public void onStatus(String status) {
        if (status.contains("成功")) {
            // 模型初始化成功，立刻触发欢迎词
            runOnUiThread(this::sayWelcomeMessage);
        }
    }

    @Override
    public void onPartialResult(String text) {
        runOnUiThread(() -> tvPartial.setText("正在听: " + text));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGenerator != null) toneGenerator.release();
        responseHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onSpeechStart(int stepIndex) {
        runOnUiThread(() -> tvStatus.setText("状态: 大厨播报中..."));
    }

    @Override
    public void onSpeechError(String error) {
        isSpeakingCurrentResponse = false;
        resetToListening();
    }

    @Override
    public void onSpeechStopped() {
        isSpeakingCurrentResponse = false;
        resetToListening();
    }

    @Override
    public void onRecordingStarted() {}

    @Override
    public void onRecordingStopped() {}

    @Override
    public void onError(String error) {
        Log.e(TAG, error);
    }
}