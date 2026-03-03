package com.example.nutricompass;

import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;
/**
 * AI生成的食谱
 */
public class Recipe implements Serializable{
    private static final long serialVersionUID = 1L;
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
    private int id;                      // 用于数据库的唯一ID
    private String date;                 // 创建日期
    private int calories;                // 总热量（为了与历史记录兼容）
    private double protein;      // 蛋白质(g)
    private double carbs;        // 碳水化合物(g)
    private double fat;          // 脂肪(g)
    private double fiber;        // 膳食纤维(g)
    private String mealType;      // 早餐/午餐/晚餐/加餐
    public Recipe() {
        this.ingredients = new ArrayList<>();
        this.cookingSteps = new ArrayList<>();
        this.nutrition = new NutritionInfo();
        this.date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
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
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getCalories() {
        return calories;
    }

    public void setCalories(int calories) {
        this.calories = calories;
    }

    // 为了与历史记录兼容，添加一个获取简要标题的方法
    public String getTitle() {
        return name;  // 使用现有的 name 字段作为标题
    }

    public void setTitle(String title) {
        this.name = title;  // 设置 name 字段
    }
    public double getProtein() { return protein; }
    public void setProtein(double protein) { this.protein = protein; }

    public double getCarbs() { return carbs; }
    public void setCarbs(double carbs) { this.carbs = carbs; }

    public double getFat() { return fat; }
    public void setFat(double fat) { this.fat = fat; }

    public double getFiber() { return fiber; }
    public void setFiber(double fiber) { this.fiber = fiber; }

    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }
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