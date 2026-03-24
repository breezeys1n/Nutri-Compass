package com.example.nutricompass;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlavorMigrationClient {
    private static final String TAG = "FlavorMigration";
    private UnifiedConfig unifiedConfig;

    public interface MigrationCallback {
        void onSuccess(JSONObject migratedRecipe);
        void onError(String error);
    }

    public FlavorMigrationClient(Context context) {
        this.unifiedConfig = UnifiedConfig.getInstance(context);
    }

    public void migrateRecipe(JSONObject originalRecipe,
                              String targetFlavor,
                              String healthGoal,
                              List<String> ingredients,
                              MigrationCallback callback) {

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
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

                String urlStr = unifiedConfig.getFlavorModelUrl();
                Log.d(TAG, "请求URL: " + urlStr);

                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(120000);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "响应码: " + responseCode);

                String responseBody;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (InputStream is = conn.getInputStream();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        responseBody = response.toString();
                    }
                } else {
                    try (InputStream es = conn.getErrorStream();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8))) {
                        StringBuilder error = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            error.append(line);
                        }
                        responseBody = error.toString();
                    }
                }

                Log.d(TAG, "========== 服务器完整响应 ==========");
                Log.d(TAG, "响应体: " + responseBody);
                Log.d(TAG, "=================================");

                if (responseCode == 200) {
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    String rawContent = jsonResponse.optString("content", "");
                    Log.d(TAG, "========== 模型返回的content字段 ==========");
                    Log.d(TAG, rawContent);
                    Log.d(TAG, "==========================================");
                    parseAndMapResult(rawContent, callback);
                } else {
                    callback.onError("服务器错误: " + responseCode + " - " + responseBody);
                }

            } catch (java.net.SocketTimeoutException e) {
                Log.e(TAG, "连接超时: " + e.getMessage());
                callback.onError("连接超时，请检查网络");
            } catch (java.net.ConnectException e) {
                Log.e(TAG, "连接失败: " + e.getMessage());
                callback.onError("无法连接到服务: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "迁移流程失败", e);
                callback.onError(e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    /**
     * 构建 Prompt
     */
    private String buildFinetunedPrompt(JSONObject originalRecipe, String targetFlavor, List<String> ingredients) {
        String ingredientList = String.join("、", ingredients);

        String instruction = "作为膳愈大厨，请严格根据以下食材制作一道地道的" + targetFlavor +
                "，赋予一个既符合核心食材又具" + targetFlavor + "风味的菜名：" + ingredientList +
                "。 输出要求：必须返回标准 JSON 格式，包含 title, cuisine, ingredients, steps, logic 字段。";

        StringBuilder inputBuilder = new StringBuilder();
        inputBuilder.append("食材：").append(ingredientList)
                .append(" | 目标菜系：").append(targetFlavor);

        return instruction + "\n" + "input: " + inputBuilder.toString() + "\n" + "output: ";
    }

    private void parseAndMapResult(String content, MigrationCallback callback) {
        try {
            Log.d(TAG, "========== 风味迁移模型原始返回 ==========");
            Log.d(TAG, "原始内容长度: " + content.length());
            Log.d(TAG, "原始内容: " + content);

            String jsonStr = extractJson(content);

            JSONObject modelOutput;
            if (jsonStr != null) {
                Log.d(TAG, "成功提取JSON: " + jsonStr);
                modelOutput = new JSONObject(jsonStr);
            } else {
                String title = extractTitleFromContent(content);
                modelOutput = new JSONObject();
                modelOutput.put("title", title);
                modelOutput.put("cuisine", "");
                modelOutput.put("logic", content);
                modelOutput.put("ingredients", new JSONArray());
                modelOutput.put("steps", new JSONArray());
            }

            JSONObject appRecipe = new JSONObject();
            appRecipe.put("name", modelOutput.optString("title", "未命名创新菜"));
            appRecipe.put("description", modelOutput.optString("logic", "风味改良成功"));

            // 处理 ingredients - 确保拆分成多个独立食材
            Object ingredientsObj = modelOutput.opt("ingredients");
            JSONArray ingredientsArray = new JSONArray();

            if (ingredientsObj instanceof JSONArray) {
                JSONArray origArray = (JSONArray) ingredientsObj;
                for (int i = 0; i < origArray.length(); i++) {
                    Object item = origArray.get(i);
                    if (item instanceof JSONObject) {
                        // 已经是 JSONObject，直接添加
                        ingredientsArray.put(item);
                    } else if (item instanceof String) {
                        // 是字符串，可能是多个食材用顿号分隔
                        String ingStr = (String) item;
                        // 按顿号或逗号拆分
                        String[] parts = ingStr.split("[，,、]");
                        for (String part : parts) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) {
                                // 创建 JSONObject
                                JSONObject ingObj = new JSONObject();
                                // 提取名称和用量
                                if (trimmed.contains("(")) {
                                    String[] nameAndAmount = trimmed.split("\\(");
                                    ingObj.put("name", nameAndAmount[0].trim());
                                    String amount = nameAndAmount[1].replace(")", "").trim();
                                    ingObj.put("amount", amount);
                                } else {
                                    ingObj.put("name", trimmed);
                                    ingObj.put("amount", "适量");
                                }
                                ingredientsArray.put(ingObj);
                            }
                        }
                    }
                }
            }
            appRecipe.put("ingredients", ingredientsArray);

            // 处理 steps
            Object stepsObj = modelOutput.opt("steps");
            JSONArray stepsArray;
            if (stepsObj instanceof JSONArray) {
                stepsArray = (JSONArray) stepsObj;
            } else if (stepsObj instanceof String) {
                stepsArray = new JSONArray();
                stepsArray.put(stepsObj.toString());
            } else {
                stepsArray = new JSONArray();
            }
            appRecipe.put("steps", stepsArray);

            JSONObject defaultNutrition = new JSONObject();
            defaultNutrition.put("calories", 0);
            defaultNutrition.put("protein", 0);
            defaultNutrition.put("carbs", 0);
            defaultNutrition.put("fat", 0);
            appRecipe.put("nutrition", defaultNutrition);

            Log.d(TAG, "构建的appRecipe: " + appRecipe.toString());
            callback.onSuccess(appRecipe);

        } catch (Exception e) {
            Log.e(TAG, "解析异常: " + e.getMessage(), e);
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
            Log.e(TAG, "提取JSON失败", e);
        }
        return null;
    }

    /**
     * 从自然语言内容中提取菜名
     */
    private String extractTitleFromContent(String content) {
        try {
            Pattern pattern = Pattern.compile("菜名[：:]\\s*([^\\s|]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Exception e) {
            Log.e(TAG, "提取菜名失败", e);
        }
        return "风味改良食谱";
    }
}