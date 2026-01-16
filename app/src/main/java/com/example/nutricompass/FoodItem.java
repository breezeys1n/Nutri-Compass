package com.example.nutricompass;

/**
 * 食材识别结果
 */
public class FoodItem {
    private String name;        // 食材名称
    private double confidence;  // 识别置信度
    private double quantity;    // 估计数量/重量
    private String unit;        // 单位（个、克、毫升等）

    public FoodItem() {
    }

    public FoodItem(String name, double confidence, double quantity, String unit) {
        this.name = name;
        this.confidence = confidence;
        this.quantity = quantity;
        this.unit = unit;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    @Override
    public String toString() {
        return String.format("%s (%.1f%s, 置信度: %.0f%%)", name, quantity, unit, confidence * 100);
    }
}