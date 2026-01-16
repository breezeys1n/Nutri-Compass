package com.example.nutricompass;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * AI食谱分析器
 * 使用DeepSeek API进行食材识别和食谱生成
 */
public class RecipeAnalyzer {
    private static final String TAG = "RecipeAnalyzer";
    private Context context;

    // DeepSeek API配置
    private static final String DEEPSEEK_API_KEY = BuildConfig.DEEPSEEK_API_KEY; // 替换为你的DeepSeek API密钥
    private static final String DEEPSEEK_ENDPOINT = "https://api.deepseek.com/chat/completions";

    public RecipeAnalyzer(Context context) {
        this.context = context;
        testDeepSeekConnection(); // 测试连接
    }

    /**
     * 分析食材并生成食谱
     */
    public Recipe analyzeRecipe(String imageBase64, String userGoal, String userCondition) {
        try {
            // 1. 使用百度AI识别食材
            BaiduImageRecognizer recognizer = new BaiduImageRecognizer(context);
            List<FoodItem> detectedFoods = recognizer.recognizeFood(imageBase64);

            // 2. 获取用户信息
            UserProfile userProfile = new UserProfile(context);
            String height = userProfile.getHeight();
            String weight = userProfile.getWeight();

            // 3. 获取天气信息
            String weather = getWeatherInfo();

            // 4. 使用DeepSeek生成个性化食谱
            Recipe recipe = generatePersonalizedRecipe(detectedFoods, userGoal, userCondition, weather, height, weight);

            return recipe;

        } catch (Exception e) {
            Log.e(TAG, "分析失败: " + e.getMessage());
            return createMockRecipe(userGoal, userCondition);
        }
    }

