package com.example.nutricompass;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity_Debug";
    private static final int PERMISSION_REQUEST_CODE = 100;

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

        // 如果已有用户信息，回显数据
        if (userProfile.isProfileComplete()) {
            etHeight.setText(userProfile.getHeight());
            etWeight.setText(userProfile.getWeight());
        }

        // 核心修改：点击保存并检查权限
        btnSave.setOnClickListener(v -> {
            String height = etHeight.getText().toString();
            String weight = etWeight.getText().toString();
            String goal = spinnerGoal.getSelectedItem().toString();

            if (height.isEmpty() || weight.isEmpty()) {
                Toast.makeText(this, "请完整填写信息", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1. 保存用户信息
            userProfile.saveBasicInfo(height, weight, goal);

            // 2. 检查权限并根据情况决定下一步
            if (checkAndRequestPermissions()) {
                proceedToCamera();
            }
        });
    }

    /**
     * 检查相机和定位权限
     * @return 如果权限已经全部授予返回 true
     */
    private boolean checkAndRequestPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();

        // 基础权限：相机、定位
        listPermissionsNeeded.add(Manifest.permission.CAMERA);
        listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        listPermissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        // 相册读取权限 (适配 Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            listPermissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        List<String> remainingPermissions = new ArrayList<>();
        for (String p : listPermissionsNeeded) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                remainingPermissions.add(p);
            }
        }

        if (!remainingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    remainingPermissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    /**
     * 权限回调处理
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 只要拿到了基本权限就允许跳转
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                proceedToCamera();
            } else {
                Toast.makeText(this, "需要相机和定位权限才能正常使用功能", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 跳转到拍摄页面
     */
    private void proceedToCamera() {
        // 计算并提示 BMI (保留你原来的逻辑)
        double bmi = userProfile.calculateBMI();
        String bmiLevel = getBMIDescription(bmi);
        Toast.makeText(this, String.format("BMI: %.1f (%s) 数据已保存", bmi, bmiLevel), Toast.LENGTH_SHORT).show();

        // 跳转
        Intent intent = new Intent(MainActivity.this, CameraActivity.class);
        startActivity(intent);
    }

    private String getBMIDescription(double bmi) {
        if (bmi < 18.5) return "体重偏轻";
        if (bmi < 24) return "正常范围";
        if (bmi < 28) return "超重";
        return "肥胖";
    }
}