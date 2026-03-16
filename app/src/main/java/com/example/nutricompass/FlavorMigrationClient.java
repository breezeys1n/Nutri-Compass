package com.example.nutricompass;

import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.List;

public class FlavorMigrationClient {
    private static final String TAG = "FlavorMigration";

    public interface MigrationCallback {
        void onSuccess(JSONObject migratedRecipe);
        void onError(String error);
    }

    public void migrateRecipe(JSONObject originalRecipe,
                              String targetFlavor,
                              String healthGoal,
                              List<String> existingIngredients,
                              MigrationCallback callback) {

        new Thread(() -> {
            try {
                String prompt = buildPrompt(originalRecipe, targetFlavor,
                        healthGoal, existingIngredients);

                JSONObject requestBody = new JSONObject();
                requestBody.put("prompt", prompt);
                requestBody.put("temperature", 0.7);
                requestBody.put("top_k", 40);
                requestBody.put("top_p", 0.9);
                requestBody.put("max_tokens", 2048);
                requestBody.put("stop", "[\"</s>\", \"\\n\\n\"]");

                Log.d(TAG, "调用风味迁移模型: " + Config.FLAVOR_MODEL_URL);
                Log.d(TAG, "提示词: " + prompt);

                URL url = new URL(Config.FLAVOR_MODEL_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(30000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "响应码: " + responseCode);

                if (responseCode == 200) {
                    Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                    String response = s.hasNext() ? s.next() : "";
                    Log.d(TAG, "模型响应: " + response);

                    JSONObject jsonResponse = new JSONObject(response);
                    String content = jsonResponse.getString("content");

                    String jsonContent = extractJson(content);
                    if (jsonContent != null) {
                        callback.onSuccess(new JSONObject(jsonContent));
                    } else {
                        JSONObject fallback = new JSONObject();
                        fallback.put("description", content);
                        callback.onSuccess(fallback);
                    }
                } else {
                    Scanner s = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A");
                    String error = s.hasNext() ? s.next() : "";
                    callback.onError("HTTP " + responseCode + ": " + error);
                }

            } catch (Exception e) {
                Log.e(TAG, "调用失败", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private String buildPrompt(JSONObject originalRecipe,
                               String targetFlavor,
                               String healthGoal,
                               List<String> ingredients) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位风味迁移专家。请将以下食谱改良为")
                .append(targetFlavor).append("风味，同时满足健康目标：")
                .append(healthGoal).append("。\n\n");

        prompt.append("【原始食谱】\n");
        prompt.append("菜名：").append(originalRecipe.optString("name", "未知")).append("\n");

        JSONArray ingArray = originalRecipe.optJSONArray("ingredients");
        if (ingArray != null) {
            prompt.append("食材：\n");
            for (int i = 0; i < ingArray.length(); i++) {
                try {
                    JSONObject ing = ingArray.getJSONObject(i);
                    prompt.append("- ").append(ing.optString("name"))
                            .append(" ").append(ing.optString("amount", "适量")).append("\n");
                } catch (Exception e) {
                    prompt.append("- ").append(ingArray.optString(i)).append("\n");
                }
            }
        }

        JSONArray steps = originalRecipe.optJSONArray("steps");
        if (steps != null) {
            prompt.append("步骤：\n");
            for (int i = 0; i < steps.length(); i++) {
                prompt.append(i+1).append(". ").append(steps.optString(i)).append("\n");
            }
        }

        prompt.append("\n【可用食材】\n");
        prompt.append(String.join("、", ingredients)).append("\n\n");

        prompt.append("请输出改良后的食谱JSON格式：\n");
        prompt.append("{\n");
        prompt.append("  \"name\": \"新菜名\",\n");
        prompt.append("  \"description\": \"简要描述\",\n");
        prompt.append("  \"ingredients\": [\n");
        prompt.append("    {\"name\": \"食材1\", \"amount\": \"用量\"},\n");
        prompt.append("    {\"name\": \"食材2\", \"amount\": \"用量\"}\n");
        prompt.append("  ],\n");
        prompt.append("  \"steps\": [\"步骤1\", \"步骤2\", \"步骤3\"],\n");
        prompt.append("  \"nutrition\": {\n");
        prompt.append("    \"calories\": 300,\n");
        prompt.append("    \"protein\": 15,\n");
        prompt.append("    \"carbs\": 25,\n");
        prompt.append("    \"fat\": 10\n");
        prompt.append("  }\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }
}