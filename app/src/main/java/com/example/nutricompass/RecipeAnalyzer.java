package com.example.nutricompass;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class RecipeAnalyzer {
    private static final String TAG = "RecipeAnalyzer_Logic";
    private Context context;

    // 本地 Ollama 地址 (确保你的电脑小羊驼正在运行且 OLLAMA_HOST=0.0.0.0)
    private static final String OLLAMA_URL = "http://192.168.2.77:11434/api/chat";
    // 使用你通过 Nutri-Compass.txt 创建的定制模型
    private static final String CUSTOM_MODEL = "my_health_chef";

    public RecipeAnalyzer(Context context) {
        this.context = context;
    }

    /**
     * 场景 A：拍照识别 + 生成食谱
     */
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

    /**
     * 场景 B：纯文字/状态勾勒生成食谱
     */
    public Recipe analyzeWithLocalIngredients(String detectedIngredients, String userGoal, String userCondition) {
        try {

            UserProfile userProfile = new UserProfile(context);
            double bmiValue = userProfile.calculateBMI();

            // 1. 获取天气
            String coords = LocationHelper.getCoordinates(context);
            String weatherRaw = WeatherProvider.fetchWeather(coords);
            String weatherInfo = parseWeatherToText(weatherRaw);

            // 2. 构建输入数据 (人设已在 my_health_chef 中，此处只传数据)
            // 这里的 userCondition 就会承载 "我爬了山走了3w步很累" 这种描述
            String userPrompt = String.format(
                    "【我的 BMI】: %s\n【我的目标】：%s\n【身体状态】：%s\n【当前天气】：%s\n【现有食材】：%s",
                    bmiValue, userGoal, userCondition, weatherInfo, detectedIngredients
            );

            // 3. 构建请求体
            JSONObject root = new JSONObject();
            root.put("model", CUSTOM_MODEL);
            root.put("stream", false);

            JSONArray messages = new JSONArray();
            // 直接以 user 身份发送数据，my_health_chef 会自动触发 system 里的营养师逻辑
            messages.put(new JSONObject().put("role", "user").put("content", userPrompt));
            root.put("messages", messages);

            Log.d(TAG, "正在请求 Ollama (模型: " + CUSTOM_MODEL + ")...");

            // 4. 网络请求
            URL url = new URL(OLLAMA_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000); // AI 思考可能需要时间，给 30 秒
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
        // 尝试提取JSON内容（处理各种格式）
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

        // 处理食材列表
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

        // 处理烹饪步骤
        JSONArray stp = json.optJSONArray("cooking_steps");
        if (stp != null) {
            for (int i = 0; i < stp.length(); i++) {
                String step = stp.getString(i);
                if (step != null && !step.trim().isEmpty()) {
                    recipe.addCookingStep(step);
                }
            }
        }

        // 处理营养信息
        JSONObject nut = json.optJSONObject("nutrition_info");
        if (nut != null) {
            Log.d(TAG, "=== 原始nutrition_info ===");
            Log.d(TAG, nut.toString());

            // 使用新的解析方法处理带单位的字符串
            double calories = parseNutritionValue(nut.optString("calories", "0"));
            double protein = parseNutritionValue(nut.optString("protein", "0"));
            double carbs = parseNutritionValue(nut.optString("carbs", "0"));
            double fat = parseNutritionValue(nut.optString("fat", "0"));

            Log.d(TAG, String.format("解析后的营养值: 热量=%.1f, 蛋白质=%.1f, 碳水=%.1f, 脂肪=%.1f",
                    calories, protein, carbs, fat));

            // 使用带参数的构造函数
            NutritionInfo nutrition = new NutritionInfo(calories, protein, carbs, fat);
            recipe.setNutrition(nutrition);
        } else {
            // 使用默认值
            NutritionInfo defaultNutrition = new NutritionInfo(300, 15, 25, 10);
            recipe.setNutrition(defaultNutrition);
            Log.w(TAG, "JSON中没有nutrition_info，使用默认值");
        }

        // 其他字段
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
            // 移除所有非数字字符（除了小数点和负号）
            String numericPart = valueWithUnit.replaceAll("[^0-9.-]", "");
            if (!numericPart.isEmpty()) {
                return Double.parseDouble(numericPart);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "解析营养值失败: " + valueWithUnit, e);
        }

        return 0.0;
    }
    // 辅助方法：从JSON字符串中提取JSON内容
    private String extractJsonContent(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        // 尝试查找JSON对象
        int startIndex = input.indexOf("{");
        int endIndex = input.lastIndexOf("}");

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return input.substring(startIndex, endIndex + 1);
        }

        // 尝试查找JSON数组
        startIndex = input.indexOf("[");
        endIndex = input.lastIndexOf("]");

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return input.substring(startIndex, endIndex + 1);
        }

        return null;
    }
    // 辅助方法：安全获取double值
    private double getDoubleValue(JSONObject obj, String key, double defaultValue) {
        try {
            if (obj.has(key)) {
                Object value = obj.get(key);
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                } else if (value instanceof String) {
                    try {
                        return Double.parseDouble((String) value);
                    } catch (NumberFormatException e) {
                        Log.w(TAG, key + "字段不是有效的数字: " + value);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "获取字段" + key + "失败: " + e.getMessage());
        }
        return defaultValue;
    }

    private Recipe createErrorRecipe(String msg) {
        Recipe r = new Recipe();
        r.setName("AI 大厨休息中");
        r.setDescription(msg);
        return r;
    }
}