package com.example.nutricompass.knowledgegraph;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * 知识图谱API
 * 从JSON文件加载菜谱数据，构建食材节点和菜谱索引
 */
public class KnowledgeGraphAPI {
    private final IngredientClassifier classifier;
    private final Map<String, Ingredient> ingredientMap;      // 食材名称 -> 食材节点
    private final Map<String, List<Recipe>> ingredientToRecipes; // 食材名称 -> 菜谱列表（倒排索引）
    private final List<Recipe> allRecipes;
    private final Set<String> addedRecipeUrls = new HashSet<>();

    public KnowledgeGraphAPI() {
        classifier = new IngredientClassifier();
        ingredientMap = new HashMap<>();
        ingredientToRecipes = new HashMap<>();
        allRecipes = new ArrayList<>();
        // 默认无数据，需要调用loadFromJsonFile或loadFromJsonString加载
    }
    public List<Recipe> searchRecipes(List<String> requiredIngredients){
        List<Recipe> recipes=searchRecipesByExactIngredients(requiredIngredients);
        if(recipes.isEmpty()){
            recipes=recommendSimilarRecipes(requiredIngredients);
        }
        if (recipes.size() > 3) {
            recipes = recipes.subList(0, 3);
        }
        return recipes;

    }
    public List<Recipe> searchRecipesByExactIngredients(List<String> requiredIngredients) {
        if (requiredIngredients == null || requiredIngredients.isEmpty()) {
            return Collections.emptyList();
        }

        // 取第一个食材的菜谱列表作为初始集合
        String firstIng = requiredIngredients.get(0);
        List<Recipe> candidates = ingredientToRecipes.getOrDefault(firstIng, Collections.emptyList());

        // 逐步取交集
        for (int i = 1; i < requiredIngredients.size(); i++) {
            String ing = requiredIngredients.get(i);
            List<Recipe> recipesForIng = ingredientToRecipes.getOrDefault(ing, Collections.emptyList());
            Set<Recipe> set = new HashSet<>(candidates);
            set.retainAll(recipesForIng); // 保留同时存在于两个列表中的菜谱
            candidates = new ArrayList<>(set);
            if (candidates.isEmpty()) {
                break;
            }
        }
        return candidates;
    }
    /**
     * 从Android assets目录加载recipes.json文件
     * @param context 应用上下文
     */
    public void loadFromAssets(Context context) {
        try {
            InputStream is = context.getAssets().open("recipes.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            loadFromJsonString(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 从JSON字符串加载菜谱数据
     * @param jsonString JSON数组字符串，每个元素是一个菜谱对象
     */
    public void loadFromJsonString(String jsonString) {
        try {
            JSONArray recipesArray = new JSONArray(jsonString);
            for (int i = 0; i < recipesArray.length(); i++) {
                JSONObject recipeObj = recipesArray.getJSONObject(i);

                // 唯一标识（优先 url，若无则用 title+索引）
                String uniqueKey;
                if (recipeObj.has("url")) {
                    uniqueKey = recipeObj.getString("url");
                } else {
                    uniqueKey = recipeObj.getString("title") + "_" + i;
                }

                String title = recipeObj.getString("title");
                // 获取菜系，若无则设为空字符串
                String cuisine = recipeObj.optString("cuisine", "");

                // 解析食材
                JSONArray ingredientsArray = recipeObj.getJSONArray("ingredients");
                List<String> ingredientNames = new ArrayList<>();
                for (int j = 0; j < ingredientsArray.length(); j++) {
                    JSONObject ingObj = ingredientsArray.getJSONObject(j);
                    String ingName = ingObj.getString("name");
                    ingredientNames.add(ingName);
                }

                // 解析步骤
                List<String> steps = new ArrayList<>();
                if (recipeObj.has("steps")) {
                    JSONArray stepsArray = recipeObj.getJSONArray("steps");
                    for (int j = 0; j < stepsArray.length(); j++) {
                        steps.add(stepsArray.getString(j));
                    }
                }

                // 添加到图谱
                addRecipe(uniqueKey, title, cuisine, ingredientNames, steps);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void addRecipe(String id, String name, String cuisine, List<String> ingredientNames, List<String> steps) {
        // 去重检查
        if (addedRecipeUrls.contains(id)) {
            return;
        }

        Recipe recipe = new Recipe(name, cuisine, ingredientNames, steps);
        allRecipes.add(recipe);
        addedRecipeUrls.add(id);

        // 建立倒排索引
        for (String ingName : ingredientNames) {
            if (!ingredientMap.containsKey(ingName)) {
                String category = classifier.classify(ingName);
                ingredientMap.put(ingName, new Ingredient(ingName, category));
            }
            ingredientToRecipes.computeIfAbsent(ingName, k -> new ArrayList<>()).add(recipe);
        }
    }
    public List<Recipe> recommendSimilarRecipes(String unknownIngredient) {
        return recommendSimilarRecipes(Collections.singletonList(unknownIngredient));
    }
    private static class RecipeScore {
        Recipe recipe;
        int matchedCount;
        double ratio;

        RecipeScore(Recipe recipe, int matchedCount, double ratio) {
            this.recipe = recipe;
            this.matchedCount = matchedCount;
            this.ratio = ratio;
        }
    }
    /**
     * 根据多个未知食材推荐近似菜谱，优先推荐同类食材占比高的菜谱
     * @param unknownIngredients 输入食材名称列表
     * @return 按相关性排序的菜谱列表（优先比例高，其次同类食材数量多）
     */
    public List<Recipe> recommendSimilarRecipes(List<String> unknownIngredients) {
        // 处理空输入
        if (unknownIngredients == null || unknownIngredients.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 对每个输入食材进行分类，收集所有有效类别
        Set<String> categories = new HashSet<>();
        for (String ing : unknownIngredients) {
            String category = classifier.classify(ing);
            if (!"其他".equals(category)) {
                categories.add(category);
            }
        }

        // 如果所有食材都无法分类，则无法推荐
        if (categories.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 找出这些类别下的所有已知食材（去重）
        Set<String> similarIngredients = new HashSet<>();
        for (Ingredient ing : ingredientMap.values()) {
            if (categories.contains(ing.getCategory())) {
                similarIngredients.add(ing.getName());
            }
        }

        // 3. 如果输入食材本身已存在于图谱中，也加入相似食材集合
        for (String ing : unknownIngredients) {
            if (ingredientMap.containsKey(ing)) {
                similarIngredients.add(ing);
            }
        }

        // 4. 收集所有可能相关的菜谱（只要包含至少一个相似食材）
        Set<Recipe> candidateSet = new HashSet<>();
        for (String ing : similarIngredients) {
            List<Recipe> recipes = ingredientToRecipes.get(ing);
            if (recipes != null) {
                candidateSet.addAll(recipes);
            }
        }

        // 5. 计算每个候选菜谱的得分（同类食材比例）
        List<RecipeScore> scoredRecipes = new ArrayList<>();
        for (Recipe recipe : candidateSet) {
            int matchedCount = 0;
            int totalCount = recipe.getIngredients().size();
            for (String ing : recipe.getIngredients()) {
                if (similarIngredients.contains(ing)) {
                    matchedCount++;
                }
            }
            // 比例得分，注意 totalCount 可能为0（理论上不应发生）
            double ratio = (totalCount == 0) ? 0 : (double) matchedCount / totalCount;
            scoredRecipes.add(new RecipeScore(recipe, matchedCount, ratio));
        }

        // 6. 按比例降序，比例相同时按同类食材数量降序排序
        scoredRecipes.sort((r1, r2) -> {
            if (Double.compare(r2.ratio, r1.ratio) != 0) {
                return Double.compare(r2.ratio, r1.ratio);
            }
            return Integer.compare(r2.matchedCount, r1.matchedCount);
        });

        // 提取排序后的菜谱列表
        List<Recipe> sortedRecipes = new ArrayList<>();
        for (RecipeScore rs : scoredRecipes) {
            sortedRecipes.add(rs.recipe);
        }
        return sortedRecipes;
    }
    // 获取所有菜谱
    public List<Recipe> getAllRecipes() {
        return allRecipes;
    }

    // 内部食材节点类
    private static class Ingredient {
        private String name;
        private String category;

        public Ingredient(String name, String category) {
            this.name = name;
            this.category = category;
        }

        public String getName() { return name; }
        public String getCategory() { return category; }
    }

    // 菜谱节点类
    public static class Recipe {
        private String name;
        private List<String> ingredients;
        private  List<String> steps;
        private String cuisine;

        public Recipe(String name, String cuisine, List<String> ingredients, List<String> steps) {
            this.name = name;
            this.cuisine = cuisine;
            this.ingredients = ingredients;
            this.steps = steps;
        }

        public String getName() { return name; }
        public List<String> getIngredients() { return ingredients; }
        public List<String> getSteps() { return steps; }
        public String getCuisine() { return cuisine; }

        @Override
        public String toString() {
            return "Recipe{name='" + name + "', ingredients=" + ingredients + "}";
        }
    }
}