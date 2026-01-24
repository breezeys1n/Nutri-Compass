package com.example.nutricompass;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvUserName;
    private TextView tvUserInfo;
    private Button btnEditProfile;
    private Button btnLogout;

    private UserProfile userProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        BackButtonUtil.setupBackButton(this);

        userProfile = new UserProfile(this);

        // 初始化控件
        tvUserName = findViewById(R.id.tv_user_name);
        tvUserInfo = findViewById(R.id.tv_user_info);
        btnEditProfile = findViewById(R.id.btn_edit_profile);
        btnLogout = findViewById(R.id.btn_logout);

        // 显示用户信息
        displayUserInfo();

        // 编辑资料按钮点击事件
        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());

        // 退出登录按钮点击事件
        btnLogout.setOnClickListener(v -> logout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回页面时刷新信息
        displayUserInfo();
    }

    /**
     * 显示用户信息
     */
    private void displayUserInfo() {
        if (userProfile.isProfileComplete()) {
            // 显示完整信息
            String info = String.format(
                    "身高: %s cm\n" +
                            "体重: %s kg\n" +
                            "性别: %s\n" +
                            "年龄: %s 岁\n" +
                            "目标: %s\n" +
                            "BMI: %.1f (%s)",
                    userProfile.getHeight(),
                    userProfile.getWeight(),
                    userProfile.getGender(),
                    userProfile.getAge(),
                    userProfile.getGoal(),
                    userProfile.calculateBMI(),
                    userProfile.getBmiDescription()
            );

            tvUserName.setText("健康食配用户");
            tvUserInfo.setText(info);
        } else {
            tvUserName.setText("未完善资料");
            tvUserInfo.setText("请完善您的个人信息");
        }
    }

    /**
     * 显示编辑资料的对话框
     */
    private void showEditProfileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("编辑个人信息");

        // 使用布局文件创建对话框视图
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
        builder.setView(dialogView);

        // 初始化对话框中的控件
        TextInputEditText etHeight = dialogView.findViewById(R.id.dialog_et_height);
        TextInputEditText etWeight = dialogView.findViewById(R.id.dialog_et_weight);
        TextInputEditText etGender = dialogView.findViewById(R.id.dialog_et_gender);
        TextInputEditText etAge = dialogView.findViewById(R.id.dialog_et_age);
        TextInputEditText etGoal = dialogView.findViewById(R.id.dialog_et_goal);
        TextInputEditText etOccupation = dialogView.findViewById(R.id.dialog_et_occupation);

        // 填充现有数据
        etHeight.setText(userProfile.getHeight());
        etWeight.setText(userProfile.getWeight());
        etGender.setText(userProfile.getGender());
        etAge.setText(userProfile.getAge());
        etGoal.setText(userProfile.getGoal());
        etOccupation.setText(userProfile.getOccupation());

        // 设置性别选择点击事件
        etGender.setOnClickListener(v -> showGenderSelectionDialog(etGender));

        // 设置目标选择点击事件
        etGoal.setOnClickListener(v -> showGoalSelectionDialog(etGoal));

        builder.setPositiveButton("保存", (dialog, which) -> {
            // 获取输入的数据
            String height = etHeight.getText().toString();
            String weight = etWeight.getText().toString();
            String gender = etGender.getText().toString();
            String age = etAge.getText().toString();
            String goal = etGoal.getText().toString();
            String occupation = etOccupation.getText().toString();

            // 验证必填项
            if (height.isEmpty() || weight.isEmpty() || gender.isEmpty() || age.isEmpty() || goal.isEmpty()) {
                Toast.makeText(this, "请填写所有必填项", Toast.LENGTH_SHORT).show();
                return;
            }

            // 更新用户信息
            userProfile.updateProfile(height, weight, gender, age, goal, occupation);

            // 显示更新成功
            double bmi = userProfile.calculateBMI();
            Toast.makeText(this,
                    String.format("信息已更新！BMI: %.1f (%s)", bmi, userProfile.getBmiDescription()),
                    Toast.LENGTH_SHORT).show();

            // 刷新显示
            displayUserInfo();
        });

        builder.setNegativeButton("取消", null);

        builder.show();
    }

    /**
     * 显示性别选择对话框
     */
    private void showGenderSelectionDialog(TextInputEditText targetEditText) {
        String[] genders = {"男", "女"};
        new AlertDialog.Builder(this)
                .setTitle("选择性别")
                .setItems(genders, (dialog, which) -> {
                    targetEditText.setText(genders[which]);
                })
                .show();
    }

    /**
     * 显示目标选择对话框
     */
    private void showGoalSelectionDialog(TextInputEditText targetEditText) {
        String[] goals = {"减脂", "增肌", "维持体重", "改善饮食结构", "提高运动表现"};
        new AlertDialog.Builder(this)
                .setTitle("选择健康目标")
                .setItems(goals, (dialog, which) -> {
                    targetEditText.setText(goals[which]);
                })
                .show();
    }

    /**
     * 退出登录/返回首页
     */
    private void logout() {
        // 这里可以添加退出登录逻辑，如清除登录状态等
        // 目前只是返回首页
        finish();
    }
}