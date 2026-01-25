package com.example.nutricompass;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class TalkWithAIActivity extends AppCompatActivity implements VoskRecognitionHelper.RecognitionCallback {
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1;

    private VoskRecognitionHelper voskHelper;
    private TextView tvStatus, tvResult, tvPartial;
    private Button btnStart, btnStop, btnInit;

    private OllamaApiClient ollamaClient;
    private boolean isAiResponding = false;
    private StringBuilder conversationHistory = new StringBuilder();
    private static final int MAX_HISTORY_LENGTH = 1000; // 控制上下文长度
    private boolean isModelInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_talk_with_ai);

        BackButtonUtil.setupBackButton(this);

        // 初始化视图
        tvStatus = findViewById(R.id.tv_status);
        tvResult = findViewById(R.id.tv_result);
        tvPartial = findViewById(R.id.tv_partial);
        btnInit = findViewById(R.id.btn_init);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);

        // 设置初始状态
        tvStatus.setText("请先初始化模型");
        tvPartial.setText("部分结果将显示在这里...");
        tvResult.setText("识别结果将显示在这里...\n");

        // 初始化识别助手
        voskHelper = new VoskRecognitionHelper(this, this);
        ollamaClient = new OllamaApiClient();
        // 按钮点击事件
        btnInit.setOnClickListener(v -> {
            btnInit.setEnabled(false);
            btnInit.setText("初始化中...");
            tvStatus.setText("正在初始化模型...");
            tvResult.setText("识别结果将显示在这里...\n");
            voskHelper.initModel();
        });

        btnStart.setOnClickListener(v -> {
            if (!isModelInitialized) {
                Toast.makeText(this, "请先初始化模型", Toast.LENGTH_SHORT).show();
                return;
            }

            if (checkPermission()) {
                voskHelper.startRecording();
            } else {
                requestPermission();
            }
        });

        btnStop.setOnClickListener(v -> {
            if (voskHelper.isRecording()) {
                voskHelper.stopRecording();
            }
        });

        // 初始化按钮状态
        btnStart.setEnabled(false);
        btnStop.setEnabled(false);

        // 检查权限
        if (!checkPermission()) {
            requestPermission();
        }
        // 检查并请求权限
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // 如果权限没有被授予，请求权限
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_RECORD_AUDIO
            );
        }
    }

    /**
     * 检查录音权限
     */
    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 请求录音权限
     */
    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show();
                if (isModelInitialized) {
                    btnStart.setEnabled(true);
                }
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音识别", Toast.LENGTH_LONG).show();
                btnStart.setEnabled(false);
            }
        }
    }

    @Override
    public void onResult(String text) {
        runOnUiThread(() -> {
            // 1. 显示用户说的话
            tvResult.append("\n👤 您: " + text + "\n");
            // 2. 将用户输入添加到对话历史
            conversationHistory.append("用户: ").append(text).append("\n");
            // 3. 修剪历史以避免过长
            if (conversationHistory.length() > MAX_HISTORY_LENGTH) {
                conversationHistory.delete(0, conversationHistory.length() - MAX_HISTORY_LENGTH);
            }
            // 4. 更新状态并调用 AI
            tvPartial.setText("正在思考...");
            isAiResponding = true;
            btnStart.setEnabled(false); // AI 响应时禁用录音

            // 5. 调用 Ollama 流式 API
            ollamaClient.streamGenerate(
                    "你是一个专业的健康营养助手。请用中文回答以下问题，保持友好、简洁、实用。\n" +
                            conversationHistory.toString(),
                    new OllamaApiClient.StreamResponseCallback() {
                        @Override
                        public void onNewToken(String token) {
                            // 流式接收每个词，更新 UI
                            runOnUiThread(() -> {
                                tvPartial.setText("AI 正在回答...");
                                tvResult.append(token); // 逐词追加显示
                                // 滚动到底部
                                scrollToBottom();
                            });
                        }

                        @Override
                        public void onComplete(String fullResponse) {
                            runOnUiThread(() -> {
                                // 将完整的 AI 回复添加到对话历史
                                conversationHistory.append("助手: ").append(fullResponse).append("\n");
                                tvResult.append("\n");
                                tvPartial.setText("AI 回答完成，可以继续说话");
                                isAiResponding = false;
                                if (!voskHelper.isRecording()) {
                                    btnStart.setEnabled(true);
                                }
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                tvResult.append("\n❌ AI 错误: " + error + "\n");
                                tvPartial.setText("AI 响应出错");
                                isAiResponding = false;
                                btnStart.setEnabled(true);
                                scrollToBottom();
                            });
                        }
                    }
            );

            scrollToBottom();
        });
    }
    // 辅助方法：滚动 TextView 到底部
    private void scrollToBottom() {
        tvResult.post(() -> {
            int scrollAmount = tvResult.getLayout().getLineTop(tvResult.getLineCount()) - tvResult.getHeight();
            if (scrollAmount > 0) {
                tvResult.scrollTo(0, scrollAmount);
            }
        });
    }
    @Override
    public void onPartialResult(String text) {
        runOnUiThread(() -> {
            if (text != null && !text.isEmpty()) {
                tvPartial.setText("正在识别: " + text);
            } else {
                tvPartial.setText("部分结果将显示在这里...");
            }
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(TalkWithAIActivity.this, error, Toast.LENGTH_LONG).show();
            tvStatus.setText("错误: " + error);
            btnInit.setEnabled(true);
            btnInit.setText("重新初始化模型");
            isModelInitialized = false;
            btnStart.setEnabled(false);
        });
    }

    @Override
    public void onStatus(String status) {
        runOnUiThread(() -> {
            tvStatus.setText("状态: " + status);

            // 如果状态是模型加载成功，启用开始按钮
            if (status.contains("模型加载成功")) {
                isModelInitialized = true;
                btnInit.setText("模型已加载");
                btnInit.setEnabled(false);

                if (checkPermission()) {
                    btnStart.setEnabled(true);
                }
            }
        });
    }

    @Override
    public void onRecordingStarted() {
        runOnUiThread(() -> {
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            tvPartial.setText("正在录音...请说话");
        });
    }

    @Override
    public void onRecordingStopped() {
        runOnUiThread(() -> {
            btnStop.setEnabled(false);
            // 只有当 AI 没有在响应时，才重新启用开始按钮
            if (!isAiResponding) {
                btnStart.setEnabled(true);
            }
            tvPartial.setText("录音已停止");
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voskHelper != null) {
            voskHelper.release();
        }
    }
}