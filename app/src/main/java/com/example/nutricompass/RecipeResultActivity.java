package com.example.nutricompass;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class RecipeResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_result); // 对应第二步的布局文件

        // 1. 接收从CameraActivity传递过来的数据
        String recipeName = getIntent().getStringExtra("recipe_name");
        String recipeReason = getIntent().getStringExtra("recipe_reason");
        String recipeNutrition = getIntent().getStringExtra("recipe_nutrition");

        // 2. 初始化视图
        TextView tvRecipeName = findViewById(R.id.tv_recipe_name);
        TextView tvRecipeReason = findViewById(R.id.tv_recipe_reason);
        TextView tvRecipeNutrition = findViewById(R.id.tv_recipe_nutrition);
        TextView tvUserData = findViewById(R.id.tv_user_data); // 显示用户数据

        // 3. 显示数据
        tvRecipeName.setText(recipeName != null ? recipeName : "未获取到食谱");
        tvRecipeReason.setText(recipeReason != null ? recipeReason : "等待AI分析...");
        tvRecipeNutrition.setText(recipeNutrition != null ? "营养信息: " + recipeNutrition : "营养信息计算中...");

        // 4. （可选）显示传递过来的用户数据，让结果更个性化
        String userGoal = getIntent().getStringExtra("user_goal");
        String userHeight = getIntent().getStringExtra("user_height");
        String userWeight = getIntent().getStringExtra("user_weight");

        if (userGoal != null) {
            String userInfo = "为您定制 | 目标: " + userGoal;
            if (userHeight != null && userWeight != null) {
                userInfo += " | 身高体重: " + userHeight + "cm / " + userWeight + "kg";
            }
            tvUserData.setText(userInfo);
        }
    }
}