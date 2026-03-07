// RecipeRetrievalClient.java
package com.example.nutricompass;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RecipeRetrievalClient {
    private static final String TAG = "RecipeRetrieval";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Gson gson;

    public RecipeRetrievalClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public interface RetrievalCallback {
        void onSuccess(List<ReferenceRecipe> recipes);
        void onError(String error);
    }

    // 返回原始JSON的回调
    public interface RawJsonCallback {
        void onSuccess(List<JSONObject> recipes);
        void onError(String error);
    }

    public void searchRecipes(List<String> ingredients, String healthGoal,
                              String cuisinePref, int topK, RetrievalCallback callback) {
        new Thread(() -> {
            try {
                JsonObject requestBody = new JsonObject();

                JsonArray ingredientsArray = new JsonArray();
                for (String ing : ingredients) {
                    ingredientsArray.add(ing);
                }
                requestBody.add("ingredients", ingredientsArray);

                if (healthGoal != null && !healthGoal.isEmpty()) {
                    requestBody.addProperty("health_goal", healthGoal);
                }

                if (cuisinePref != null && !cuisinePref.isEmpty()) {
                    requestBody.addProperty("cuisine_preference", cuisinePref);
                }

                requestBody.addProperty("top_k", topK);

                Log.d(TAG, "发送RAG请求: " + requestBody.toString());

                Request request = new Request.Builder()
                        .url(Config.RAG_SERVICE_URL)
                        .post(RequestBody.create(requestBody.toString(), JSON))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseStr = response.body().string();
                        Log.d(TAG, "RAG响应: " + responseStr);

                        List<ReferenceRecipe> recipes = parseResponse(responseStr);
                        callback.onSuccess(recipes);
                    } else {
                        callback.onError("服务器错误: " + response.code());
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "RAG检索失败", e);
                callback.onError("网络错误: " + e.getMessage());
            }
        }).start();
    }

    // 获取原始JSON数据的方法
    public void searchRawRecipes(List<String> ingredients, String healthGoal,
                                 String cuisinePref, int topK, RawJsonCallback callback) {
        new Thread(() -> {
            try {
                JsonObject requestBody = new JsonObject();

                JsonArray ingredientsArray = new JsonArray();
                for (String ing : ingredients) {
                    ingredientsArray.add(ing);
                }
                requestBody.add("ingredients", ingredientsArray);

                if (healthGoal != null && !healthGoal.isEmpty()) {
                    requestBody.addProperty("health_goal", healthGoal);
                }

                if (cuisinePref != null && !cuisinePref.isEmpty()) {
                    requestBody.addProperty("cuisine_preference", cuisinePref);
                }

                requestBody.addProperty("top_k", topK);
                requestBody.addProperty("return_raw", true);

                Log.d(TAG, "发送原始RAG请求: " + requestBody.toString());

                Request request = new Request.Builder()
                        .url(Config.RAG_SERVICE_URL)
                        .post(RequestBody.create(requestBody.toString(), JSON))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseStr = response.body().string();
                        Log.d(TAG, "RAG原始响应: " + responseStr);

                        List<JSONObject> recipes = parseRawResponse(responseStr);
                        callback.onSuccess(recipes);
                    } else {
                        callback.onError("服务器错误: " + response.code());
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "RAG检索失败", e);
                callback.onError("网络错误: " + e.getMessage());
            }
        }).start();
    }

    private List<ReferenceRecipe> parseResponse(String jsonStr) {
        List<ReferenceRecipe> recipes = new ArrayList<>();
        try {
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
            JsonArray results = json.getAsJsonArray("results");

            for (int i = 0; i < results.size(); i++) {
                JsonObject item = results.get(i).getAsJsonObject();
                ReferenceRecipe recipe = new ReferenceRecipe();

                recipe.id = item.get("id").getAsString();
                recipe.name = item.get("name").getAsString();
                recipe.cuisine = item.get("cuisine").getAsString();
                recipe.similarityScore = item.get("similarity_score").getAsDouble();

                JsonArray tags = item.getAsJsonArray("health_tags");
                if (tags != null) {
                    for (int j = 0; j < tags.size(); j++) {
                        recipe.healthTags.add(tags.get(j).getAsString());
                    }
                }

                recipes.add(recipe);
            }
        } catch (Exception e) {
            Log.e(TAG, "解析响应失败", e);
        }
        return recipes;
    }

    // 解析原始JSON响应，提取食谱数据
    private List<JSONObject> parseRawResponse(String jsonStr) {
        List<JSONObject> recipes = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(jsonStr);

            if (json.has("results")) {
                JSONArray results = json.getJSONArray("results");
                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.getJSONObject(i);

                    // 如果包含recipe字段，提取出来
                    if (item.has("recipe")) {
                        JSONObject recipe = item.getJSONObject("recipe");
                        recipes.add(recipe);
                    } else {
                        recipes.add(item);
                    }
                }
            }

            Log.d(TAG, "解析到 " + recipes.size() + " 个原始食谱");

        } catch (Exception e) {
            Log.e(TAG, "解析原始响应失败: " + e.getMessage());
        }
        return recipes;
    }

    public static class ReferenceRecipe {
        public String id;
        public String name;
        public String cuisine;
        public double similarityScore;
        public List<String> healthTags = new ArrayList<>();

        @Override
        public String toString() {
            return String.format("%s (%.0f%%) - %s",
                    name, similarityScore * 100, cuisine);
        }
    }
}