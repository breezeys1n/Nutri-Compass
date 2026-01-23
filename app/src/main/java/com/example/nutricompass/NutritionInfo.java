package com.example.nutricompass;
import java.io.Serializable;
/**
 * 营养信息
 */
public class NutritionInfo implements Serializable{
    private static final long serialVersionUID = 1L;
    private double calories;    // 卡路里
    private double protein;     // 蛋白质
    private double carbs;       // 碳水化合物
    private double fat;         // 脂肪
    private double fiber;       // 膳食纤维
    private double sodium;      // 钠

    public NutritionInfo() {
    }

    public NutritionInfo(double calories, double protein, double carbs, double fat) {
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
    }

    // Getters and Setters
    public double getCalories() {
        return calories;
    }

    public void setCalories(double calories) {
        this.calories = calories;
    }

    public double getProtein() {
        return protein;
    }

    public void setProtein(double protein) {
        this.protein = protein;
    }

    public double getCarbs() {
        return carbs;
    }

    public void setCarbs(double carbs) {
        this.carbs = carbs;
    }

    public double getFat() {
        return fat;
    }

    public void setFat(double fat) {
        this.fat = fat;
    }

    public double getFiber() {
        return fiber;
    }

    public void setFiber(double fiber) {
        this.fiber = fiber;
    }

    public double getSodium() {
        return sodium;
    }

    public void setSodium(double sodium) {
        this.sodium = sodium;
    }
}