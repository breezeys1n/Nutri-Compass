package com.example.nutricompass;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class DoubaoImageRecognizer {
    private static final String TAG = "DoubaoRecognizer";
    private static final String API_KEY = "b0b594b7-fb2c-46a1-baa9-64879e030b94";
    private static final String ENDPOINT_ID = "ep-20260119185948-fqfdc";
    private static final String URL_STR = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    // 修改返回值类型为 String，避免引用 FoodItem
    public String recognizeFood(String imageBase64) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", ENDPOINT_ID);

            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");

            JSONArray contentArray = new JSONArray();
            // 强力指令：只准出食材列表
            contentArray.put(new JSONObject().put("type", "text")
                    .put("text", "请仅识别图片中的主要食材，以逗号分隔返回，不要输出任何额外文字。"));
            contentArray.put(new JSONObject().put("type", "image_url")
                    .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + imageBase64)));

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
                return jsonResponse.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
            }
        } catch (Exception e) {
            Log.e(TAG, "豆包识别出错: " + e.getMessage());
        }
        return "未能识别食材";
    }
}