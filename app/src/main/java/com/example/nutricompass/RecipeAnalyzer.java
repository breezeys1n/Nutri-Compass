// RecipeAnalyzer.java
package com.example.nutricompass;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RecipeAnalyzer {
    private static final String TAG = "RecipeAnalyzer_Logic";
    private Context context;

    // 本地 Ollama 地址
    private static final String OLLAMA_URL = "http://10.138.79.96:11434/api/chat";
    private static final String CUSTOM_MODEL = "my_health_chef";

    // 新增：RAG 客户端
    private RecipeRetrievalClient retrievalClient;

    public RecipeAnalyzer(Context context) {
        this.context = context;
        // 初始化 RAG 客户端
        this.retrievalClient = new RecipeRetrievalClient();
    }

    public Recipe analyzeRecipe(String imageBase64, String userGoal, String userCondition) {
        try {
            DoubaoImageRecognizer doubao = new DoubaoImageRecognizer();
            String detectedIngredients = doubao.recognizeFood(imageBase64);
            Log.d(TAG, "豆包识别结果: " + detectedIngredients);

            return analyzeWithLocalIngredients(detectedIngredients, userGoal, userCondition);
        } catch (Exception e) {
            Log.e(TAG, "视觉分析链路失败", e);
            return createErrorRecipe("图片识别失败: " + e.getMessage());
        }
    }

    public Recipe analyzeWithLocalIngredients(String detectedIngredients, String userGoal, String userCondition) {
        try {
            UserProfile userProfile = new UserProfile(context);
            double bmiValue = userProfile.calculateBMI();

            // 1. 获取天气
            String coords = LocationHelper.getCoordinates(context);
            String weatherRaw = WeatherProvider.fetchWeather(coords);
            String weatherInfo = parseWeatherToText(weatherRaw);

            // ===== 新增：RAG 检索参考食谱 =====
            List<String> ingredientsList = parseIngredientsToList(detectedIngredients);
            StringBuilder ragReference = new StringBuilder();

            if (!ingredientsList.isEmpty()) {
                final List<JSONObject>[] referenceRecipes = new List[]{new ArrayList<>()};
                final boolean[] retrievalDone = {false};
                CountDownLatch latch = new CountDownLatch(1);

                retrievalClient.searchRawRecipes(ingredientsList, userGoal, "", 5,
                        new RecipeRetrievalClient.RawJsonCallback() {
                            @Override
                            public void onSuccess(List<JSONObject> recipes) {
                                try {
                                    // 按质量分数排序筛选前2个
                                    List<JSONObject> filtered = new ArrayList<>();

                                    Collections.sort(recipes, new Comparator<JSONObject>() {
                                        @Override
                                        public int compare(JSONObject a, JSONObject b) {
                                            double scoreA = a.optDouble("quality_score", 0);
                                            double scoreB = b.optDouble("quality_score", 0);
                                            return Double.compare(scoreB, scoreA);
                                        }
                                    });

                                    // 只取前2个最相关的食谱
                                    for (int i = 0; i < Math.min(2, recipes.size()); i++) {
                                        filtered.add(recipes.get(i));
                                    }

                                    referenceRecipes[0] = filtered;
                                } catch (Exception e) {
                                    Log.e(TAG, "处理食谱列表失败: " + e.getMessage());
                                }
                                retrievalDone[0] = true;
                                latch.countDown();
                                Log.d(TAG, "找到 " + recipes.size() + " 个参考食谱，选用前 " + referenceRecipes[0].size() + " 个");
                            }

                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "RAG检索失败: " + error);
                                retrievalDone[0] = true;
                                latch.countDown();
                            }
                        }
                );

                latch.await(1500, TimeUnit.MILLISECONDS);

                // 如果有结果，添加完整食谱内容
                if (retrievalDone[0] && !referenceRecipes[0].isEmpty()) {
                    ragReference.append("\n\n【参考食谱（供你借鉴烹饪方法和搭配思路）】");

                    for (int idx = 0; idx < referenceRecipes[0].size(); idx++) {
                        JSONObject ref = referenceRecipes[0].get(idx);

                        ragReference.append("\n\n--- 参考食谱 ").append(idx + 1).append(" ---\n");

                        // 食谱名称（注意：这里是name不是title）
                        if (ref.has("name")) {
                            ragReference.append("菜名：").append(ref.optString("name")).append("\n");
                        }

                        // 菜系
                        if (ref.has("cuisine")) {
                            ragReference.append("菜系：").append(ref.optString("cuisine")).append("\n");
                        }

                        // 食材列表
                        if (ref.has("ingredients")) {
                            ragReference.append("食材：\n");
                            JSONArray ingredients = ref.getJSONArray("ingredients");
                            for (int i = 0; i < ingredients.length(); i++) {
                                JSONObject ing = ingredients.getJSONObject(i);
                                String name = ing.optString("name", "");
                                String amount = ing.optString("amount", "适量");
                                ragReference.append("  - ").append(name).append(" ").append(amount).append("\n");
                            }
                        }

                        // 烹饪步骤
                        if (ref.has("steps")) {
                            ragReference.append("步骤：\n");
                            JSONArray steps = ref.getJSONArray("steps");
                            for (int i = 0; i < steps.length(); i++) {
                                ragReference.append("  ").append(i + 1).append(". ").append(steps.getString(i)).append("\n");
                            }
                        }
                    }
                    ragReference.append("\n【以上是参考食谱，请借鉴其烹饪方法和搭配思路】\n");
                }
            }
            // ===== RAG 检索结束 =====

            // 2. 构建输入数据
            String userPrompt;
            if (ragReference.length() > 0) {
                // 如果有参考食谱，把完整的食谱内容传过去
                userPrompt = String.format(
                        "【我的 BMI】: %s\n" +
                                "【我的目标】：%s\n" +
                                "【身体状态】：%s\n" +
                                "【当前天气】：%s\n" +
                                "【现有食材】：%s\n" +
                                "%s\n" +
                                "请根据以上信息，为我生成一道创意食谱。可以参考参考食谱的烹饪方法，但要结合我的现有食材进行创新。",
                        bmiValue, userGoal, userCondition, weatherInfo, detectedIngredients, ragReference.toString()
                );
            } else {
                // 如果没有参考食谱，和原来一样
                userPrompt = String.format(
                        "【我的 BMI】: %s\n" +
                                "【我的目标】：%s\n" +
                                "【身体状态】：%s\n" +
                                "【当前天气】：%s\n" +
                                "【现有食材】：%s\n" +
                                "\n请根据以上信息，为我生成一道创意食谱。",
                        bmiValue, userGoal, userCondition, weatherInfo, detectedIngredients
                );
            }

            // 3. 构建请求体（完全不变）
            JSONObject root = new JSONObject();
            root.put("model", CUSTOM_MODEL);
            root.put("stream", false);

            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);
            messages.put(userMessage);
            root.put("messages", messages);

            Log.d(TAG, "正在请求 Ollama (模型: " + CUSTOM_MODEL + ")...");
            Log.d(TAG, "最终 Prompt: " + userPrompt);

            // 4. 网络请求（完全不变）
            URL url = new URL(OLLAMA_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                String response = s.hasNext() ? s.next() : "";
                JSONObject respJson = new JSONObject(response);
                String aiContent = respJson.getJSONObject("message").getString("content");

                Log.d(TAG, "AI 返回原始内容: " + aiContent);
                return parseRecipeFromJson(aiContent, userCondition, weatherInfo);
            } else {
                Log.e(TAG, "HTTP 错误码: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            Log.e(TAG, "Ollama 请求链路异常", e);
        }
        return createErrorRecipe("无法连接到电脑 AI 大厨，请检查小羊驼是否开启");
    }

    // 新增：解析食材列表
    private List<String> parseIngredientsToList(String detectedIngredients) {
        List<String> ingredients = new ArrayList<>();
        if (detectedIngredients == null || detectedIngredients.isEmpty()) {
            return ingredients;
        }
        String[] parts = detectedIngredients.split("[，,]");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                ingredients.add(trimmed);
            }
        }
        return ingredients;
    }

    // 以下方法完全不变
    private String parseWeatherToText(String raw) {
        if (raw == null || !raw.contains("{")) return "天气适宜";
        try {
            JSONObject json = new JSONObject(raw);
            if (json.has("lives")) {
                JSONObject live = json.getJSONArray("lives").getJSONObject(0);
                return live.optString("province") + live.optString("city") + " " +
                        live.optString("weather") + " " + live.optString("temperature") + "°C";
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "天气查询失败";
    }

    private Recipe parseRecipeFromJson(String jsonStr, String cond, String weather) throws Exception {
        String jsonContent = extractJsonContent(jsonStr);
        if (jsonContent == null) {
            Log.e(TAG, "无法从AI响应中提取JSON");
            Recipe errorRecipe = new Recipe();
            errorRecipe.setName("AI响应格式异常");
            errorRecipe.setDescription("AI大厨的响应格式不符合预期");
            return errorRecipe;
        }

        JSONObject json = new JSONObject(jsonContent);
        Recipe recipe = new Recipe();

        recipe.setName(json.optString("recipe_name", "创意食谱"));
        recipe.setDescription(json.optString("description", "美味健康的一餐"));
        recipe.setReason(json.optString("recommendation_reason", "根据您的状态定制"));
        recipe.setUserCondition(cond);
        recipe.setWeatherCondition(weather);

        JSONArray ingArray = json.optJSONArray("ingredients");
        if (ingArray != null) {
            for (int i = 0; i < ingArray.length(); i++) {
                Object obj = ingArray.get(i);
                if (obj instanceof JSONObject) {
                    JSONObject itemObj = (JSONObject) obj;
                    String item = itemObj.optString("item", "");
                    String amount = itemObj.optString("amount", "");
                    if (!item.isEmpty()) {
                        recipe.addIngredient(item + (!amount.isEmpty() ? " (" + amount + ")" : ""));
                    }
                } else {
                    recipe.addIngredient(obj.toString());
                }
            }
        }

        JSONArray stp = json.optJSONArray("cooking_steps");
        if (stp != null) {
            for (int i = 0; i < stp.length(); i++) {
                if (!stp.isNull(i)) {
                    String step = stp.optString(i, "");
                    if (!step.trim().isEmpty()) {
                        recipe.addCookingStep(step);
                    }
                }
            }
        }

        JSONObject nut = json.optJSONObject("nutrition_info");
        if (nut != null) {
            Log.d(TAG, "=== 原始nutrition_info ===");
            Log.d(TAG, nut.toString());

            double calories = parseNutritionValue(nut.optString("calories", "0"));
            double protein = parseNutritionValue(nut.optString("protein", "0"));
            double carbs = parseNutritionValue(nut.optString("carbs", "0"));
            double fat = parseNutritionValue(nut.optString("fat", "0"));

            Log.d(TAG, String.format("解析后的营养值: 热量=%.1f, 蛋白质=%.1f, 碳水=%.1f, 脂肪=%.1f",
                    calories, protein, carbs, fat));

            NutritionInfo nutrition = new NutritionInfo(calories, protein, carbs, fat);
            recipe.setNutrition(nutrition);
        } else {
            NutritionInfo defaultNutrition = new NutritionInfo(300, 15, 25, 10);
            recipe.setNutrition(defaultNutrition);
            Log.w(TAG, "JSON中没有nutrition_info，使用默认值");
        }

        String tips = json.optString("dietary_tips", "");
        if (!tips.isEmpty()) {
            recipe.setDescription(recipe.getDescription() + "\n\n💡提示：" + tips);
        }

        recipe.setPreparationTime(json.optString("preparation_time", "15分钟"));
        recipe.setCookingTime(json.optString("cooking_time", "20分钟"));

        String diff = json.optString("difficulty_level", "简单");
        recipe.setDifficulty(diff.contains("中") ? 2 : (diff.contains("难") || diff.contains("挑战") ? 3 : 1));

        return recipe;
    }

    private double parseNutritionValue(String valueWithUnit) {
        if (valueWithUnit == null || valueWithUnit.trim().isEmpty()) {
            return 0.0;
        }
        try {
            String numericPart = valueWithUnit.replaceAll("[^0-9.-]", "");
            if (!numericPart.isEmpty()) {
                return Double.parseDouble(numericPart);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "解析营养值失败: " + valueWithUnit, e);
        }
        return 0.0;
    }

    private String extractJsonContent(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        int startIndex = input.indexOf("{");
        int endIndex = input.lastIndexOf("}");
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return input.substring(startIndex, endIndex + 1);
        }
        startIndex = input.indexOf("[");
        endIndex = input.lastIndexOf("]");
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return input.substring(startIndex, endIndex + 1);
        }
        return null;
    }

    private Recipe createErrorRecipe(String msg) {
        Recipe r = new Recipe();
        r.setName("AI 大厨休息中");
        r.setDescription(msg);
        return r;
    }
    // 添加回调接口
    public interface GenerationCallback {
        void onSuccess(Recipe recipe);
        void onError(String error);
    }

    /**
     * 基于迁移后的食谱生成最终版本
     */
    public void generateFromMigration(String ingredients,
                                      String userGoal,
                                      String userCondition,
                                      JSONObject migratedRecipe,
                                      String cuisine,
                                      GenerationCallback callback) {
        new Thread(() -> {
            try {
                UserProfile userProfile = new UserProfile(context);
                double bmiValue = userProfile.calculateBMI();

                // 获取天气
                String coords = LocationHelper.getCoordinates(context);
                String weatherRaw = WeatherProvider.fetchWeather(coords);
                String weatherInfo = parseWeatherToText(weatherRaw);

                // 从迁移食谱中提取信息
                String recipeName = migratedRecipe.optString("name", "风味改良食谱");
                String recipeDesc = migratedRecipe.optString("description", "");

                JSONArray steps = migratedRecipe.optJSONArray("steps");
                StringBuilder stepsStr = new StringBuilder();
                if (steps != null) {
                    for (int i = 0; i < steps.length(); i++) {
                        stepsStr.append(i+1).append(". ").append(steps.getString(i)).append("\n");
                    }
                }

                // 构建提示词：告诉大模型这是用户选择的改良版
                String userPrompt = String.format(
                        "【用户已选择风味改良】\n" +
                                "用户希望将食谱改良为 %s 风味，以下是风味迁移模型生成的基础版本。\n\n" +
                                "【用户信息】\n" +
                                "BMI: %.1f\n" +
                                "健康目标: %s\n" +
                                "身体状态: %s\n" +
                                "当前天气: %s\n\n" +
                                "【基础改良食谱】\n" +
                                "菜名: %s\n" +
                                "描述: %s\n" +
                                "食材: %s\n" +
                                "步骤:\n%s\n\n" +
                                "【任务】\n" +
                                "1. 基于这个基础食谱，生成完整的烹饪指导\n" +
                                "2. 确保符合用户的健康目标 (%s)\n" +
                                "3. 添加详细的烹饪技巧和时间建议\n" +
                                "4. 保持 %s 的风味特色\n" +
                                "5. 输出格式必须是JSON，包含：name, description, ingredients, cooking_steps, nutrition_info, preparation_time, cooking_time, difficulty_level, dietary_tips",
                        cuisine,
                        bmiValue, userGoal, userCondition, weatherInfo,
                        recipeName, recipeDesc, ingredients, stepsStr.toString(),
                        userGoal, cuisine
                );

                Log.d(TAG, "生成提示词: " + userPrompt);

                // 调用 Ollama
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", CUSTOM_MODEL);
                requestBody.put("stream", false);

                JSONArray messages = new JSONArray();
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", userPrompt);
                messages.put(userMessage);
                requestBody.put("messages", messages);

                URL url = new URL(OLLAMA_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(60000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                    String response = s.hasNext() ? s.next() : "";
                    JSONObject respJson = new JSONObject(response);
                    String aiContent = respJson.getJSONObject("message").getString("content");

                    Log.d(TAG, "Ollama响应: " + aiContent);

                    Recipe finalRecipe = parseRecipeFromJson(aiContent, userCondition, weatherInfo);

                    // 如果没有获取到菜名，使用基础食谱的菜名
                    if (finalRecipe.getName() == null || finalRecipe.getName().isEmpty()) {
                        finalRecipe.setName(recipeName);
                    }

                    callback.onSuccess(finalRecipe);
                } else {
                    callback.onError("HTTP错误: " + conn.getResponseCode());
                }

            } catch (Exception e) {
                Log.e(TAG, "生成失败", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }
}