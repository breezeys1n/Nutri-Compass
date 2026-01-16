package com.example.nutricompass;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText etHeight, etWeight;
    private Spinner spinnerGoal;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化控件
        etHeight = findViewById(R.id.et_height);
        etWeight = findViewById(R.id.et_weight);
        spinnerGoal = findViewById(R.id.spinner_goal);
        btnSave = findViewById(R.id.btn_save);

        // 点击保存按钮
        btnSave.setOnClickListener(v -> {
            String height = etHeight.getText().toString();
            String weight = etWeight.getText().toString();
            String goal = spinnerGoal.getSelectedItem().toString(); // 获取选中的目标

            if (height.isEmpty() || weight.isEmpty()) {
                Toast.makeText(this, "请完整填写信息", Toast.LENGTH_SHORT).show();
            } else {
                // 1. 创建跳转意图
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);

                // 2. 把数据“塞进”意图里 (Key-Value 形式)
                intent.putExtra("user_goal", goal);
                intent.putExtra("user_height", height);
                intent.putExtra("user_weight", weight);

                // 3. 开始跳转
                startActivity(intent);
            }
        });
    }
}