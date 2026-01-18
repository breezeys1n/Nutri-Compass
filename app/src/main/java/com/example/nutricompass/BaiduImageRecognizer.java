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

    private static final String OBJECT_RECOGNITION_URL = "https://aip.baidubce.com/rest/2.0/image-classify/v2/advanced_general";
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
            String result = callObjectRecognitionAPI(imageBase64, token);
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
    private String callObjectRecognitionAPI(String imageBase64, String accessToken) throws Exception {
        String urlStr = OBJECT_RECOGNITION_URL + "?access_token=" + accessToken;

        if (imageBase64.contains(",")) {
            imageBase64 = imageBase64.split(",")[1];
        }

        String params = "image=" + URLEncoder.encode(imageBase64, "UTF-8") +
                "&baike_num=1";  // 获取百科信息，有助于识别

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = params.getBytes("UTF-8");
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            return readStream(conn.getInputStream());
        } else {
            String error = readStream(conn.getErrorStream());
            throw new Exception("物体识别API调用失败: " + responseCode + " - " + error);
        }
    }

    /**
     * 解析识别结果
     */
    private List<FoodItem> parseRecognitionResult(String jsonResult) {
        List<FoodItem> items = new ArrayList<>();

        try {
            JSONObject json = new JSONObject(jsonResult);
            Log.d(TAG, "百度AI原始响应: " + jsonResult.substring(0, Math.min(500, jsonResult.length())));

            if (json.has("error_code")) {
                int errorCode = json.getInt("error_code");
                String errorMsg = json.getString("error_msg");
                Log.e(TAG, "百度AI错误: " + errorCode + " - " + errorMsg);
                return getDefaultFoodItems();
            }

            if (!json.has("result") || json.isNull("result")) {
                Log.w(TAG, "未识别到任何物体");
                return getDefaultFoodItems();
            }

            JSONArray results = json.getJSONArray("result");
            Log.d(TAG, "识别到 " + results.length() + " 个物体");

            // 解析每个物体结果 - 适配新格式
            for (int i = 0; i < results.length(); i++) {
                JSONObject object = results.getJSONObject(i);

                String keyword = "";
                double score = 0.0;
                String root = "";

                // 通用物体识别API返回的字段
                if (object.has("keyword")) {
                    keyword = object.getString("keyword");
                }
                if (object.has("score")) {
                    score = object.getDouble("score");
                }
                if (object.has("root")) {
                    root = object.getString("root");
                }

                Log.d(TAG, "物体 " + (i+1) + ": " + keyword + " (类别: " + root + ", 置信度: " + score + ")");

                // 过滤出食材相关的物体
                if (isFoodItem(keyword, root)) {
                    // 转换食材名称（如"鸡蛋" -> "鸡蛋"）
                    String foodName = normalizeFoodName(keyword);
                    double quantity = estimateQuantityFromObject(foodName, score);
                    String unit = getFoodUnit(foodName);

                    FoodItem foodItem = new FoodItem(foodName, score, quantity, unit);
                    items.add(foodItem);

                    Log.d(TAG, "✅ 提取为食材: " + foodName + " (" + quantity + unit + ")");
                } else {
                    Log.d(TAG, "❌ 忽略非食材: " + keyword);
                }
            }

            // 如果没提取到食材，使用默认值
            if (items.isEmpty()) {
                Log.w(TAG, "未能从识别结果中提取食材，使用默认值");
                return getDefaultFoodItems();
            }

            Log.d(TAG, "最终提取到 " + items.size() + " 种食材");

        } catch (Exception e) {
            Log.e(TAG, "解析识别结果失败: " + e.getMessage(), e);
            return getDefaultFoodItems();
        }

        return items;
    }

    /**
     * 判断是否为食材
     */
    private boolean isFoodItem(String keyword, String root) {
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }

        String lowerKeyword = keyword.toLowerCase();
        String lowerRoot = root != null ? root.toLowerCase() : "";

        // 根据root类别判断
        if (lowerRoot.contains("食材") || lowerRoot.contains("食品") ||
                lowerRoot.contains("水果") || lowerRoot.contains("蔬菜")) {
            return true;
        }

        // 根据关键词判断
        String[] foodKeywords = {
                "鸡蛋", "蛋", "egg", "鸡", "鸡肉", "猪", "猪肉", "牛", "牛肉",
                "鱼", "虾", "蟹", "豆腐", "米饭", "面条", "面包", "牛奶",
                "番茄", "西红柿", "洋葱", "青椒", "土豆", "胡萝卜", "白菜",
                "青菜", "蘑菇", "黄瓜", "茄子", "西兰花", "菠菜", "芹菜"
        };

        for (String food : foodKeywords) {
            if (lowerKeyword.contains(food)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 标准化食材名称
     */
    private String normalizeFoodName(String keyword) {
        String lowerKeyword = keyword.toLowerCase();

        // 食材名称映射
        if (lowerKeyword.contains("鸡蛋") || lowerKeyword.contains("蛋")) {
            return "鸡蛋";
        } else if (lowerKeyword.contains("番茄") || lowerKeyword.contains("西红柿")) {
            return "番茄";
        } else if (lowerKeyword.contains("鸡") && lowerKeyword.contains("肉")) {
            return "鸡肉";
        } else if (lowerKeyword.contains("猪") && lowerKeyword.contains("肉")) {
            return "猪肉";
        } else if (lowerKeyword.contains("牛") && lowerKeyword.contains("肉")) {
            return "牛肉";
        } else if (lowerKeyword.contains("米饭") || lowerKeyword.contains("米")) {
            return "米饭";
        } else if (lowerKeyword.contains("面条") || lowerKeyword.contains("面")) {
            return "面条";
        } else if (lowerKeyword.contains("面包")) {
            return "面包";
        } else if (lowerKeyword.contains("牛奶")) {
            return "牛奶";
        } else if (lowerKeyword.contains("豆腐")) {
            return "豆腐";
        } else if (lowerKeyword.contains("洋葱")) {
            return "洋葱";
        } else if (lowerKeyword.contains("青椒")) {
            return "青椒";
        } else if (lowerKeyword.contains("土豆")) {
            return "土豆";
        } else if (lowerKeyword.contains("胡萝卜")) {
            return "胡萝卜";
        } else if (lowerKeyword.contains("白菜")) {
            return "白菜";
        } else if (lowerKeyword.contains("青菜")) {
            return "青菜";
        } else if (lowerKeyword.contains("蘑菇")) {
            return "蘑菇";
        }

        // 默认返回原名称（去掉可能的后缀）
        return keyword.replace("(食材)", "").replace("(食品)", "").trim();
    }

    /**
     * 根据物体估计食材数量
     */
    private double estimateQuantityFromObject(String foodName, double score) {
        // 根据置信度调整数量估计
        double baseQuantity = getBaseQuantity(foodName);

        // 置信度越高，估计越准确
        if (score > 0.7) {
            return baseQuantity;
        } else if (score > 0.5) {
            return baseQuantity * 0.8; // 减少估计值
        } else {
            return baseQuantity * 0.5; // 显著减少
        }
    }

    /**
     * 获取食材基础数量
     */
    private double getBaseQuantity(String foodName) {
        switch (foodName) {
            case "鸡蛋":
                return 2.0;
            case "番茄":
            case "西红柿":
                return 2.0;
            case "洋葱":
                return 1.0;
            case "鸡肉":
            case "猪肉":
            case "牛肉":
                return 150.0;
            case "米饭":
                return 200.0;
            case "面条":
                return 150.0;
            case "豆腐":
                return 1.0; // 块
            case "牛奶":
                return 250.0; // ml
            case "面包":
                return 2.0; // 片
            default:
                return 100.0; // 默认100克
        }
    }

    /**
     * 获取食材单位
     */
    private String getFoodUnit(String foodName) {
        if (foodName.contains("鸡蛋") || foodName.contains("番茄") ||
                foodName.contains("洋葱") || foodName.contains("面包") ||
                foodName.contains("豆腐")) {
            return "个";
        } else if (foodName.contains("鸡肉") || foodName.contains("猪肉") ||
                foodName.contains("牛肉") || foodName.contains("米饭") ||
                foodName.contains("面条")) {
            return "克";
        } else if (foodName.contains("牛奶")) {
            return "毫升";
        } else if (foodName.contains("白菜") || foodName.contains("青菜")) {
            return "颗";
        } else {
            return "克";
        }
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