    /**
     * 检测食材（这里可以调用DeepSeek的视觉识别或使用第三方API）
     * 注意：DeepSeek目前主要支持文本，视觉识别需要结合其他服务
     */
    private List<FoodItem> detectFoodItems(String imageBase64) {
        List<FoodItem> foods = new ArrayList<>();

        try {
            Log.d(TAG, "开始调用百度AI识别食材...");

            // 使用百度AI识别食材
            BaiduImageRecognizer recognizer = new BaiduImageRecognizer(context);
            foods = recognizer.recognizeFood(imageBase64);

            Log.d(TAG, "识别结果: " + foods.size() + " 种食材");
            for (FoodItem food : foods) {
                Log.d(TAG, "  - " + food.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "食材识别失败: " + e.getMessage(), e);
            // 识别失败时使用模拟数据
            foods.add(new FoodItem("番茄", 0.92, 2, "个"));
            foods.add(new FoodItem("鸡蛋", 0.95, 3, "个"));
        }

        return foods;
    }

    /**
     * 调用DeepSeek进行食材识别（文本描述）
     */
    private String callDeepSeekForFoodDetection(String imageBase64) throws Exception {
        // 注意：DeepSeek可能需要base64格式的图片或通过其他方式处理
        // 这里暂时返回模拟数据
        return "识别到以下食材：番茄、鸡蛋、洋葱、青椒";
    }

    /**
     * 获取天气信息
     */
    private String getWeatherInfo() {
        try {
            // 这里可以调用天气API，例如：
            // 1. 和风天气：https://dev.qweather.com/
            // 2. 心知天气：https://www.seniverse.com/
            // 暂时返回模拟数据

            // 调用天气API的示例（需要添加网络权限和相应的API密钥）
            // return getWeatherFromAPI();

            return "晴朗，25°C，湿度60%，适合清爽饮食";
        } catch (Exception e) {
            Log.e(TAG, "获取天气失败: " + e.getMessage());
            return "天气：适宜";
        }
    }

    /**
     * 生成个性化食谱（调用DeepSeek API）
     */
    private Recipe generatePersonalizedRecipe(List<FoodItem> foods,
                                              String goal,
                                              String condition,
                                              String weather,
                                              String height,
                                              String weight) {
        try {
            // 修改1：构建更清晰的食材列表字符串
            StringBuilder foodList = new StringBuilder();
            if (foods != null && !foods.isEmpty()) {
                foodList.append("检测到以下主要食材：\n");
                for (FoodItem food : foods) {
                    foodList.append("- ")
                            .append(food.getName())
                            .append("：大约")
                            .append(String.format("%.1f", food.getQuantity()))
                            .append(food.getUnit())
                            .append("（识别可信度：")
                            .append(String.format("%.0f%%", food.getConfidence() * 100))
                            .append("）\n");
                }
                // 添加常见可用的基本调料
                foodList.append("\n【可添加的基本调料】\n");
                foodList.append("- 植物油、盐、酱油、醋、糖、料酒、姜、蒜、葱\n");
                foodList.append("- 可根据需要适量使用\n");
            } else {
                foodList.append("未能明确识别食材，请基于用户目标和常见食材推荐。");
            }

            Log.d(TAG, "构建的食材列表：\n" + foodList.toString());

            // 修改2：使用改进的提示词构建方法
            String prompt = buildRecipePrompt(foodList.toString(), goal, condition, weather, height, weight, foods);

            // 调用DeepSeek API
            String recipeJson = callDeepSeekAPI(prompt);

            // 解析返回的JSON
            return parseRecipeFromDeepSeekResponse(recipeJson, foods, goal, condition, weather);

        } catch (Exception e) {
            Log.e(TAG, "生成食谱失败: " + e.getMessage());
            return createMockRecipe(goal, condition);
        }
    }

    /**
     * 构建AI提示词
     */
    private String buildRecipePrompt(String foodList, String goal, String condition,
                                     String weather, String height, String weight,
                                     List<FoodItem> foods) {

        // 分析食材类型，帮助AI更好理解
        String ingredientAnalysis = analyzeIngredientTypes(foods);

        return String.format(
                "你是一个富有创意的专业营养师和厨师。请根据用户的具体情况，设计一个**独特、个性化**的健康食谱。\n\n" +

                        "【用户档案】\n" +
                        "🏷️ 健康目标：%s\n" +
                        "📊 身体数据：身高 %scm，体重 %skg\n" +
                        "🌡️ 当前状态：%s\n" +
                        "🌤️ 天气情况：%s\n\n" +

                        "【可用食材分析】\n%s\n\n" +

                        "%s\n\n" + // 食材类型分析

                        "【设计要求】\n" +
                        "🔹 **核心原则**：食谱必须主要使用上述检测到的食材\n" +
                        "🔹 **目标导向**：\n" +
                        "   - 如果目标是'减脂'：优先蒸、煮、烤，避免油炸，控制油盐\n" +
                        "   - 如果目标是'增肌'：确保蛋白质充足，可适量增加份量\n" +
                        "   - 如果目标是'控糖'：选择低GI食材，减少精制碳水\n" +
                        "   - 如果目标是'清淡调理'：做法温和，易消化\n" +
                        "🔹 **创新要求**：\n" +
                        "   - **不要做'番茄炒蛋'或'番茄洋葱炒蛋盖饭'**\n" +
                        "   - 根据食材特点设计新颖的搭配\n" +
                        "   - 菜名要有创意，反映食材特色\n" +
                        "🔹 **天气适配**：%s天气，选择适合的烹饪方式和口味\n\n" +

                        "【输出格式要求】\n" +
                        "请严格按以下JSON格式返回：\n" +
                        "{\n" +
                        "  \"recipe_name\": \"富有创意的菜名（体现食材和健康目标）\",\n" +
                        "  \"description\": \"50字内描述这道菜的特点和好处\",\n" +
                        "  \"recommendation_reason\": \"详细说明为什么这道菜适合这位用户（结合所有因素）\",\n" +
                        "  \"ingredients\": [\"主要食材1 精确用量\", \"主要食材2 精确用量\", \"调料 精确用量\"],\n" +
                        "  \"cooking_steps\": [\"步骤1（包含具体时间和操作）\", \"步骤2（包含火候和技巧）\"],\n" +
                        "  \"nutrition_info\": {\"calories\": 数值, \"protein\": 数值, \"carbs\": 数值, \"fat\": 数值},\n" +
                        "  \"preparation_time\": \"例如：10-15分钟\",\n" +
                        "  \"cooking_time\": \"例如：15-20分钟\",\n" +
                        "  \"difficulty_level\": \"简单/中等/复杂\",\n" +
                        "  \"cooking_tips\": \"实用烹饪建议，如替代方案、注意事项等\"\n" +
                        "}\n\n" +
                        "**重要提醒**：根据不同的食材组合，请设计完全不同的菜谱！",
                goal, height, weight, condition, weather, foodList, ingredientAnalysis,
                weather.contains("炎热") || weather.contains("热") ? "炎热" : "适宜"
        );
    }

    /**
     * 分析食材类型，帮助AI更好地理解
     */
    private String analyzeIngredientTypes(List<FoodItem> foods) {
        if (foods == null || foods.isEmpty()) {
            return "【食材类型】未检测到明确食材，请自由发挥推荐。\n";
        }

        StringBuilder analysis = new StringBuilder("【食材类型分析】\n");

        int vegetableCount = 0;
        int proteinCount = 0;
        int starchCount = 0;
        List<String> vegetableNames = new ArrayList<>();
        List<String> proteinNames = new ArrayList<>();
        List<String> starchNames = new ArrayList<>();

        for (FoodItem food : foods) {
            String name = food.getName().toLowerCase();

            if (name.contains("番茄") || name.contains("洋葱") || name.contains("青椒") ||
                    name.contains("胡萝卜") || name.contains("青菜") || name.contains("蘑菇") ||
                    name.contains("土豆") || name.contains("白菜")) {
                vegetableCount++;
                vegetableNames.add(food.getName());
            } else if (name.contains("鸡") || name.contains("猪") || name.contains("牛") ||
                    name.contains("鱼") || name.contains("肉") || name.contains("蛋") ||
                    name.contains("豆腐")) {
                proteinCount++;
                proteinNames.add(food.getName());
            } else if (name.contains("米饭") || name.contains("面条") || name.contains("土豆")) {
                starchCount++;
                starchNames.add(food.getName());
            }
        }

        if (vegetableCount > 0) {
            analysis.append("- 蔬菜类：").append(String.join("、", vegetableNames))
                    .append("（可做主菜或配菜）\n");
        }
        if (proteinCount > 0) {
            analysis.append("- 蛋白质类：").append(String.join("、", proteinNames))
                    .append("（适合做主料）\n");
        }
        if (starchCount > 0) {
            analysis.append("- 淀粉类：").append(String.join("、", starchNames))
                    .append("（可做主食或配菜）\n");
        }

        // 给出搭配建议
        analysis.append("\n【可能的搭配思路】\n");
        if (proteinCount > 0 && vegetableCount > 0) {
            analysis.append("✅ 可制作：蛋白质+蔬菜的炒菜/炖菜/蒸菜\n");
        }
        if (vegetableCount >= 2) {
            analysis.append("✅ 可制作：纯蔬菜的沙拉/炒时蔬/汤\n");
        }
        if (proteinCount >= 2) {
            analysis.append("✅ 可制作：蛋白质组合（如：双拼）\n");
        }
        if (starchCount > 0 && (proteinCount > 0 || vegetableCount > 0)) {
            analysis.append("✅ 可制作：主食+菜的盖饭/拌面/烩饭\n");
        }

        return analysis.toString();
    }

    /**
     * 调用DeepSeek API
     */
    private String callDeepSeekAPI(String prompt) throws Exception {
        // 修改API密钥验证逻辑
        if (DEEPSEEK_API_KEY == null || DEEPSEEK_API_KEY.trim().isEmpty() ||
                DEEPSEEK_API_KEY.equals("你的DeepSeek API密钥")) {
            throw new Exception("请配置有效的DeepSeek API密钥");
        }
        URL url = new URL(DEEPSEEK_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + DEEPSEEK_API_KEY);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        // 构建请求体 - 确保模型名称正确
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "deepseek-chat");  // 确认这是正确的模型名称
        requestBody.put("stream", false);

        // 添加温度参数，使响应更一致
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 2000);

        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messages.put(message);

