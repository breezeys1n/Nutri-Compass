// FlavorMigrationClient.java
package com.example.nutricompass;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

public class FlavorMigrationClient {
    private static final String TAG = "FlavorMigration";
    private UnifiedConfig unifiedConfig;

    public interface MigrationCallback {
        void onSuccess(JSONObject migratedRecipe);
        void onError(String error);
    }

    // 构造函数，添加Context参数
    public FlavorMigrationClient(Context context) {
        this.unifiedConfig = UnifiedConfig.getInstance(context);
    }

    public void migrateRecipe(JSONObject originalRecipe,
                              String targetFlavor,
                              String healthGoal,
                              List<String> ingredients,
                              MigrationCallback callback) {

        new Thread(() -> {
            try {
                // 构建Prompt
                String prompt = buildFinetunedPrompt(originalRecipe, targetFlavor, ingredients);

                JSONObject requestBody = new JSONObject();
                requestBody.put("prompt", prompt);
                requestBody.put("temperature", 0.2);
                requestBody.put("n_predict", 1024);

                JSONArray stopWords = new JSONArray();
                stopWords.put("</s>");
                stopWords.put("\n\n");
                requestBody.put("stop", stopWords);

                Log.d(TAG, "Prompt发送内容: " + prompt);

                // 使用UnifiedConfig获取URL
                URL url = new URL(unifiedConfig.getFlavorModelUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(45000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                    String response = s.hasNext() ? s.next() : "";

                    JSONObject jsonResponse = new JSONObject(response);
                    String rawContent = jsonResponse.getString("content").trim();

                    parseAndMapResult(rawContent, callback);
                } else {
                    callback.onError("服务器错误: " + responseCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "迁移流程失败", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    /**
     * 构建 Prompt
     */
    private String buildFinetunedPrompt(JSONObject originalRecipe, String targetFlavor, List<String> ingredients) {
        String ingredientList = String.join("、", ingredients);

        // 1. Instruction
        String instruction = "作为膳愈大厨，请严格根据以下食材制作一道地道的" + targetFlavor +
                "，赋予一个既符合核心食材又具" + targetFlavor + "风味的菜名：" + ingredientList +
                "。 输出要求：必须返回标准 JSON 格式，包含 title, cuisine, ingredients, steps, logic 字段。";

        // 2. Input - 移除原始食谱参考
        StringBuilder inputBuilder = new StringBuilder();
        inputBuilder.append("食材：").append(ingredientList)
                .append(" | 目标菜系：").append(targetFlavor);

        // 注意：这里不再添加原始食谱信息

        return instruction + "\n" + "input: " + inputBuilder.toString() + "\n" + "output: ";
    }

    private void parseAndMapResult(String content, MigrationCallback callback) {
        try {
            String jsonStr = extractJson(content);
            if (jsonStr == null) {
                callback.onError("模型返回非JSON内容");
                return;
            }

            JSONObject modelOutput = new JSONObject(jsonStr);
            JSONObject appRecipe = new JSONObject();

            // 字段映射：title -> name, logic -> description
            appRecipe.put("name", modelOutput.optString("title", "未命名创新菜"));
            appRecipe.put("description", modelOutput.optString("logic", "风味改良成功"));
            appRecipe.put("ingredients", modelOutput.optJSONArray("ingredients"));
            appRecipe.put("steps", modelOutput.optJSONArray("steps"));

            // 营养字段补全
            JSONObject defaultNutrition = new JSONObject();
            defaultNutrition.put("calories", 0);
            defaultNutrition.put("protein", 0);
            appRecipe.put("nutrition", defaultNutrition);

            callback.onSuccess(appRecipe);
        } catch (Exception e) {
            callback.onError("解析异常: " + e.getMessage());
        }
    }

    private String extractJson(String text) {
        try {
            int start = text.indexOf("{");
            int end = text.lastIndexOf("}");
            if (start != -1 && end != -1 && end > start) {
                return text.substring(start, end + 1);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}