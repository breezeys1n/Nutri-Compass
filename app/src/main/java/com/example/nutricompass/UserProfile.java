package com.example.nutricompass;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 用户个人信息管理类
 * 用于存储和获取用户的个人信息
 */
public class UserProfile {

    private static final String PREFS_NAME = "user_profile";
    private static final String KEY_HEIGHT = "height";
    private static final String KEY_WEIGHT = "weight";
    private static final String KEY_GOAL = "goal";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_AGE = "age";
    private static final String KEY_OCCUPATION = "occupation";

    private Context context;
    private SharedPreferences prefs;

    public UserProfile(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 保存用户基本信息
     */
    public void saveBasicInfo(String height, String weight, String goal) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_HEIGHT, height);
        editor.putString(KEY_WEIGHT, weight);
        editor.putString(KEY_GOAL, goal);
        editor.apply();
    }

    /**
     * 获取身高
     */
    public String getHeight() {
        return prefs.getString(KEY_HEIGHT, "");
    }

    /**
     * 获取体重
     */
    public String getWeight() {
        return prefs.getString(KEY_WEIGHT, "");
    }

    /**
     * 获取目标
     */
    public String getGoal() {
        return prefs.getString(KEY_GOAL, "");
    }

    /**
     * 保存完整用户信息（后续版本使用）
     */
    public void saveFullInfo(String height, String weight, String goal,
                             String gender, String age, String occupation) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_HEIGHT, height);
        editor.putString(KEY_WEIGHT, weight);
        editor.putString(KEY_GOAL, goal);
        editor.putString(KEY_GENDER, gender);
        editor.putString(KEY_AGE, age);
        editor.putString(KEY_OCCUPATION, occupation);
        editor.apply();
    }

    /**
     * 计算BMI
     */
    public double calculateBMI() {
        try {
            double height = Double.parseDouble(getHeight());
            double weight = Double.parseDouble(getWeight());

            if (height > 0 && weight > 0) {
                // 转换为米
                height = height / 100;
                return weight / (height * height);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 判断用户信息是否完整
     */
    public boolean isProfileComplete() {
        return !getHeight().isEmpty() &&
                !getWeight().isEmpty() &&
                !getGoal().isEmpty();
    }

    /**
     * 清除所有用户信息
     */
    public void clearProfile() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
    }
}