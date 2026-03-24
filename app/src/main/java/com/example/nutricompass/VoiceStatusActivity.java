package com.example.nutricompass;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class VoiceStatusActivity extends AppCompatActivity implements VoskRecognitionHelper.RecognitionCallback {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

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

        BackButtonUtil.setupBackButton(this);

        fabMic = findViewById(R.id.fab_mic);
        btnNextCamera = findViewById(R.id.btn_next_camera);
        etStatusInput = findViewById(R.id.et_status_input);
        tvPartial = findViewById(R.id.tv_vosk_partial);
        tvMicHint = findViewById(R.id.tv_mic_hint);

        userGoal = getIntent().getStringExtra("user_goal");

        voskHelper = new VoskRecognitionHelper(this, this);
        voskHelper.initModel();

        fabMic.setOnClickListener(v -> toggleRecognition());

        btnNextCamera.setOnClickListener(v -> {
            String desc = etStatusInput.getText().toString();
            Intent intent = new Intent(this, CameraActivity.class);
            intent.putExtra("user_status_desc", desc);
            intent.putExtra("user_goal", userGoal);
            startActivity(intent);
        });
    }

    private void toggleRecognition() {
        // ==================== 检查录音权限 ====================
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }
        // ====================================================

        if (!isRecording) {
            voskHelper.startRecording();
            fabMic.setImageResource(android.R.drawable.ic_media_pause);
            fabMic.setBackgroundTintList(ColorStateList.valueOf(0xFFFF5252));
            tvMicHint.setText("正在录音...点击停止");
            isRecording = true;
        } else {
            voskHelper.stopRecording();
            fabMic.setImageResource(android.R.drawable.ic_btn_speak_now);
            fabMic.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
            tvMicHint.setText("点击开始录入");
            isRecording = false;
        }
    }

    // ==================== 权限回调 ====================
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "录音权限已获取", Toast.LENGTH_SHORT).show();
                // 权限获取后，再次触发录音
                toggleRecognition();
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_LONG).show();
            }
        }
    }
    // ====================================================

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

    @Override
    public void onRecordingStopped() {
        Log.d("Vosk", "录音已停止");
    }
}