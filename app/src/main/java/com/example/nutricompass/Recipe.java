package com.example.nutricompass;

import java.util.ArrayList;
import java.util.List;

/**
 * AI生成的食谱
 */
public class Recipe {
    private String name;                    // 食谱名称
    private String description;             // 食谱描述
    private String reason;                  // 推荐理由
    private String weatherCondition;        // 天气条件
    private String userCondition;           // 用户身体状况
    private List<String> ingredients;       // 所需食材
    private List<String> cookingSteps;      // 烹饪步骤
    private NutritionInfo nutrition;        // 营养信息
    private String preparationTime;         // 准备时间
    private String cookingTime;             // 烹饪时间
    private int difficulty;                 // 难度等级 1-5

    public Recipe() {
        this.ingredients = new ArrayList<>();
        this.cookingSteps = new ArrayList<>();
        this.nutrition = new NutritionInfo();
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getWeatherCondition() {
        return weatherCondition;
    }

    public void setWeatherCondition(String weatherCondition) {
        this.weatherCondition = weatherCondition;
    }

    public String getUserCondition() {
        return userCondition;
    }

    public void setUserCondition(String userCondition) {
        this.userCondition = userCondition;
    }

    public List<String> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<String> ingredients) {
        this.ingredients = ingredients;
    }

    public void addIngredient(String ingredient) {
        this.ingredients.add(ingredient);
    }

    public List<String> getCookingSteps() {
        return cookingSteps;
    }

    public void setCookingSteps(List<String> cookingSteps) {
        this.cookingSteps = cookingSteps;
    }

    public void addCookingStep(String step) {
        this.cookingSteps.add(step);
    }

    public NutritionInfo getNutrition() {
        return nutrition;
    }

    public void setNutrition(NutritionInfo nutrition) {
        this.nutrition = nutrition;
    }

    public String getPreparationTime() {
        return preparationTime;
    }

    public void setPreparationTime(String preparationTime) {
        this.preparationTime = preparationTime;
    }

    public String getCookingTime() {
        return cookingTime;
    }

    public void setCookingTime(String cookingTime) {
        this.cookingTime = cookingTime;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * 获取简要营养信息
     */
    public String getBriefNutrition() {
        if (nutrition != null) {
            return String.format("%.0f大卡 | 蛋白%.0fg | 碳水%.0fg | 脂肪%.0fg",
                    nutrition.getCalories(),
                    nutrition.getProtein(),
                    nutrition.getCarbs(),
                    nutrition.getFat());
        }
        return "暂无营养信息";
    }

    @Override
    public String toString() {
        return String.format("%s\n%s\n推荐理由: %s\n营养: %s",
                name, description, reason, getBriefNutrition());
    }
}