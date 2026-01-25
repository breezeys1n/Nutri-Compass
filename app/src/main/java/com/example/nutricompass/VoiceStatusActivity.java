package com.example.nutricompass;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class VoiceStatusActivity extends AppCompatActivity implements VoskRecognitionHelper.RecognitionCallback {

    private VoskRecognitionHelper voskHelper;
    private EditText etStatusInput;
    private TextView tvPartial, tvMicHint;
    private FloatingActionButton fabMic;
    private MaterialButton btnNextCamera;

    private boolean isRecording = false;
    private String userGoal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_status);

        // --- 修复返回键：必须放在最前面 ---
        BackButtonUtil.setupBackButton(this);

        // --- 核心修复：直接使用成员变量，不要在前面加类名(如 FloatingActionButton) ---
        fabMic = findViewById(R.id.fab_mic);
        btnNextCamera = findViewById(R.id.btn_next_camera);
        etStatusInput = findViewById(R.id.et_status_input);
        tvPartial = findViewById(R.id.tv_vosk_partial);
        tvMicHint = findViewById(R.id.tv_mic_hint);

        userGoal = getIntent().getStringExtra("user_goal");

        // 初始化 Vosk
        voskHelper = new VoskRecognitionHelper(this, this);
        voskHelper.initModel();

        // 麦克风点击事件
        fabMic.setOnClickListener(v -> toggleRecognition());

        // 跳转逻辑
        btnNextCamera.setOnClickListener(v -> {
            String desc = etStatusInput.getText().toString();
            Intent intent = new Intent(this, CameraActivity.class);
            intent.putExtra("user_status_desc", desc);
            intent.putExtra("user_goal", userGoal);
            startActivity(intent);
        });
    }

    private void toggleRecognition() {
        if (!isRecording) {
            // 注意：请检查你的 VoskRecognitionHelper 是否提供了 startRecognition 方法
            // 如果报错，请确认该 helper 里的方法名，通常是 start() 或 startRecognition()
            voskHelper.startRecording();
            fabMic.setImageResource(android.R.drawable.ic_media_pause);
            fabMic.setBackgroundTintList(ColorStateList.valueOf(0xFFFF5252)); // 变红
            tvMicHint.setText("正在录音...点击停止");
            isRecording = true;
        } else {
            voskHelper.stopRecording();
            fabMic.setImageResource(android.R.drawable.ic_btn_speak_now);
            fabMic.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50)); // 变绿
            tvMicHint.setText("点击开始录入");
            isRecording = false;
        }
    }

    // --- 实现回调接口（补全漏掉的方法） ---

    @Override
    public void onStatus(String status) {
        Log.d("VoskStatus", status);
    }

    @Override
    public void onResult(String text) {
        runOnUiThread(() -> {
            if (text != null && !text.isEmpty()) {
                String existing = etStatusInput.getText().toString();
                etStatusInput.setText(existing + text + " ");
                etStatusInput.setSelection(etStatusInput.getText().length());
            }
            tvPartial.setText("");
        });
    }

    @Override
    public void onPartialResult(String text) {
        runOnUiThread(() -> tvPartial.setText("正在识别：" + text));
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "语音错误: " + error, Toast.LENGTH_SHORT).show();
            isRecording = false;
            fabMic.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
        });
    }

    @Override
    public void onRecordingStarted() {
        Log.d("Vosk", "录音已开始");
    }

    // 修复报错：必须实现这个方法
    @Override
    public void onRecordingStopped() {
        Log.d("Vosk", "录音已停止");
    }
}