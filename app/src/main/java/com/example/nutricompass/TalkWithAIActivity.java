package com.example.nutricompass;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class TalkWithAIActivity extends AppCompatActivity implements VoskRecognitionHelper.RecognitionCallback {
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1;

    private VoskRecognitionHelper voskHelper;
    private TextView tvStatus, tvResult, tvPartial;
    private Button btnStart, btnStop, btnInit;

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

        // 初始化识别助手
        voskHelper = new VoskRecognitionHelper(this, this);

        // 按钮点击事件
        btnInit.setOnClickListener(v -> {
            btnInit.setEnabled(false);
            tvStatus.setText("初始化中...");
            voskHelper.initModel();
        });

        btnStart.setOnClickListener(v -> {
            if (checkPermission()) {
                voskHelper.startRecording();
            }
        });

        btnStop.setOnClickListener(v -> voskHelper.stopRecording());

        // 初始化时检查权限
        if (!checkPermission()) {
            requestPermission();
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音识别", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onResult(String text) {
        runOnUiThread(() -> tvResult.append("\n" + text));
    }

    @Override
    public void onPartialResult(String text) {
        runOnUiThread(() -> tvPartial.setText("正在识别: " + text));
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(TalkWithAIActivity.this, error, Toast.LENGTH_SHORT).show();
            tvStatus.setText("错误: " + error);
            btnInit.setEnabled(true);
        });
    }

    @Override
    public void onStatus(String status) {
        runOnUiThread(() -> tvStatus.setText("状态: " + status));
    }

    @Override
    public void onRecordingStarted() {
        runOnUiThread(() -> {
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            tvPartial.setText("");
        });
    }

    @Override
    public void onRecordingStopped() {
        runOnUiThread(() -> {
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
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
