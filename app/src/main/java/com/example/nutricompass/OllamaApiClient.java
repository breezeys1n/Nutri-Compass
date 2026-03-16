package com.example.nutricompass;

import android.util.Log;
import okhttp3.*;
import java.io.IOException;
import org.json.JSONObject;

public class OllamaApiClient {
    private static final String TAG = "OllamaApiClient";
    private static final String OLLAMA_BASE_URL = "http://10.133.130.187:11434";
    private static final String MODEL_NAME = "qwen2.5:7b";
    private final OkHttpClient client;

    public interface StreamResponseCallback {
        void onNewToken(String token);
        void onComplete(String fullResponse);
        void onError(String error);
    }

    public OllamaApiClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public void streamGenerate(String prompt, final StreamResponseCallback callback) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", MODEL_NAME);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", true); // 关键：启用流式响应

            Request request = new Request.Builder()
                    .url(OLLAMA_BASE_URL + "/api/generate")
                    .post(RequestBody.create(
                            requestBody.toString(),
                            MediaType.parse("application/json")
                    ))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "请求失败", e);
                    callback.onError("网络请求失败: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        callback.onError("服务器响应错误: " + response.code());
                        response.close();
                        return;
                    }

                    StringBuilder fullResponse = new StringBuilder();
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            // 逐行读取流式响应
                            java.io.BufferedReader reader = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(responseBody.byteStream())
                            );
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.trim().isEmpty()) continue;
                                // 解析 Ollama 的流式 JSON 响应
                                try {
                                    JSONObject json = new JSONObject(line);
                                    if (json.has("response")) {
                                        String token = json.getString("response");
                                        if (!token.isEmpty()) {
                                            fullResponse.append(token);
                                            callback.onNewToken(token);
                                        }
                                    }
                                    if (json.has("done") && json.getBoolean("done")) {
                                        callback.onComplete(fullResponse.toString());
                                        break;
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "解析响应行失败: " + line, e);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "处理响应流失败", e);
                        callback.onError("处理响应时出错: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "构建请求失败", e);
            callback.onError("构建请求失败: " + e.getMessage());
        }
    }
}
