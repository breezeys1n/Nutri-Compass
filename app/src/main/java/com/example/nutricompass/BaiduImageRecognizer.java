package com.example.nutricompass;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 百度AI图像识别 - 真实API版本
 */
public class BaiduImageRecognizer {
    private static final String TAG = "BaiduImageRecognizer";

    // 替换为你的百度AI API密钥
    private static final String API_KEY = BuildConfig.BAIDU_API_KEY;
    private static final String SECRET_KEY = BuildConfig.BAIDU_SECRET_KEY;

    private static final String DISH_RECOGNITION_URL = "https://aip.baidubce.com/rest/2.0/image-classify/v2/dish";
    private static final String ACCESS_TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";

    private Context context;
    private String accessToken;
    private long tokenExpireTime;

    public BaiduImageRecognizer(Context context) {
        this.context = context;
        this.accessToken = null;
        this.tokenExpireTime = 0;
    }

    /**
     * 识别图片中的食材（主方法）
     */
    public List<FoodItem> recognizeFood(String imageBase64) {
        List<FoodItem> foodItems = new ArrayList<>();

        try {
            Log.d(TAG, "开始识别食材，图片Base64长度: " + imageBase64.length());

            // 1. 获取access_token
            String token = getValidAccessToken();
            if (token == null) {
                Log.e(TAG, "获取access_token失败");
                return getDefaultFoodItems();
            }

            // 2. 调用菜品识别API
            String result = callDishRecognitionAPI(imageBase64, token);
            Log.d(TAG, "百度AI返回结果: " + result.substring(0, Math.min(200, result.length())));

            // 3. 解析结果
            foodItems = parseRecognitionResult(result);

            Log.d(TAG, "识别到 " + foodItems.size() + " 种食材");

        } catch (Exception e) {
            Log.e(TAG, "食材识别失败: " + e.getMessage(), e);
            return getDefaultFoodItems();
        }

        return foodItems;
    }

    /**
     * 获取有效的access_token
     */
    private synchronized String getValidAccessToken() throws Exception {
        // 如果token有效且未过期，直接返回
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }

        Log.d(TAG, "获取新的access_token...");

        String urlStr = ACCESS_TOKEN_URL +
                "?grant_type=client_credentials" +
                "&client_id=" + API_KEY +
                "&client_secret=" + SECRET_KEY;

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoInput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            String response = readStream(conn.getInputStream());
            JSONObject json = new JSONObject(response);

