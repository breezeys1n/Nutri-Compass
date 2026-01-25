package com.example.nutricompass;

import java.io.Serializable;

/**
 * 营养分析结果
 */
public class NutritionAnalysis implements Serializable {
    private static final long serialVersionUID = 1L;

    private int estimatedLeftoverPercentage;  // 估算剩余百分比
    private String leftoverItems;            // 剩余食材
    private double actualIntakeRatio;        // 实际摄入比例
    private String nutritionImpact;          // 营养影响分析
    private String completionStatus;         // 完成度评价
    private String suggestions;              // 后续建议
    private int caloriesConsumed;           // 估算摄入热量
    private int proteinConsumed;            // 估算摄入蛋白质
    private int carbsConsumed;              // 估算摄入碳水
    private int fatConsumed;                // 估算摄入脂肪

    public NutritionAnalysis() {
    }

    // Getters and Setters
    public int getEstimatedLeftoverPercentage() {
        return estimatedLeftoverPercentage;
    }

    public void setEstimatedLeftoverPercentage(int estimatedLeftoverPercentage) {
        this.estimatedLeftoverPercentage = estimatedLeftoverPercentage;
    }

    public String getLeftoverItems() {
        return leftoverItems;
    }

    public void setLeftoverItems(String leftoverItems) {
        this.leftoverItems = leftoverItems;
    }

    public double getActualIntakeRatio() {
        return actualIntakeRatio;
    }

    public void setActualIntakeRatio(double actualIntakeRatio) {
        this.actualIntakeRatio = actualIntakeRatio;
    }

    public String getNutritionImpact() {
        return nutritionImpact;
    }

    public void setNutritionImpact(String nutritionImpact) {
        this.nutritionImpact = nutritionImpact;
    }

    public String getCompletionStatus() {
        return completionStatus;
    }

    public void setCompletionStatus(String completionStatus) {
        this.completionStatus = completionStatus;
    }

    public String getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(String suggestions) {
        this.suggestions = suggestions;
    }

    public int getCaloriesConsumed() {
        return caloriesConsumed;
    }

    public void setCaloriesConsumed(int caloriesConsumed) {
        this.caloriesConsumed = caloriesConsumed;
    }

    public int getProteinConsumed() {
        return proteinConsumed;
    }

    public void setProteinConsumed(int proteinConsumed) {
        this.proteinConsumed = proteinConsumed;
    }

    public int getCarbsConsumed() {
        return carbsConsumed;
    }

    public void setCarbsConsumed(int carbsConsumed) {
        this.carbsConsumed = carbsConsumed;
    }

    public int getFatConsumed() {
        return fatConsumed;
    }

    public void setFatConsumed(int fatConsumed) {
        this.fatConsumed = fatConsumed;
    }
}