        requestBody.put("messages", messages);

        Log.d(TAG, "调用DeepSeek API，提示词长度: " + prompt.length());
        Log.d(TAG, "使用API密钥（部分）: " + DEEPSEEK_API_KEY.substring(0, Math.min(10, DEEPSEEK_API_KEY.length())) + "...");

        // 发送请求
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
            Log.d(TAG, "请求发送成功，数据大小: " + input.length + " bytes");
        }

        // 检查响应
        int responseCode = conn.getResponseCode();
        Log.d(TAG, "DeepSeek API响应码: " + responseCode);

        if (responseCode == 200) {
            String response = readStream(conn.getInputStream());
            Log.d(TAG, "DeepSeek API响应（前200字符）: " +
                    (response.length() > 200 ? response.substring(0, 200) + "..." : response));

            // 解析响应
            try {
                JSONObject jsonResponse = new JSONObject(response);
                if (!jsonResponse.has("choices")) {
                    throw new Exception("响应中缺少choices字段");
                }

                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices.length() == 0) {
                    throw new Exception("choices数组为空");
                }

                JSONObject choice = choices.getJSONObject(0);
                if (!choice.has("message")) {
                    throw new Exception("choice中缺少message字段");
                }

                JSONObject messageObj = choice.getJSONObject("message");
                String content = messageObj.getString("content");

                Log.d(TAG, "成功获取AI响应，内容长度: " + content.length());
                return content;

            } catch (Exception e) {
                Log.e(TAG, "解析API响应失败: " + e.getMessage());
                throw new Exception("API响应格式错误: " + e.getMessage());
            }

        } else {
            String error = readStream(conn.getErrorStream());
            Log.e(TAG, "DeepSeek API错误响应: " + error);

            // 尝试解析错误信息
            try {
                JSONObject errorJson = new JSONObject(error);
                if (errorJson.has("error") && errorJson.getJSONObject("error").has("message")) {
                    throw new Exception("DeepSeek API错误: " + errorJson.getJSONObject("error").getString("message"));
                }
            } catch (Exception e) {
                // 如果无法解析JSON，返回原始错误
            }
            throw new Exception("DeepSeek API请求失败，状态码: " + responseCode + ", 错误: " + error);
        }
    }
    /**
     * 测试DeepSeek API连接
     */
    public void testDeepSeekConnection() {
        new Thread(() -> {
            try {
                String testPrompt = "请简单回复：API连接测试成功";
                String response = callDeepSeekAPI(testPrompt);
                Log.d(TAG, "DeepSeek API测试成功: " + response);
            } catch (Exception e) {
                Log.e(TAG, "DeepSeek API测试失败: " + e.getMessage());
            }
        }).start();
    }
    /**
     * 读取输入流
     */
    private String readStream(java.io.InputStream inputStream) throws Exception {
        Scanner scanner = new Scanner(inputStream, "UTF-8");
        String result = scanner.useDelimiter("\\A").next();
        scanner.close();
        return result;
    }

    /**
     * 解析DeepSeek返回的JSON
     */
    private Recipe parseRecipeFromDeepSeekResponse(String response,
                                                   List<FoodItem> foods,
                                                   String goal,
                                                   String condition,
                                                   String weather) {
        try {
            // 从响应中提取JSON部分（DeepSeek可能会在JSON前后添加文本）
            String jsonStr = extractJsonFromResponse(response);

            JSONObject json = new JSONObject(jsonStr);
            Recipe recipe = new Recipe();

            // 解析基本信息
            recipe.setName(json.getString("recipe_name"));
            recipe.setDescription(json.getString("description"));
            recipe.setReason(json.getString("recommendation_reason"));
            recipe.setWeatherCondition(weather);
            recipe.setUserCondition(condition);

            // 解析食材列表
            JSONArray ingredientsArray = json.getJSONArray("ingredients");
            for (int i = 0; i < ingredientsArray.length(); i++) {
                recipe.addIngredient(ingredientsArray.getString(i));
            }

            // 解析烹饪步骤
            JSONArray stepsArray = json.getJSONArray("cooking_steps");
            for (int i = 0; i < stepsArray.length(); i++) {
                recipe.addCookingStep((i + 1) + ". " + stepsArray.getString(i));
            }

            // 解析营养信息
            JSONObject nutritionJson = json.getJSONObject("nutrition_info");
            NutritionInfo nutrition = new NutritionInfo();
            nutrition.setCalories(nutritionJson.getDouble("calories"));
            nutrition.setProtein(nutritionJson.getDouble("protein"));
            nutrition.setCarbs(nutritionJson.getDouble("carbs"));
            nutrition.setFat(nutritionJson.getDouble("fat"));
            recipe.setNutrition(nutrition);

            // 解析其他信息
            recipe.setPreparationTime(json.getString("preparation_time"));
            recipe.setCookingTime(json.getString("cooking_time"));

            // 解析难度等级
            String difficulty = json.getString("difficulty_level");
            recipe.setDifficulty(convertDifficultyToNumber(difficulty));

            // 添加小贴士
            if (json.has("cooking_tips")) {
                recipe.addCookingStep("\n烹饪小贴士: " + json.getString("cooking_tips"));
            }

            return recipe;

        } catch (Exception e) {
            Log.e(TAG, "解析DeepSeek响应失败: " + e.getMessage() + "\n响应内容: " + response);
            // 解析失败时使用模拟数据
            return createMockRecipe(goal, condition);
        }
    }

    /**
     * 从响应中提取JSON字符串
     */
    private String extractJsonFromResponse(String response) {
        try {
            // 尝试直接解析
            new JSONObject(response);
            return response;
        } catch (Exception e) {
            // 如果失败，尝试提取JSON部分
            int start = response.indexOf("{");
            int end = response.lastIndexOf("}") + 1;
            if (start >= 0 && end > start) {
                return response.substring(start, end);
            }
            throw new RuntimeException("无法提取JSON");
        }
    }

    /**
     * 将难度等级转换为数字
     */
    private int convertDifficultyToNumber(String difficulty) {
        switch (difficulty.toLowerCase()) {
            case "简单": return 1;
            case "中等": return 2;
            case "复杂":
            case "困难": return 3;
            default: return 2;
        }
    }

    /**
     * 创建模拟食谱（当API调用失败时使用）
     */
    private Recipe createMockRecipe(String goal, String condition) {
        Recipe recipe = new Recipe();
        recipe.setName("智能番茄炒蛋");
        recipe.setDescription("根据您的目标和现有食材定制的健康食谱");
        recipe.setReason(String.format("针对您的%s目标，结合%s状态，采用低油少盐的烹饪方式", goal, condition));
        recipe.setWeatherCondition("晴朗，25°C，适合清爽饮食");
        recipe.setUserCondition(condition);

        // 添加食材
        recipe.addIngredient("番茄 2个");
        recipe.addIngredient("鸡蛋 3个");
        recipe.addIngredient("橄榄油 10毫升");
        recipe.addIngredient("盐 3克");
        recipe.addIngredient("葱花 5克");
        recipe.addIngredient("蒜末 3克");

        // 添加步骤
        recipe.addCookingStep("1. 番茄洗净切块，鸡蛋打散备用");
        recipe.addCookingStep("2. 热锅加入10毫升橄榄油，倒入蛋液翻炒至金黄，盛出备用");
        recipe.addCookingStep("3. 锅中加入5毫升油，放入番茄块翻炒出汁");
        recipe.addCookingStep("4. 加入炒好的鸡蛋，轻轻翻炒均匀");
        recipe.addCookingStep("5. 加入3克盐和葱花，翻炒均匀即可出锅");
        recipe.addCookingStep("烹饪小贴士：使用不粘锅可以减少油的用量");

        // 设置营养信息
        NutritionInfo nutrition = new NutritionInfo();
        nutrition.setCalories(220);
        nutrition.setProtein(15);
        nutrition.setCarbs(10);
        nutrition.setFat(12);
        recipe.setNutrition(nutrition);

        recipe.setPreparationTime("10分钟");
        recipe.setCookingTime("15分钟");
        recipe.setDifficulty(2);

        return recipe;
    }
}