package com.example.nutricompass;

import android.content.Context;
import android.util.Log;
import com.example.nutricompass.provider.WeatherProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class RecipeAnalyzer {
    private static final String TAG = "RecipeAnalyzer_Logic";
    private Context context;
    private DoubaoImageRecognizer doubaoRecognizer;

    // 使用 BuildConfig 获取 local.properties 中的 Key
    private static final String DEEPSEEK_API_KEY = BuildConfig.DEEPSEEK_API_KEY;
    private static final String DEEPSEEK_URL = "https://api.deepseek.com/chat/completions";

    public RecipeAnalyzer(Context context) {
        this.context = context;
        // 初始化豆包识别器，内部不要引用 FoodItem 类
        this.doubaoRecognizer = new DoubaoImageRecognizer();
    }

    public Recipe analyzeRecipe(String imageBase64, String userGoal, String userCondition) {
        try {
            // 1. 豆包识图获取食材字符串
            Log.d(TAG, "📡 正在通过豆包识图...");
            String detectedIngredients = doubaoRecognizer.recognizeFood(imageBase64);
            Log.d(TAG, "✅ 豆包识别结果: " + detectedIngredients);

            // 2. 获取并解析天气 (处理高德 JSON 变文字)
            String coords = LocationHelper.getCoordinates(context);
            String weatherRaw = WeatherProvider.fetchWeather(coords);
            String weatherInfo = parseWeatherToText(weatherRaw);
            Log.d(TAG, "🌤️ 天气描述: " + weatherInfo);

            // 3. 获取用户信息
            UserProfile userProfile = new UserProfile(context);
            String height = userProfile.getHeight();
            String weight = userProfile.getWeight();

            // 4. 构建提示词 (严格按你要求的格式)
            String prompt = String.format(
                    "你是一个接地气的专业营养师和家常厨师。请根据用户的具体情况，设计一个**家常、方便制作**的健康食谱。\n\n" +
                            "【用户档案】\n" +
                            "🏷️ 健康目标：%s\n" +
                            "📊 身体数据：身高 %scm，体重 %skg\n" +
                            "🌡️ 当前状态：%s\n" +
                            "🌤️ 天气情况：%s\n\n" +
                            "【可用食材分析】\n%s\n\n" +
                            "【设计要求】\n" +
                            "🔹 **核心原则**：食谱必须主要使用上述图片中检测到的食材，如果食材很多也可以从中挑选而不全用\n" +
                            "🔹 **目标导向**：\n" +
                            "   - 如果目标是'减脂'：优先蒸、煮、烤，避免油炸，控制油盐\n" +
                            "   - 如果目标是'增肌'：确保蛋白质充足，可适量增加份量\n" +
                            "   - 如果目标是'控糖'：选择低GI食材，减少精制碳水\n" +
                            "   - 如果目标是'清淡调理'：做法温和，易消化\n" +
                            "🔹 **创新要求**：\n" +
                            "   - **不要做'番茄炒蛋'或'番茄洋葱炒蛋盖饭'**\n" +
                            "   - 根据食材特点设计家常的搭配\n" +
                            "   - 菜名要有家常味，反映食材特色\n" +
                            "🔹 **天气适配**：%s天气，选择适合的烹饪方式和口味\n\n" +
                            "【输出格式要求】\n" +
                            "请严格按以下JSON格式返回：\n" +
                            "{\n" +
                            "  \"recipe_name\": \"菜名\",\n" +
                            "  \"description\": \"50字内描述这道菜的特点和好处\",\n" +
                            "  \"recommendation_reason\": \"详细说明理由，可以说天气但是不要提地名\",\n" +
                            "  \"ingredients\": [\"食材用量\", \"调料用量\"],\n" +
                            "  \"cooking_steps\": [\"步骤1\", \"步骤2\"],\n" +
                            "  \"nutrition_info\": {\"calories\": 数值, \"protein\": 数值, \"carbs\": 数值, \"fat\": 数值},\n" +
                            "  \"preparation_time\": \"例如：10-15分钟\",\n" +
                            "  \"cooking_time\": \"例如：15-20分钟\",\n" +
                            "  \"difficulty_level\": \"简单/中等/复杂\",\n" +
                            "  \"cooking_tips\": \"烹饪建议\"\n" +
                            "}\n\n" +
                            "**重要提醒**：根据不同的食材组合，请设计完全不同的菜谱！",
                    userGoal, height, weight, userCondition, weatherInfo, detectedIngredients,
                    (weatherInfo.contains("热")) ? "炎热" : "适宜"
            );

            // 5. 调用 DeepSeek
            Log.d(TAG, "📡 正在请求 DeepSeek...");
            String deepSeekResult = callDeepSeek(prompt);

            // 6. 解析
            return parseResponse(deepSeekResult, userCondition, weatherInfo);

        } catch (Exception e) {
            Log.e(TAG, "❌ 分析链条故障: " + e.getMessage());
            return createErrorRecipe(e.getMessage());
        }
    }

    private String parseWeatherToText(String raw) {
        if (raw == null || !raw.contains("{")) return "天气适宜";
        try {
            JSONObject json = new JSONObject(raw);
            if (json.has("lives")) {
                JSONObject live = json.getJSONArray("lives").getJSONObject(0);
                return live.optString("weather") + " " + live.optString("temperature") + "°C";
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "天气适宜";
    }

    private String callDeepSeek(String prompt) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", "deepseek-chat");
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "user").put("content", prompt));
        body.put("messages", msgs);
        body.put("response_format", new JSONObject().put("type", "json_object"));

        URL url = new URL(DEEPSEEK_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + DEEPSEEK_API_KEY);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setReadTimeout(60000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }

        if (conn.getResponseCode() == 200) {
            Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            return new JSONObject(result).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
        } else {
            throw new Exception("HTTP " + conn.getResponseCode());
        }
    }

    private Recipe parseResponse(String jsonStr, String cond, String weather) throws Exception {
        JSONObject json = new JSONObject(jsonStr);
        Recipe recipe = new Recipe();
        recipe.setName(json.getString("recipe_name"));
        recipe.setDescription(json.getString("description"));
        recipe.setReason(json.getString("recommendation_reason"));
        recipe.setUserCondition(cond);
        recipe.setWeatherCondition(weather);

        JSONArray ing = json.getJSONArray("ingredients");
        for (int i = 0; i < ing.length(); i++) recipe.addIngredient(ing.getString(i));

        JSONArray stp = json.getJSONArray("cooking_steps");
        for (int i = 0; i < stp.length(); i++) recipe.addCookingStep(stp.getString(i));

        JSONObject nut = json.getJSONObject("nutrition_info");
        recipe.setNutrition(new NutritionInfo(
                nut.optDouble("calories"),
                nut.optDouble("protein"),
                nut.optDouble("carbs"),
                nut.optDouble("fat")
        ));

        recipe.setPreparationTime(json.optString("preparation_time"));
        recipe.setCookingTime(json.optString("cooking_time"));
        String diff = json.optString("difficulty_level");
        recipe.setDifficulty(diff.contains("简单") ? 1 : (diff.contains("复杂") ? 3 : 2));

        return recipe;
    }

    private Recipe createErrorRecipe(String msg) {
        Recipe r = new Recipe();
        r.setName("分析出现错误");
        r.setReason("请检查网络或重新拍摄图片再试一次");
        return r;
    }
}