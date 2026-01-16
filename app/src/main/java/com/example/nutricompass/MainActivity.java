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
    private UserProfile userProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化用户信息管理
        userProfile = new UserProfile(this);

        // 初始化控件
        etHeight = findViewById(R.id.et_height);
        etWeight = findViewById(R.id.et_weight);
        spinnerGoal = findViewById(R.id.spinner_goal);
        btnSave = findViewById(R.id.btn_save);

        // 如果已有用户信息，显示在界面上
        if (userProfile.isProfileComplete()) {
            etHeight.setText(userProfile.getHeight());
            etWeight.setText(userProfile.getWeight());
            // TODO: 设置spinner选中项（需要根据值来设置位置）
        }

        // 点击保存按钮
        btnSave.setOnClickListener(v -> {
            String height = etHeight.getText().toString();
            String weight = etWeight.getText().toString();
            String goal = spinnerGoal.getSelectedItem().toString();

            if (height.isEmpty() || weight.isEmpty()) {
                Toast.makeText(this, "请完整填写信息", Toast.LENGTH_SHORT).show();
            } else {
                // 保存用户信息到本地
                userProfile.saveBasicInfo(height, weight, goal);

                // 显示BMI信息
                double bmi = userProfile.calculateBMI();
                String bmiInfo = String.format("BMI: %.1f - ", bmi);
                if (bmi < 18.5) {
                    bmiInfo += "体重偏轻";
                } else if (bmi < 24) {
                    bmiInfo += "正常范围";
                } else if (bmi < 28) {
                    bmiInfo += "超重";
                } else {
                    bmiInfo += "肥胖";
                }

                Toast.makeText(this, "信息已保存！" + bmiInfo, Toast.LENGTH_LONG).show();

                // 跳转到拍摄页面
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                startActivity(intent);
            }
        });
    }
}