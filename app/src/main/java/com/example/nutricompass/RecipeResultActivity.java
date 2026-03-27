package com.example.nutricompass;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecipeResultActivity extends AppCompatActivity implements SpeechService.SpeechCallback {
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
    private Button btnFlavorMigration;
    private Recipe currentRecipe;
    private FlavorMigrationClient flavorClient;

    // ==================== 新增：记录当前食谱的数据库ID ====================
    private int currentRecipeId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_result);

        flavorClient = new FlavorMigrationClient(this);

        BackButtonUtil.setupBackButton(this);
        userProfile = new UserProfile(this);
        initViews();

        Recipe recipe = (Recipe) getIntent().getSerializableExtra("recipe");
        if (recipe != null) {
            displayRecipeFromObject(recipe);
        } else {
            displayRecipeInfo();
        }

        displayUserInfo();
        checkTTSAvailability();
        initSpeechService();
        setupButtonListeners();

        btnFlavorMigration = findViewById(R.id.btn_flavor_migration);
        if (btnFlavorMigration != null) {
            btnFlavorMigration.setOnClickListener(v -> {
                showFlavorSelectionDialog();
            });
        }
    }

    private void showFlavorSelectionDialog() {
        String[] cuisines = {
                "川菜 (麻辣)", "粤菜 (鲜香)", "苏菜 (甜鲜)",
                "鲁菜 (咸鲜)", "湘菜 (香辣)", "徽菜 (重味)",
                "浙菜 (清淡)", "闽菜 (鲜香)"
        };

        new AlertDialog.Builder(this)
                .setTitle("选择目标风味")
                .setItems(cuisines, (dialog, which) -> {
                    String selectedCuisine = cuisines[which];
                    callFlavorMigration(selectedCuisine);
                })
                .show();
    }

    private void callFlavorMigration(String targetCuisine) {
        if (currentRecipe == null) {
            Toast.makeText(this, "当前食谱为空，无法进行风味迁移", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在将食谱改良为 " + targetCuisine + "...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        JSONObject recipeJson = convertRecipeToJson(currentRecipe);
        List<String> ingredients = currentRecipe.getIngredients();

        flavorClient.migrateRecipe(
                recipeJson,
                targetCuisine,
                getUserHealthGoal(),
                ingredients,
                new FlavorMigrationClient.MigrationCallback() {
                    @Override
                    public void onSuccess(JSONObject migratedRecipe) {
                        callOriginalModelWithMigrated(migratedRecipe, targetCuisine, progressDialog);
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(RecipeResultActivity.this,
                                    "风味迁移失败: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }

    private void callOriginalModelWithMigrated(JSONObject migratedRecipe, String cuisine, ProgressDialog progressDialog) {
        runOnUiThread(() -> {
            progressDialog.setMessage("大厨正在为您优化食谱...");
        });

        StringBuilder ingredientsStr = new StringBuilder();
        try {
            JSONArray ingredients = migratedRecipe.getJSONArray("ingredients");
            for (int i = 0; i < ingredients.length(); i++) {
                JSONObject ing = ingredients.getJSONObject(i);
                if (ingredientsStr.length() > 0) ingredientsStr.append("，");
                ingredientsStr.append(ing.getString("name"));
                if (ing.has("amount") && !ing.getString("amount").equals("适量")) {
                    ingredientsStr.append("(").append(ing.getString("amount")).append(")");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析食材失败", e);
        }

        UserProfile userProfile = new UserProfile(this);
        String userGoal = userProfile.getGoal();
        String userCondition = "";

        RecipeAnalyzer analyzer = new RecipeAnalyzer(this);

        analyzer.generateFromMigration(
                ingredientsStr.toString(),
                userGoal,
                userCondition,
                migratedRecipe,
                cuisine,
                new RecipeAnalyzer.GenerationCallback() {
                    @Override
                    public void onSuccess(Recipe finalRecipe) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();

                            // ==================== 替换原食谱 ====================
                            RecipeDatabase db = new RecipeDatabase(RecipeResultActivity.this);

                            if (currentRecipeId != -1) {
                                db.deleteRecipe(currentRecipeId);
                                Log.d(TAG, "已删除原食谱，ID: " + currentRecipeId);
                            }

                            long newId = db.addRecipe(finalRecipe);
                            if (newId != -1) {
                                finalRecipe.setId((int) newId);
                                currentRecipeId = (int) newId;
                                Log.d(TAG, "已保存新食谱，新ID: " + newId);
                            }
                            // ==================================================

                            displayNewRecipe(finalRecipe, cuisine);

                            Toast.makeText(RecipeResultActivity.this,
                                    "已生成 " + cuisine + " 风味的完整食谱！",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(RecipeResultActivity.this,
                                    "食谱优化失败: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }

    private void displayNewRecipe(Recipe newRecipe, String cuisine) {
        this.currentRecipe = newRecipe;
        displayRecipeFromObject(newRecipe);
        Toast.makeText(this, "已生成 " + cuisine + " 风味的新食谱！", Toast.LENGTH_SHORT).show();
    }

    private JSONObject convertRecipeToJson(Recipe recipe) {
        if (recipe == null) {
            Log.e(TAG, "convertRecipeToJson: recipe is null");
            return new JSONObject();
        }
        try {
            JSONObject json = new JSONObject();
            json.put("name", recipe.getName());
            json.put("description", recipe.getDescription());

            JSONArray ingredients = new JSONArray();
            if (recipe.getIngredients() != null) {
                for (String ing : recipe.getIngredients()) {
                    JSONObject ingObj = new JSONObject();
                    if (ing.contains("(") && ing.contains(")")) {
                        String[] parts = ing.split("\\(");
                        ingObj.put("name", parts[0].trim());
                        ingObj.put("amount", parts[1].replace(")", "").trim());
                    } else {
                        ingObj.put("name", ing);
                        ingObj.put("amount", "适量");
                    }
                    ingredients.put(ingObj);
                }
            }
            json.put("ingredients", ingredients);

            JSONArray steps = new JSONArray();
            if (recipe.getCookingSteps() != null) {
                for (String step : recipe.getCookingSteps()) {
                    steps.put(step);
                }
            }
            json.put("steps", steps);

            JSONObject nutrition = new JSONObject();
            if (recipe.getNutrition() != null) {
                nutrition.put("calories", recipe.getNutrition().getCalories());
                nutrition.put("protein", recipe.getNutrition().getProtein());
                nutrition.put("carbs", recipe.getNutrition().getCarbs());
                nutrition.put("fat", recipe.getNutrition().getFat());
            }
            json.put("nutrition", nutrition);

            return json;
        } catch (Exception e) {
            Log.e(TAG, "转换Recipe失败", e);
            return new JSONObject();
        }
    }

    private Recipe convertJsonToRecipe(JSONObject json) {
        Recipe recipe = new Recipe();
        try {
            recipe.setName(json.optString("name", "风味改良食谱"));
            recipe.setDescription(json.optString("description", ""));

            JSONArray ingredients = json.optJSONArray("ingredients");
            if (ingredients != null) {
                for (int i = 0; i < ingredients.length(); i++) {
                    JSONObject ing = ingredients.getJSONObject(i);
                    String name = ing.optString("name");
                    String amount = ing.optString("amount", "适量");
                    recipe.addIngredient(name + (amount != null ? " (" + amount + ")" : ""));
                }
            }

            JSONArray steps = json.optJSONArray("steps");
            if (steps != null) {
                for (int i = 0; i < steps.length(); i++) {
                    recipe.addCookingStep(steps.getString(i));
                }
            }

            JSONObject nutrition = json.optJSONObject("nutrition");
            if (nutrition != null) {
                NutritionInfo ni = new NutritionInfo();
                ni.setCalories(nutrition.optDouble("calories", 0));
                ni.setProtein(nutrition.optDouble("protein", 0));
                ni.setCarbs(nutrition.optDouble("carbs", 0));
                ni.setFat(nutrition.optDouble("fat", 0));
                recipe.setNutrition(ni);
            }

            recipe.setReason("基于 " + json.optString("name", "原食谱") + " 的风味改良版本");
            recipe.setPreparationTime("约20分钟");
            recipe.setCookingTime("约25分钟");
            recipe.setDifficulty(2);

        } catch (Exception e) {
            Log.e(TAG, "转换JSON失败", e);
        }
        return recipe;
    }

    private String getUserHealthGoal() {
        return new UserProfile(this).getGoal();
    }

    private void saveRecipeToHistory(Recipe recipe) {
        new RecipeDatabase(this).addRecipe(recipe);
    }

    // ==================== 新增：保存前检查重复 ====================
    private void saveRecipeToHistoryIfNotExists(Recipe recipe) {
        RecipeDatabase db = new RecipeDatabase(this);
        List<Recipe> existingRecipes = db.getAllRecipes();

        boolean exists = false;
        for (Recipe existing : existingRecipes) {
            if (existing.getTitle() != null && existing.getTitle().equals(recipe.getTitle()) &&
                    existing.getDate() != null && existing.getDate().equals(recipe.getDate())) {
                exists = true;
                currentRecipeId = existing.getId();
                Log.d(TAG, "食谱已存在，ID: " + currentRecipeId + "，不重复保存");
                break;
            }
        }

        if (!exists) {
            long id = db.addRecipe(recipe);
            if (id != -1) {
                currentRecipeId = (int) id;
                Log.d(TAG, "保存新食谱，ID: " + id);
            }
        }
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

        Recipe recipe = new Recipe();
        recipe.setTitle(recipeName);
        recipe.setDescription(recipeDescription);
        recipe.setReason(recipeReason);
        recipe.setWeatherCondition(weatherInfo);
        recipe.setPreparationTime(prepTime);
        recipe.setCookingTime(cookTime);

        if (ingredients != null) {
            for (String s : ingredients) recipe.addIngredient(s);
        }
        if (steps != null) {
            for (String s : steps) recipe.addCookingStep(s);
        }

        this.currentRecipe = recipe;

        // ==================== 使用重复检查方法 ====================
        saveRecipeToHistoryIfNotExists(recipe);
    }

    private void displayRecipeFromObject(Recipe recipe) {
        this.currentRecipe = recipe;

        // ==================== 获取当前食谱的数据库ID ====================
        RecipeDatabase db = new RecipeDatabase(this);
        List<Recipe> allRecipes = db.getAllRecipes();
        for (Recipe r : allRecipes) {
            if (r.getTitle() != null && r.getTitle().equals(recipe.getTitle()) &&
                    r.getDate() != null && r.getDate().equals(recipe.getDate())) {
                currentRecipeId = r.getId();
                Log.d(TAG, "找到已存在的食谱，ID: " + currentRecipeId);
                break;
            }
        }

        tvRecipeName.setText(recipe.getTitle());
        tvRecipeDescription.setText(recipe.getDescription());
        tvRecipeReason.setText(recipe.getReason());
        tvWeatherInfo.setText("🌤️ " + (recipe.getWeatherCondition() != null ? recipe.getWeatherCondition() : ""));

        if (recipe.getNutrition() != null && recipe.getNutrition().getCalories() > 0) {
            NutritionInfo n = recipe.getNutrition();
            tvRecipeNutrition.setText(String.format(Locale.CHINA,
                    "热量: %.0f大卡 | 蛋白质: %.1fg | 碳水: %.1fg | 脂肪: %.1fg",
                    n.getCalories(), n.getProtein(), n.getCarbs(), n.getFat()));
        } else if (recipe.getCalories() > 0) {
            tvRecipeNutrition.setText(String.format(Locale.CHINA,
                    "热量: %d大卡 | 蛋白质: %.1fg | 碳水: %.1fg | 脂肪: %.1fg",
                    recipe.getCalories(), recipe.getProtein(), recipe.getCarbs(), recipe.getFat()));
        } else {
            tvRecipeNutrition.setText(recipe.getBriefNutrition());
        }

        tvPrepTimeValue.setText(recipe.getPreparationTime());
        tvCookTimeValue.setText(recipe.getCookingTime());
        tvDifficultyValue.setText(convertDifficultyToString(recipe.getDifficulty()));

        if (recipe.getIngredients() != null)
            displayIngredients(recipe.getIngredients().toArray(new String[0]));

        if (recipe.getCookingSteps() != null) {
            cookingStepsList.clear();
            cookingStepsList.addAll(recipe.getCookingSteps());
            displayCookingSteps(recipe.getCookingSteps().toArray(new String[0]));

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
                String cleanStr = nutritionStr.replace("：", ":").replace("克", "g");
                String[] parts = cleanStr.split("\\|");
                for (String part : parts) {
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
        if (steps == null) return;

        for (int i = 0; i < steps.length; i++) {
            View v = LayoutInflater.from(this).inflate(R.layout.item_cooking_step, layoutStepsContainer, false);
            TextView tvStepNumber = v.findViewById(R.id.tv_step_number);
            TextView tvStepDescription = v.findViewById(R.id.tv_step_description);

            tvStepNumber.setText(String.valueOf(i + 1));
            tvStepDescription.setText(steps[i]);

            v.setTag(i);
            layoutStepsContainer.addView(v);
        }
    }

    private String convertDifficultyToString(int d) {
        if (d == 1) return "简单";
        if (d == 3) return "复杂";
        return "中等";
    }

    private void setupButtonListeners() {
        btnNextStep.setOnClickListener(v -> {
            Log.d(TAG, "开始点击跳转按钮...");

            try {
                if (speechService != null) {
                    speechService.stopSpeaking();
                }

                Intent intent = new Intent(RecipeResultActivity.this, TalkWithAIActivity.class);

                String name = (tvRecipeName != null) ? tvRecipeName.getText().toString() : "未知食谱";
                intent.putExtra("recipe_name", name);

                if (cookingStepsList != null) {
                    intent.putStringArrayListExtra("recipe_steps", new ArrayList<>(cookingStepsList));
                } else {
                    intent.putStringArrayListExtra("recipe_steps", new ArrayList<>());
                }

                Log.d(TAG, "正在启动 TalkWithAIActivity...");
                startActivity(intent);

            } catch (Exception e) {
                Log.e(TAG, "跳转失败，原因: " + e.getMessage());
                e.printStackTrace();
                Toast.makeText(this, "页面跳转异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

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
                clearStepHighlight(stepIndex);
            } else {
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
            for (int i = 0; i < layoutStepsContainer.getChildCount(); i++) {
                View stepView = layoutStepsContainer.getChildAt(i);
                if (stepView != null) {
                    stepView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
                }
            }
            Toast.makeText(this, "朗读已停止", Toast.LENGTH_SHORT).show();
        });
    }

    private void highlightCurrentStep(int stepIndex) {
        for (int i = 0; i < layoutStepsContainer.getChildCount(); i++) {
            View stepView = layoutStepsContainer.getChildAt(i);
            if (stepView != null) {
                stepView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            }
        }

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
            try {
                if (status == TextToSpeech.SUCCESS) {
                    if (ttsHolder[0] != null) {
                        Locale locale = ttsHolder[0].getLanguage();
                        Log.d(TAG, "当前TTS语言: " + (locale != null ? locale.toString() : "未知"));

                        int result = ttsHolder[0].setLanguage(Locale.CHINESE);
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e(TAG, "设备不支持中文TTS或缺少语音包");
                            runOnUiThread(() -> Toast.makeText(this, "设备缺少中文语音包，请在系统设置中检查", Toast.LENGTH_LONG).show());
                        } else {
                            Log.d(TAG, "TTS 检查通过：支持中文");
                        }
                    }
                } else {
                    Log.e(TAG, "TTS 初始化失败，错误码: " + status);
                }
            } catch (Exception e) {
                Log.e(TAG, "检查TTS可用性时发生异常", e);
            } finally {
                if (ttsHolder[0] != null) {
                    try {
                        ttsHolder[0].stop();
                        ttsHolder[0].shutdown();
                        ttsHolder[0] = null;
                    } catch (Exception e) {
                        Log.e(TAG, "关闭临时TTS失败", e);
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechService != null) {
            speechService.stopSpeaking();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (speechService != null && speechService.isSpeaking()) {
            speechService.stopSpeaking();
        }
    }
}