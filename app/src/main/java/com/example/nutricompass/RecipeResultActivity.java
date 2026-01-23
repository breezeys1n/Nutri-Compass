package com.example.nutricompass;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.DecimalFormat;
import java.util.List;
public class RecipeResultActivity extends AppCompatActivity {

    private TextView tvRecipeName, tvRecipeDescription, tvRecipeReason, tvWeatherInfo;
    private TextView tvRecipeNutrition, tvCookingTips, tvUserData;
    private TextView tvPrepTimeValue, tvCookTimeValue, tvDifficultyValue;
    private LinearLayout layoutIngredientsContainer, layoutStepsContainer;
    private Button btnNextStep;
    private UserProfile userProfile;
    private DecimalFormat decimalFormat = new DecimalFormat("#.##");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_result);

        // 初始化用户信息
        userProfile = new UserProfile(this);

        // 初始化视图
        initViews();

        // 显示用户信息
        displayUserInfo();

        // 显示食谱信息
        Recipe recipe = (Recipe) getIntent().getSerializableExtra("recipe");
        if (recipe != null) {
            // 如果传递了完整的 Recipe 对象
            displayRecipeFromObject(recipe);
        } else {
            // 原来的逻辑，通过 Intent extras 获取数据
            displayRecipeInfo();
        }

        // 设置按钮点击事件
        setupButtonListeners();
    }
    /**
     * 从 Recipe 对象显示食谱信息
     */
    private void displayRecipeFromObject(Recipe recipe) {
        // 设置基本信息
        tvRecipeName.setText(recipe.getName() != null ? recipe.getName() : "智能食谱");
        tvRecipeDescription.setText(recipe.getDescription() != null ? recipe.getDescription() : "为您量身定制的健康食谱");
        tvRecipeReason.setText(recipe.getReason() != null ? recipe.getReason() : "根据您的健康目标和现有食材精心推荐");

        // 设置天气信息
        if (recipe.getWeatherCondition() != null && !recipe.getWeatherCondition().isEmpty()) {
            tvWeatherInfo.setText("🌤️ " + recipe.getWeatherCondition());
        } else {
            tvWeatherInfo.setText("🌤️ 天气信息获取中...");
        }

        // 设置营养信息
        if (recipe.getNutrition() != null) {
            tvRecipeNutrition.setText(recipe.getBriefNutrition());
        } else {
            // 从calories字段生成简要营养信息
            if (recipe.getCalories() > 0) {
                tvRecipeNutrition.setText(String.format("热量: %d大卡", recipe.getCalories()));
            } else {
                calculateAndDisplayDefaultNutrition();
            }
        }

        // 设置烹饪小贴士 - 如果有的话
        // tvCookingTips.setText("💡 " + recipe.getCookingTips()); // 如果需要可以添加这个字段

        // 设置时间信息
        tvPrepTimeValue.setText(recipe.getPreparationTime() != null ? recipe.getPreparationTime() : "10分钟");
        tvCookTimeValue.setText(recipe.getCookingTime() != null ? recipe.getCookingTime() : "15分钟");

        // 设置难度信息
        tvDifficultyValue.setText(convertDifficultyToString(recipe.getDifficulty()));

        // 显示食材列表
        List<String> ingredientsList = recipe.getIngredients();
        String[] ingredientsArray = ingredientsList.toArray(new String[0]);
        displayIngredients(ingredientsArray);

        // 显示烹饪步骤
        List<String> stepsList = recipe.getCookingSteps();
        String[] stepsArray = stepsList.toArray(new String[0]);
        displayCookingSteps(stepsArray);
    }
    private void initViews() {
        tvRecipeName = findViewById(R.id.tv_recipe_name);
        tvRecipeDescription = findViewById(R.id.tv_recipe_description);
        tvRecipeReason = findViewById(R.id.tv_recipe_reason);
        tvWeatherInfo = findViewById(R.id.tv_weather_info);
        tvRecipeNutrition = findViewById(R.id.tv_recipe_nutrition);
        tvCookingTips = findViewById(R.id.tv_cooking_tips);
        tvUserData = findViewById(R.id.tv_user_data);
        tvPrepTimeValue = findViewById(R.id.tv_prep_time_value);
        tvCookTimeValue = findViewById(R.id.tv_cook_time_value);
        tvDifficultyValue = findViewById(R.id.tv_difficulty_value);
        layoutIngredientsContainer = findViewById(R.id.layout_ingredients_container);
        layoutStepsContainer = findViewById(R.id.layout_steps_container);
        btnNextStep = findViewById(R.id.btn_next_step);
    }

    private void displayUserInfo() {
        StringBuilder userInfo = new StringBuilder("为您定制 | 目标: ");
        userInfo.append(userProfile.getGoal());

        if (userProfile.isProfileComplete()) {
            userInfo.append(" | BMI: ").append(decimalFormat.format(userProfile.calculateBMI()));
            userInfo.append(" (").append(getBMICategory(userProfile.calculateBMI())).append(")");
        }

        tvUserData.setText(userInfo.toString());
    }

    private String getBMICategory(double bmi) {
        if (bmi < 18.5) return "体重偏轻";
        else if (bmi < 24) return "正常范围";
        else if (bmi < 28) return "超重";
        else return "肥胖";
    }

    private void displayRecipeInfo() {
        // 获取传递的数据
        String recipeName = getIntent().getStringExtra("recipe_name");
        String recipeReason = getIntent().getStringExtra("recipe_reason");
        String recipeDescription = getIntent().getStringExtra("recipe_description");
        String recipeNutrition = getIntent().getStringExtra("recipe_nutrition");
        String weatherInfo = getIntent().getStringExtra("recipe_weather");
        String[] ingredients = getIntent().getStringArrayExtra("recipe_ingredients");
        String[] steps = getIntent().getStringArrayExtra("recipe_steps");
        String prepTime = getIntent().getStringExtra("recipe_prep_time");
        String cookTime = getIntent().getStringExtra("recipe_cook_time");
        String difficulty = getIntent().getStringExtra("recipe_difficulty");
        String cookingTips = getIntent().getStringExtra("recipe_cooking_tips");

        // 设置基本信息
        tvRecipeName.setText(recipeName != null ? recipeName : "智能食谱");
        tvRecipeDescription.setText(recipeDescription != null ? recipeDescription : "为您量身定制的健康食谱");
        tvRecipeReason.setText(recipeReason != null ? recipeReason : "根据您的健康目标和现有食材精心推荐");

        // 设置天气信息 - 这里添加带emoji的天气信息
        if (weatherInfo != null && !weatherInfo.isEmpty()) {
            tvWeatherInfo.setText("🌤️ " + weatherInfo);
        } else {
            tvWeatherInfo.setText("🌤️ 天气信息获取中...");
        }

        // 设置营养信息
        if (recipeNutrition != null && !recipeNutrition.isEmpty()) {
            tvRecipeNutrition.setText(recipeNutrition);
        } else {
            // 计算默认营养信息
            calculateAndDisplayDefaultNutrition();
        }

        // 设置烹饪小贴士
        if (cookingTips != null && !cookingTips.isEmpty()) {
            tvCookingTips.setText("💡 " + cookingTips);
        } else {
            tvCookingTips.setText("💡 小贴士：搭配一份绿叶蔬菜营养更均衡！");
        }

        // 设置时间信息
        tvPrepTimeValue.setText(prepTime != null ? prepTime : "10分钟");
        tvCookTimeValue.setText(cookTime != null ? cookTime : "15分钟");

        // 设置难度信息
        if (difficulty != null) {
            tvDifficultyValue.setText(difficulty);
        } else {
            // 根据数字转换为文字
            int difficultyNum = getIntent().getIntExtra("recipe_difficulty_num", 2);
            tvDifficultyValue.setText(convertDifficultyToString(difficultyNum));
        }

        // 显示食材列表
        displayIngredients(ingredients);

        // 显示烹饪步骤
        displayCookingSteps(steps);

        //保存历史信息
        saveRecipeToHistory(recipeName, recipeDescription, recipeReason,
                recipeNutrition, ingredients, steps, prepTime,
                cookTime, difficulty, cookingTips);
    }
    private void saveRecipeToHistory(String recipeName, String recipeDescription,
                                     String recipeReason, String recipeNutrition,
                                     String[] ingredients, String[] steps,
                                     String prepTime, String cookTime,
                                     String difficulty, String cookingTips) {

        // 创建 Recipe 对象
        Recipe recipe = new Recipe();
        recipe.setTitle(recipeName != null ? recipeName : "智能食谱");
        recipe.setDescription(recipeDescription != null ? recipeDescription : "为您量身定制的健康食谱");
        recipe.setReason(recipeReason != null ? recipeReason : "根据您的健康目标和现有食材精心推荐");

        // 设置其他信息
        if (ingredients != null) {
            for (String ingredient : ingredients) {
                recipe.addIngredient(ingredient);
            }
        }

        if (steps != null) {
            for (String step : steps) {
                recipe.addCookingStep(step);
            }
        }
        recipe.setPreparationTime(prepTime);
        recipe.setCookingTime(cookTime);

        // 转换难度字符串为数字
        if (difficulty != null) {
            if (difficulty.contains("简单")) recipe.setDifficulty(1);
            else if (difficulty.contains("复杂")) recipe.setDifficulty(3);
            else recipe.setDifficulty(2);
        }
        // 设置营养信息
        if (recipeNutrition != null && recipeNutrition.contains("大卡")) {
            try {
                // 从营养信息中提取热量值
                String[] parts = recipeNutrition.split("\\|");
                for (String part : parts) {
                    if (part.contains("大卡") || part.contains("热量")) {
                        String calStr = part.replaceAll("[^0-9]", "");
                        if (!calStr.isEmpty()) {
                            recipe.setCalories(Integer.parseInt(calStr));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 如果没有提取到热量，使用默认值
        if (recipe.getCalories() == 0) {
            recipe.setCalories(300); // 默认值
        }

        // 保存到数据库
        RecipeDatabase database = new RecipeDatabase(this);
        long id = database.addRecipe(recipe);

        if (id != -1) {
            // 可以在这里显示保存成功的提示，或者静默保存
            // Toast.makeText(this, "食谱已保存到历史记录", Toast.LENGTH_SHORT).show();
        }
    }
    private void calculateAndDisplayDefaultNutrition() {
        // 根据用户目标计算默认营养值
        String goal = userProfile.getGoal();
        String defaultNutrition;

        switch (goal) {
            case "科学减脂":
                defaultNutrition = "热量: 280大卡 | 蛋白质: 20g | 碳水: 25g | 脂肪: 12g";
                break;
            case "增肌塑形":
                defaultNutrition = "热量: 380大卡 | 蛋白质: 30g | 碳水: 35g | 脂肪: 15g";
                break;
            case "控糖饮食":
                defaultNutrition = "热量: 250大卡 | 蛋白质: 18g | 碳水: 15g | 脂肪: 10g";
                break;
            case "清淡调理":
                defaultNutrition = "热量: 220大卡 | 蛋白质: 15g | 碳水: 20g | 脂肪: 8g";
                break;
            default:
                defaultNutrition = "热量: 300大卡 | 蛋白质: 22g | 碳水: 28g | 脂肪: 14g";
        }

        tvRecipeNutrition.setText(defaultNutrition);
    }

    private String convertDifficultyToString(int difficulty) {
        switch (difficulty) {
            case 1: return "简单";
            case 2: return "中等";
            case 3: return "复杂";
            default: return "中等";
        }
    }

    private void displayIngredients(String[] ingredients) {
        // 清空容器
        layoutIngredientsContainer.removeAllViews();

        if (ingredients != null && ingredients.length > 0) {
            // 移除占位符
            TextView placeholder = findViewById(R.id.tv_ingredients_placeholder);
            if (placeholder != null) {
                layoutIngredientsContainer.removeView(placeholder);
            }

            // 动态添加食材项
            for (String ingredient : ingredients) {
                View ingredientItem = LayoutInflater.from(this)
                        .inflate(R.layout.item_ingredient, layoutIngredientsContainer, false);

                TextView tvIngredient = ingredientItem.findViewById(R.id.tv_ingredient);
                tvIngredient.setText("• " + ingredient);

                layoutIngredientsContainer.addView(ingredientItem);
            }
        } else {
            // 显示默认食材
            TextView tvDefault = new TextView(this);
            tvDefault.setText("• 番茄 2个\n• 鸡蛋 3个\n• 橄榄油 10毫升\n• 盐 3克");
            tvDefault.setTextSize(16);
            tvDefault.setTextColor(getResources().getColor(android.R.color.darker_gray));
            tvDefault.setLineSpacing(8, 1);
            layoutIngredientsContainer.addView(tvDefault);
        }
    }

    private void displayCookingSteps(String[] steps) {
        // 清空容器
        layoutStepsContainer.removeAllViews();

        if (steps != null && steps.length > 0) {
            // 移除占位符
            TextView placeholder = findViewById(R.id.tv_steps_placeholder);
            if (placeholder != null) {
                layoutStepsContainer.removeView(placeholder);
            }

            // 动态添加步骤项
            for (int i = 0; i < steps.length; i++) {
                View stepItem = LayoutInflater.from(this)
                        .inflate(R.layout.item_cooking_step, layoutStepsContainer, false);

                TextView tvStepNumber = stepItem.findViewById(R.id.tv_step_number);
                TextView tvStepDescription = stepItem.findViewById(R.id.tv_step_description);

                tvStepNumber.setText(String.valueOf(i + 1));
                tvStepDescription.setText(steps[i]);

                layoutStepsContainer.addView(stepItem);
            }
        } else {
            // 显示默认步骤
            TextView tvDefault = new TextView(this);
            tvDefault.setText("1. 番茄洗净切块，鸡蛋打散备用\n" +
                    "2. 热锅加入少量橄榄油，倒入蛋液翻炒\n" +
                    "3. 加入番茄块翻炒出汁\n" +
                    "4. 加入适量盐调味，翻炒均匀");
            tvDefault.setTextSize(16);
            tvDefault.setTextColor(getResources().getColor(android.R.color.darker_gray));
            tvDefault.setLineSpacing(8, 1);
            layoutStepsContainer.addView(tvDefault);
        }
    }

    private void setupButtonListeners() {
        btnNextStep.setOnClickListener(v -> {
            // TODO: 跳转到烹饪指导页面
            // Intent intent = new Intent(RecipeResultActivity.this, CookingGuideActivity.class);
            // startActivity(intent);

            // 暂时显示提示
            tvCookingTips.setText("💡 烹饪指导功能正在开发中，即将上线！");
        });
    }
}