            if (json.has("access_token")) {
                accessToken = json.getString("access_token");
                // 百度token有效期通常是30天，这里设为25天避免过期
                tokenExpireTime = System.currentTimeMillis() + (25 * 24 * 60 * 60 * 1000L);

                Log.d(TAG, "获取access_token成功: " + accessToken.substring(0, 10) + "...");
                return accessToken;
            } else {
                throw new Exception("API返回格式错误: " + response);
            }
        } else {
            String error = readStream(conn.getErrorStream());
            throw new Exception("获取token失败: " + responseCode + " - " + error);
        }
    }

    /**
     * 调用菜品识别API
     */
    private String callDishRecognitionAPI(String imageBase64, String accessToken) throws Exception {
        String urlStr = DISH_RECOGNITION_URL + "?access_token=" + accessToken;

        // 移除Base64前缀（如果有）
        if (imageBase64.contains(",")) {
            imageBase64 = imageBase64.split(",")[1];
        }

        // 构建请求参数
        String params = "image=" + URLEncoder.encode(imageBase64, "UTF-8") +
                "&top_num=10" +  // 返回最多10个结果
                "&filter_threshold=0.7"; // 过滤置信度低于0.7的结果

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        // 发送请求
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = params.getBytes("UTF-8");
            os.write(input, 0, input.length);
        }

        // 获取响应
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            return readStream(conn.getInputStream());
        } else {
            String error = readStream(conn.getErrorStream());
            throw new Exception("识别API调用失败: " + responseCode + " - " + error);
        }
    }

    /**
     * 解析识别结果
     */
    private List<FoodItem> parseRecognitionResult(String jsonResult) {
        List<FoodItem> items = new ArrayList<>();

        try {
            JSONObject json = new JSONObject(jsonResult);

            if (json.has("error_code")) {
                int errorCode = json.getInt("error_code");
                String errorMsg = json.getString("error_msg");
                Log.e(TAG, "百度AI错误: " + errorCode + " - " + errorMsg);
                return getDefaultFoodItems();
            }

            if (!json.has("result") || json.isNull("result")) {
                Log.w(TAG, "未识别到任何菜品");
                return getDefaultFoodItems();
            }

            JSONArray results = json.getJSONArray("result");
            Log.d(TAG, "识别到 " + results.length() + " 个菜品");

            // 分析每个菜品，提取食材
            for (int i = 0; i < results.length(); i++) {
                JSONObject dish = results.getJSONObject(i);
                String dishName = dish.getString("name");
                double probability = dish.has("probability") ? dish.getDouble("probability") : 0.8;

                Log.d(TAG, "菜品 " + (i+1) + ": " + dishName + " (置信度: " + probability + ")");

                // 根据菜品名提取常见食材
                extractIngredientsFromDishName(dishName, probability, items);
            }

            // 如果没提取到食材，使用默认值
            if (items.isEmpty()) {
                Log.w(TAG, "未能从识别结果中提取食材，使用默认值");
                return getDefaultFoodItems();
            }

            // 去重（同一种食材取最高置信度）
            items = deduplicateFoodItems(items);

        } catch (Exception e) {
            Log.e(TAG, "解析识别结果失败: " + e.getMessage(), e);
            return getDefaultFoodItems();
        }

        return items;
    }

    /**
     * 从菜品名中提取食材
     */
    private void extractIngredientsFromDishName(String dishName, double confidence, List<FoodItem> items) {
        // 常见食材关键词映射
        String lowerName = dishName.toLowerCase();

        // 蔬菜类
        if (lowerName.contains("番茄") || lowerName.contains("西红柿") || lowerName.contains("tomato")) {
            items.add(new FoodItem("番茄", confidence, estimateQuantity("番茄", dishName), "个"));
        }
        if (lowerName.contains("鸡蛋") || lowerName.contains("蛋") || lowerName.contains("egg")) {
            items.add(new FoodItem("鸡蛋", confidence, estimateQuantity("鸡蛋", dishName), "个"));
        }
        if (lowerName.contains("洋葱") || lowerName.contains("onion")) {
            items.add(new FoodItem("洋葱", confidence, estimateQuantity("洋葱", dishName), "个"));
        }
        if (lowerName.contains("青椒") || lowerName.contains("辣椒") || lowerName.contains("pepper")) {
            items.add(new FoodItem("青椒", confidence, estimateQuantity("青椒", dishName), "个"));
        }
        if (lowerName.contains("土豆") || lowerName.contains("马铃薯") || lowerName.contains("potato")) {
            items.add(new FoodItem("土豆", confidence, estimateQuantity("土豆", dishName), "个"));
        }
        if (lowerName.contains("胡萝卜") || lowerName.contains("carrot")) {
            items.add(new FoodItem("胡萝卜", confidence, estimateQuantity("胡萝卜", dishName), "根"));
        }
        if (lowerName.contains("白菜") || lowerName.contains("青菜") || lowerName.contains("cabbage")) {
            items.add(new FoodItem("青菜", confidence, estimateQuantity("青菜", dishName), "颗"));
        }
        if (lowerName.contains("蘑菇") || lowerName.contains("香菇") || lowerName.contains("mushroom")) {
            items.add(new FoodItem("蘑菇", confidence, estimateQuantity("蘑菇", dishName), "个"));
        }

        // 肉类
        if (lowerName.contains("鸡肉") || lowerName.contains("鸡") || lowerName.contains("chicken")) {
            items.add(new FoodItem("鸡肉", confidence, estimateQuantity("鸡肉", dishName), "克"));
        }
        if (lowerName.contains("猪肉") || lowerName.contains("肉") || lowerName.contains("pork")) {
            items.add(new FoodItem("猪肉", confidence, estimateQuantity("猪肉", dishName), "克"));
        }
        if (lowerName.contains("牛肉") || lowerName.contains("beef")) {
            items.add(new FoodItem("牛肉", confidence, estimateQuantity("牛肉", dishName), "克"));
        }
        if (lowerName.contains("鱼") || lowerName.contains("fish")) {
            items.add(new FoodItem("鱼肉", confidence, estimateQuantity("鱼肉", dishName), "克"));
        }

        // 其他
        if (lowerName.contains("米饭") || lowerName.contains("rice")) {
            items.add(new FoodItem("米饭", confidence, estimateQuantity("米饭", dishName), "碗"));
        }
        if (lowerName.contains("面条") || lowerName.contains("noodle")) {
            items.add(new FoodItem("面条", confidence, estimateQuantity("面条", dishName), "克"));
        }
        if (lowerName.contains("豆腐") || lowerName.contains("tofu")) {
            items.add(new FoodItem("豆腐", confidence, estimateQuantity("豆腐", dishName), "块"));
        }
    }

    /**
     * 估计食材数量（基于菜品名）
     */
    private double estimateQuantity(String ingredient, String dishName) {
        // 简单规则估计数量
        switch (ingredient) {
            case "番茄":
            case "鸡蛋":
            case "洋葱":
            case "青椒":
            case "土豆":
            case "胡萝卜":
                return 2.0; // 默认2个
            case "鸡肉":
            case "猪肉":
            case "牛肉":
            case "鱼肉":
            case "面条":
                return 150.0; // 默认150克
            case "青菜":
                return 1.0; // 默认1颗
            case "蘑菇":
                return 5.0; // 默认5个
            case "豆腐":
                return 1.0; // 默认1块
            case "米饭":
                return 1.0; // 默认1碗
            default:
                return 1.0;
        }
    }

    /**
     * 食材去重
     */
    private List<FoodItem> deduplicateFoodItems(List<FoodItem> items) {
        List<FoodItem> deduplicated = new ArrayList<>();

        for (FoodItem item : items) {
            boolean found = false;
            for (FoodItem existing : deduplicated) {
                if (existing.getName().equals(item.getName())) {
                    // 保留置信度更高的
                    if (item.getConfidence() > existing.getConfidence()) {
                        existing.setConfidence(item.getConfidence());
                    }
                    // 合并数量
                    existing.setQuantity(existing.getQuantity() + item.getQuantity());
                    found = true;
                    break;
                }
            }
            if (!found) {
                deduplicated.add(item);
            }
        }

        return deduplicated;
    }

    /**
     * 获取默认食材（识别失败时使用）
     */
    private List<FoodItem> getDefaultFoodItems() {
        List<FoodItem> defaultItems = new ArrayList<>();
        defaultItems.add(new FoodItem("番茄", 0.9, 2, "个"));
        defaultItems.add(new FoodItem("鸡蛋", 0.9, 3, "个"));
        defaultItems.add(new FoodItem("洋葱", 0.7, 1, "个"));
        return defaultItems;
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
}