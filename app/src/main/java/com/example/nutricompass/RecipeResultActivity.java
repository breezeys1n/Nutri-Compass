package com.example.nutricompass;

import android.nfc.Tag;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecipeResultActivity extends AppCompatActivity implements SpeechService.SpeechCallback{
    private static final String TAG = "RecipeResultActivity";
    private TextView tvRecipeName, tvRecipeDescription, tvRecipeReason, tvWeatherInfo;
    private TextView tvRecipeNutrition, tvCookingTips, tvUserData;
    private TextView tvPrepTimeValue, tvCookTimeValue, tvDifficultyValue;
    private LinearLayout layoutIngredientsContainer, layoutStepsContainer;
    private Button btnNextStep;
    private UserProfile userProfile;
    private DecimalFormat decimalFormat = new DecimalFormat("#.##");
    private ImageButton btnVoiceControl;
    private SpeechService speechService;
    private List<String> cookingStepsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_result);

        BackButtonUtil.setupBackButton(this);
        userProfile = new UserProfile(this);
        initViews();
        displayUserInfo();
        checkTTSAvailability();
        initSpeechService();
        Recipe recipe = (Recipe) getIntent().getSerializableExtra("recipe");
        if (recipe != null) {
            displayRecipeFromObject(recipe);
        } else {
            displayRecipeInfo();
        }

        setupButtonListeners();
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
        btnVoiceControl = findViewById(R.id.btn_voice_control);
        if (btnVoiceControl != null && speechService != null) {
            speechService.setVoiceButton(btnVoiceControl);
        }
    }
    private void initSpeechService() {
        speechService = SpeechService.getInstance(this);
        speechService.setCallback(this);
    }
    private void displayUserInfo() {
        StringBuilder userInfo = new StringBuilder("为您定制 | 目标: ");
        userInfo.append(userProfile.getGoal());
        if (userProfile.isProfileComplete()) {
            userInfo.append(" | BMI: ").append(decimalFormat.format(userProfile.calculateBMI()));
        }
        tvUserData.setText(userInfo.toString());
    }

    private void displayRecipeInfo() {
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

        tvRecipeName.setText(recipeName != null ? recipeName : "智能食谱");
        tvRecipeDescription.setText(recipeDescription != null ? recipeDescription : "");
        tvRecipeReason.setText(recipeReason != null ? recipeReason : "");
        tvWeatherInfo.setText(weatherInfo != null ? "🌤️ " + weatherInfo : "🌤️ 天气加载中...");
        tvRecipeNutrition.setText(recipeNutrition != null ? recipeNutrition : "计算中...");
        tvPrepTimeValue.setText(prepTime != null ? prepTime : "10分钟");
        tvCookTimeValue.setText(cookTime != null ? cookTime : "15分钟");
        tvDifficultyValue.setText(difficulty != null ? difficulty : "中等");

        displayIngredients(ingredients);
        displayCookingSteps(steps);

        saveRecipeToHistory(recipeName, recipeDescription, recipeReason,
                recipeNutrition, ingredients, steps, prepTime,
                cookTime, difficulty, cookingTips);
    }

    private void displayRecipeFromObject(Recipe recipe) {
        tvRecipeName.setText(recipe.getTitle());
        tvRecipeDescription.setText(recipe.getDescription());
        tvRecipeReason.setText(recipe.getReason());
        tvWeatherInfo.setText("🌤️ " + (recipe.getWeatherCondition() != null ? recipe.getWeatherCondition() : ""));

        if (recipe.getNutrition() != null && recipe.getNutrition().getCalories() > 0) {
            NutritionInfo n = recipe.getNutrition();
            // 严谨显示所有数值
            tvRecipeNutrition.setText(String.format(Locale.CHINA, "热量: %.0f大卡 | 蛋白质: %.1fg | 碳水: %.1fg | 脂肪: %.1fg",
                    n.getCalories(), n.getProtein(), n.getCarbs(), n.getFat()));
        } else {
            tvRecipeNutrition.setText(recipe.getBriefNutrition());
        }

        tvPrepTimeValue.setText(recipe.getPreparationTime());
        tvCookTimeValue.setText(recipe.getCookingTime());
        tvDifficultyValue.setText(convertDifficultyToString(recipe.getDifficulty()));

        if (recipe.getIngredients() != null) displayIngredients(recipe.getIngredients().toArray(new String[0]));
        if (recipe.getCookingSteps() != null) displayCookingSteps(recipe.getCookingSteps().toArray(new String[0]));
        if (recipe.getCookingSteps() != null) {
            cookingStepsList.clear();
            cookingStepsList.addAll(recipe.getCookingSteps());
            displayCookingSteps(recipe.getCookingSteps().toArray(new String[0]));

            // 设置到语音服务
            if (speechService != null) {
                speechService.setCookingSteps(cookingStepsList);
            }
        }
    }

    private void saveRecipeToHistory(String name, String desc, String reason, String nutritionStr,
                                     String[] ingredients, String[] steps, String prep, String cook, String diff, String tips) {
        Recipe recipe = new Recipe();
        recipe.setTitle(name);
        recipe.setDescription(desc);
        recipe.setReason(reason);
        recipe.setPreparationTime(prep);
        recipe.setCookingTime(cook);

        if (ingredients != null) for (String s : ingredients) recipe.addIngredient(s);
        if (steps != null) for (String s : steps) recipe.addCookingStep(s);

        if (nutritionStr != null) {
            NutritionInfo info = new NutritionInfo();
            try {
                // 将常见的特殊符号替换，统一分隔符
                String cleanStr = nutritionStr.replace("：", ":").replace("克", "g");
                String[] parts = cleanStr.split("\\|");
                for (String part : parts) {
                    // 正则提取数字
                    String numStr = part.replaceAll("[^0-9.]", "");
                    if (numStr.isEmpty()) continue;
                    double val = Double.parseDouble(numStr);

                    if (part.contains("热量") || part.contains("大卡") || part.contains("Calories")) {
                        info.setCalories(val);
                        recipe.setCalories((int)val);
                    } else if (part.contains("蛋白质") || part.contains("蛋白") || part.contains("Protein")) {
                        info.setProtein(val);
                    } else if (part.contains("碳水") || part.contains("Carbs")) {
                        info.setCarbs(val);
                    } else if (part.contains("脂肪") || part.contains("Fat")) {
                        info.setFat(val);
                    }
                }
                recipe.setNutrition(info);
            } catch (Exception e) { e.printStackTrace(); }
        }
        new RecipeDatabase(this).addRecipe(recipe);
    }

    private void displayIngredients(String[] ingredients) {
        layoutIngredientsContainer.removeAllViews();
        if (ingredients == null) return;
        for (String s : ingredients) {
            View v = LayoutInflater.from(this).inflate(R.layout.item_ingredient, layoutIngredientsContainer, false);
            ((TextView) v.findViewById(R.id.tv_ingredient)).setText("• " + s);
            layoutIngredientsContainer.addView(v);
        }
    }

    private void displayCookingSteps(String[] steps) {
        layoutStepsContainer.removeAllViews();
        cookingStepsList.clear();

        if (steps == null) return;

        for (int i = 0; i < steps.length; i++) {
            cookingStepsList.add(steps[i]);

            View v = LayoutInflater.from(this).inflate(R.layout.item_cooking_step, layoutStepsContainer, false);
            TextView tvStepNumber = v.findViewById(R.id.tv_step_number);
            TextView tvStepDescription = v.findViewById(R.id.tv_step_description);

            tvStepNumber.setText(String.valueOf(i + 1));
            tvStepDescription.setText(steps[i]);

            // 高亮当前朗读的步骤
            final int stepIndex = i;
            v.setTag(stepIndex);

            layoutStepsContainer.addView(v);
        }

        // 设置烹饪步骤到语音服务
        if (speechService != null) {
            speechService.setCookingSteps(cookingStepsList);
        }
    }

    private String convertDifficultyToString(int d) {
        if (d == 1) return "简单";
        if (d == 3) return "复杂";
        return "中等";
    }

    private void setupButtonListeners() {
        btnNextStep.setOnClickListener(v -> tvCookingTips.setText("💡 烹饪指导功能正在开发中！"));
        // 语音按钮监听
        if (btnVoiceControl != null) {
            btnVoiceControl.setOnClickListener(v -> {
                if (cookingStepsList.isEmpty()) {
                    Toast.makeText(this, "暂无烹饪步骤", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (speechService.isSpeaking()) {
                    speechService.pauseOrResume();
                } else {
                    speechService.startSpeakingSteps();
                }
            });
        }
    }
    @Override
    public void onSpeechStart(int stepIndex) {
        runOnUiThread(() -> {
            highlightCurrentStep(stepIndex);
            Toast.makeText(this, "开始朗读第" + (stepIndex + 1) + "步", Toast.LENGTH_SHORT).show();
        });
    }
    @Override
    public void onSpeechDone(int stepIndex) {
        runOnUiThread(() -> {
            if (stepIndex >= 0) {
                // 单个步骤完成
                clearStepHighlight(stepIndex);
            } else {
                // 所有步骤完成
                Toast.makeText(this, "烹饪步骤朗读完成", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onSpeechError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "语音朗读出错: " + error, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onSpeechStopped() {
        runOnUiThread(() -> {
            // 清除所有高亮
            for (int i = 0; i < layoutStepsContainer.getChildCount(); i++) {
                View stepView = layoutStepsContainer.getChildAt(i);
                if (stepView != null) {
                    stepView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
                }
            }
            Toast.makeText(this, "朗读已停止", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 高亮显示当前朗读的步骤
     */
    private void highlightCurrentStep(int stepIndex) {
        // 先清除所有高亮
        for (int i = 0; i < layoutStepsContainer.getChildCount(); i++) {
            View stepView = layoutStepsContainer.getChildAt(i);
            if (stepView != null) {
                stepView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            }
        }

        // 高亮当前步骤
        if (stepIndex >= 0 && stepIndex < layoutStepsContainer.getChildCount()) {
            View currentStepView = layoutStepsContainer.getChildAt(stepIndex);
            if (currentStepView != null) {
                currentStepView.setBackgroundColor(ContextCompat.getColor(this, R.color.step_highlight));
            }
        }
    }

    private void clearStepHighlight(int stepIndex) {
        if (stepIndex >= 0 && stepIndex < layoutStepsContainer.getChildCount()) {
            View stepView = layoutStepsContainer.getChildAt(stepIndex);
            if (stepView != null) {
                stepView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            }
        }
    }
    private void checkTTSAvailability() {
        final TextToSpeech[] ttsHolder = new TextToSpeech[1];

        ttsHolder[0] = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "设备支持TTS");

                // 检查中文支持
                int langResult = ttsHolder[0].setLanguage(Locale.CHINA);
                switch (langResult) {
                    case TextToSpeech.LANG_AVAILABLE:
                        Log.d(TAG, "中文语音可用");
                        break;
                    case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                        Log.d(TAG, "中文（地区）语音可用");
                        break;
                    case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                        Log.d(TAG, "中文（地区变体）语音可用");
                        break;
                    case TextToSpeech.LANG_MISSING_DATA:
                        Log.e(TAG, "缺少中文语音数据");
                        Toast.makeText(this,
                                "缺少中文语音数据，请到设置中安装语音包",
                                Toast.LENGTH_LONG).show();
                        break;
                    case TextToSpeech.LANG_NOT_SUPPORTED:
                        Log.e(TAG, "不支持中文语音");
                        Toast.makeText(this,
                                "设备不支持中文语音",
                                Toast.LENGTH_LONG).show();
                        break;
                }

                // 列出可用引擎
                List<TextToSpeech.EngineInfo> engines = ttsHolder[0].getEngines();
                Log.d(TAG, "可用的TTS引擎数量: " + engines.size());
                for (TextToSpeech.EngineInfo engine : engines) {
                    Log.d(TAG, "引擎: " + engine.name + ", 标签: " + engine.label);
                }

            } else {
                Log.e(TAG, "设备不支持TTS，状态码: " + status);
                Toast.makeText(this,
                        "设备不支持文本转语音功能",
                        Toast.LENGTH_LONG).show();
            }

            // 清理临时TTS实例
            ttsHolder[0].shutdown();
        });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止语音并释放资源
        if (speechService != null) {
            speechService.stopSpeaking();

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停语音朗读
        if (speechService != null && speechService.isSpeaking()) {
            speechService.stopSpeaking();
        }
    }
}