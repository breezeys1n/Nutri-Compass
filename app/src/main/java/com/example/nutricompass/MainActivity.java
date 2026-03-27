package com.example.nutricompass;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity_Debug";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String PREFS_NAME = "NutriCompassPrefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch";

    private Button btnGenerateRecipe;
    private BottomNavigationView bottomNavigationView;

    // 用户信息录入相关控件
    private TextInputEditText etHeight, etWeight, etGender, etAge, etGoal;
    private Button btnSaveUserInfo;
    private UserProfile userProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 检查是否是第一次启动
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true);

        if (isFirstLaunch) {
            // 第一次启动，显示用户信息录入界面
            setContentView(R.layout.activity_user_setup);
            setupUserInfoInput();
        } else {
            // 不是第一次启动，显示主界面
            setContentView(R.layout.activity_main);
            setupMainInterface();
        }
    }

    /**
     * 设置用户信息录入界面
     */
    private void setupUserInfoInput() {
        userProfile = new UserProfile(this);

        // 初始化控件
        etHeight = findViewById(R.id.et_height);
        etWeight = findViewById(R.id.et_weight);
        etGender = findViewById(R.id.et_gender);
        etAge = findViewById(R.id.et_age);
        etGoal = findViewById(R.id.et_goal);
        btnSaveUserInfo = findViewById(R.id.btn_save);

        // 设置性别选择点击事件
        etGender.setOnClickListener(v -> showGenderDialog());

        // 设置目标选择点击事件
        etGoal.setOnClickListener(v -> showGoalDialog());

        // 保存按钮点击事件
        btnSaveUserInfo.setOnClickListener(v -> saveUserInfoAndProceed());
    }

    /**
     * 设置主界面（带底部导航栏）
     */
    private void setupMainInterface() {
        // 初始化控件
        btnGenerateRecipe = findViewById(R.id.btn_generate_recipe);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // 设置底部导航栏选中监听
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_home) {
                // 已经是首页，无需跳转
                return true;
            } else if (itemId == R.id.navigation_history) {
                // 跳转到历史页面
                Intent intent = new Intent(this, HistoryActivity.class);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.navigation_profile) {
                // 跳转到个人页面（用于修改信息）
                Intent intent = new Intent(this, ProfileActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });

        // 设置首页为选中状态
        bottomNavigationView.setSelectedItemId(R.id.navigation_home);

        // 核心功能按钮点击事件
        btnGenerateRecipe.setOnClickListener(v -> {
            // 检查权限
            if (checkAndRequestPermissions()) {
                // 跳转到食谱生成页面
                proceedToRecipeGeneration();
            }
        });
    }

    /**
     * 保存用户信息并进入主界面
     */
    private void saveUserInfoAndProceed() {
        String height = etHeight.getText().toString();
        String weight = etWeight.getText().toString();
        String gender = etGender.getText().toString();
        String age = etAge.getText().toString();
        String goal = etGoal.getText().toString();

        // 验证输入
        if (height.isEmpty() || weight.isEmpty() || gender.isEmpty() || age.isEmpty() || goal.isEmpty()) {
            Toast.makeText(this, "请完整填写所有信息", Toast.LENGTH_SHORT).show();
            return;
        }

        // 保存用户信息
        userProfile.saveBasicInfo(height, weight, goal);
        userProfile.saveAdditionalInfo(gender, age);

        // 标记已不是第一次启动
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_FIRST_LAUNCH, false);
        editor.apply();

        // 计算并显示BMI
        double bmi = userProfile.calculateBMI();
        String bmiLevel = getBMIDescription(bmi);
        Toast.makeText(this, String.format("BMI: %.1f (%s) 信息已保存", bmi, bmiLevel), Toast.LENGTH_SHORT).show();

        // 重新加载主界面
        recreate();
    }

    /**
     * 显示性别选择对话框
     */
    private void showGenderDialog() {
        String[] genders = {"男", "女"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("选择性别")
                .setItems(genders, (dialog, which) -> {
                    etGender.setText(genders[which]);
                })
                .show();
    }

    /**
     * 显示目标选择对话框
     */
    private void showGoalDialog() {
        String[] goals = {"减脂", "增肌", "维持体重", "改善饮食结构", "提高运动表现"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("选择健康目标")
                .setItems(goals, (dialog, which) -> {
                    etGoal.setText(goals[which]);
                })
                .show();
    }

    /**
     * 检查相机和定位权限
     */
    private boolean checkAndRequestPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();

        // 基础权限：相机、定位、录音
        listPermissionsNeeded.add(Manifest.permission.CAMERA);
        listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        listPermissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);

        // 相册读取权限
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
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                proceedToRecipeGeneration();
            } else {
                Toast.makeText(this, "需要权限才能正常使用功能", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 跳转到食谱生成页面
     */
    private void proceedToRecipeGeneration() {
        Intent intent = new Intent(MainActivity.this, VoiceStatusActivity.class);

        if (userProfile == null) {
            userProfile = new UserProfile(this);
        }
        String savedGoal = userProfile.getGoal();
        intent.putExtra("user_goal", savedGoal);
        // ----------------------------

        startActivity(intent);
    }


    /**
     * BMI描述
     */
    private String getBMIDescription(double bmi) {
        if (bmi < 18.5) return "体重偏轻";
        if (bmi < 24) return "正常范围";
        if (bmi < 28) return "超重";
        return "肥胖";
    }
}