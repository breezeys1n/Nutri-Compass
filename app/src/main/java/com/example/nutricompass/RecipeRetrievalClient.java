// RecipeRetrievalClient.java
package com.example.nutricompass;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
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
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public interface RetrievalCallback {
        void onSuccess(List<ReferenceRecipe> recipes);
        void onError(String error);
    }

    public void searchRecipes(List<String> ingredients, String healthGoal,
                              String cuisinePref, int topK, RetrievalCallback callback) {
        new Thread(() -> {
            try {
                // 构建请求体
                JsonObject requestBody = new JsonObject();

                // 食材列表
                JsonArray ingredientsArray = new JsonArray();
                for (String ing : ingredients) {
                    ingredientsArray.add(ing);
                }
                requestBody.add("ingredients", ingredientsArray);

                // 健康目标（如果有）
                if (healthGoal != null && !healthGoal.isEmpty()) {
                    requestBody.addProperty("health_goal", healthGoal);
                }

                // 菜系偏好（如果有）
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

                // 解析健康标签
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