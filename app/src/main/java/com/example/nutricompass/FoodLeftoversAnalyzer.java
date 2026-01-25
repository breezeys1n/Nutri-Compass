package com.example.nutricompass;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class FoodLeftoversAnalyzer {
    private static final String TAG = "FoodLeftoversAnalyzer";
    private static final String API_KEY = "b0b594b7-fb2c-46a1-baa9-64879e030b94";
    private static final String ENDPOINT_ID = "ep-20260119185948-fqfdc";
    private static final String URL_STR = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    public NutritionAnalysis analyzeLeftovers(String beforeMealBase64, String afterMealBase64, String originalIngredients) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", ENDPOINT_ID);

            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");

            JSONArray contentArray = new JSONArray();

            // 给AI的指令：分析餐前餐后照片，计算剩余食物量
            String prompt = "请分析这两张照片：第一张是餐前食物，第二张是餐后剩余食物。\n" +
                    "餐前主要食材有：" + originalIngredients + "\n" +
                    "请根据餐后剩余食物照片，估算：\n" +
                    "1. 剩余食物比例（百分比）\n" +
                    "2. 主要剩余食材是哪些\n" +
                    "3. 对实际摄入量的影响\n" +
                    "4. 营养摄入完成度评价\n\n" +
                    "请以JSON格式返回，包含以下字段：\n" +
                    "- estimated_leftover_percentage: 估算剩余百分比(0-100)\n" +
                    "- leftover_items: 主要剩余食材列表\n" +
                    "- actual_intake_ratio: 实际摄入比例(0.0-1.0)\n" +
                    "- nutrition_impact: 营养影响分析(蛋白质、碳水、脂肪、热量的影响)\n" +
                    "- completion_status: 完成度评价(优秀/良好/一般/不足)\n" +
                    "- suggestions: 后续建议";

            contentArray.put(new JSONObject().put("type", "text").put("text", prompt));

            // 餐前照片
            contentArray.put(new JSONObject().put("type", "image_url")
                    .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + beforeMealBase64)));

            // 餐后照片
            contentArray.put(new JSONObject().put("type", "image_url")
                    .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + afterMealBase64)));

            userMessage.put("content", contentArray);
            messages.put(userMessage);
            requestBody.put("messages", messages);

            URL url = new URL(URL_STR);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.toString().getBytes("UTF-8"));
            }

            if (conn.getResponseCode() == 200) {
                Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                String response = s.hasNext() ? s.next() : "";
                JSONObject jsonResponse = new JSONObject(response);
                String aiContent = jsonResponse.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");

                return parseAnalysisResult(aiContent);
            }
        } catch (Exception e) {
            Log.e(TAG, "豆包分析剩余食物出错: " + e.getMessage());
        }
        return null;
    }

    private NutritionAnalysis parseAnalysisResult(String jsonStr) {
        try {
            // 提取JSON部分
            int start = jsonStr.indexOf("{");
            int end = jsonStr.lastIndexOf("}") + 1;
            if (start >= 0 && end > start) {
                String jsonContent = jsonStr.substring(start, end);
                JSONObject json = new JSONObject(jsonContent);

                NutritionAnalysis analysis = new NutritionAnalysis();
                analysis.setEstimatedLeftoverPercentage(json.optInt("estimated_leftover_percentage", 0));
                analysis.setActualIntakeRatio(json.optDouble("actual_intake_ratio", 1.0));
                analysis.setNutritionImpact(json.optString("nutrition_impact", ""));
                analysis.setCompletionStatus(json.optString("completion_status", ""));
                analysis.setSuggestions(json.optString("suggestions", ""));

                // 解析剩余食材列表
                JSONArray leftoverArray = json.optJSONArray("leftover_items");
                if (leftoverArray != null) {
                    StringBuilder items = new StringBuilder();
                    for (int i = 0; i < leftoverArray.length(); i++) {
                        if (items.length() > 0) items.append(", ");
                        items.append(leftoverArray.getString(i));
                    }
                    analysis.setLeftoverItems(items.toString());
                }

                return analysis;
            }
        } catch (Exception e) {
            Log.e(TAG, "解析分析结果失败: " + e.getMessage());
        }
        return null;
    }
}