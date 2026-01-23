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

    private boolean isModelInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_talk_with_ai);

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
            tvResult.append(text + "\n");
            // 滚动到底部
            final TextView resultView = tvResult;
            resultView.post(() -> {
                int scrollAmount = resultView.getLayout().getLineTop(resultView.getLineCount()) - resultView.getHeight();
                if (scrollAmount > 0) {
                    resultView.scrollTo(0, scrollAmount);
                } else {
                    resultView.scrollTo(0, 0);
                }
            });
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
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
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