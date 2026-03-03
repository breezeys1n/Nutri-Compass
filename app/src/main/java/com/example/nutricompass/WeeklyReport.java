package com.example.nutricompass;

import java.util.List;
import java.util.Map;

public class WeeklyReport {
    private String startDate;
    private String endDate;
    private String generateTime;

    // 基础统计
    private int totalDays;
    private int totalMeals;
    private int uniqueRecipes;

    // 营养汇总
    private NutritionSummary weeklyTotal;
    private NutritionSummary dailyAvg;
    private NutritionRatio ratio;

    // 趋势数据
    private List<Double> calorieTrend;
    private List<Double> proteinTrend;
    private List<Double> carbsTrend;
    private List<Double> fatTrend;

    // 分析结果
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> suggestions;

    // 每日明细
    private Map<String, DailyNutrition> dailyDetails;

    /**
     * 内部类：营养汇总
     */
    public static class NutritionSummary {
        private double calories;
        private double protein;
        private double carbs;
        private double fat;
        private double fiber;

        // 默认构造函数
        public NutritionSummary() {
        }

        // 带参数的构造函数
        public NutritionSummary(double calories, double protein, double carbs, double fat, double fiber) {
            this.calories = calories;
            this.protein = protein;
            this.carbs = carbs;
            this.fat = fat;
            this.fiber = fiber;
        }

        // Getters
        public double getCalories() { return calories; }
        public double getProtein() { return protein; }
        public double getCarbs() { return carbs; }
        public double getFat() { return fat; }
        public double getFiber() { return fiber; }

        // Setters
        public void setCalories(double calories) { this.calories = calories; }
        public void setProtein(double protein) { this.protein = protein; }
        public void setCarbs(double carbs) { this.carbs = carbs; }
        public void setFat(double fat) { this.fat = fat; }
        public void setFiber(double fiber) { this.fiber = fiber; }

        @Override
        public String toString() {
            return String.format("热量:%.0f, 蛋白质:%.1f, 碳水:%.1f, 脂肪:%.1f, 纤维:%.1f",
                    calories, protein, carbs, fat, fiber);
        }
    }

    /**
     * 内部类：营养比例
     */
    public static class NutritionRatio {
        private double protein;
        private double carbs;
        private double fat;

        // 默认构造函数
        public NutritionRatio() {
        }

        // 带参数的构造函数
        public NutritionRatio(double protein, double carbs, double fat) {
            this.protein = protein;
            this.carbs = carbs;
            this.fat = fat;
        }

        // Getters
        public double getProtein() { return protein; }
        public double getCarbs() { return carbs; }
        public double getFat() { return fat; }

        // Setters
        public void setProtein(double protein) { this.protein = protein; }
        public void setCarbs(double carbs) { this.carbs = carbs; }
        public void setFat(double fat) { this.fat = fat; }

        // 格式化为百分比
        public String getProteinPercent() {
            return String.format("%.0f%%", protein * 100);
        }
        public String getCarbsPercent() {
            return String.format("%.0f%%", carbs * 100);
        }
        public String getFatPercent() {
            return String.format("%.0f%%", fat * 100);
        }

        @Override
        public String toString() {
            return String.format("蛋白质:%.1f%%, 碳水:%.1f%%, 脂肪:%.1f%%",
                    protein * 100, carbs * 100, fat * 100);
        }
    }

    /**
     * 内部类：每日营养明细
     */
    public static class DailyNutrition {
        private String date;
        private double calories;
        private double protein;
        private double carbs;
        private double fat;
        private double fiber;
        private int mealCount;
        private List<String> recipeTitles;

        // 默认构造函数
        public DailyNutrition() {
        }

        // Getters
        public String getDate() { return date; }
        public double getCalories() { return calories; }
        public double getProtein() { return protein; }
        public double getCarbs() { return carbs; }
        public double getFat() { return fat; }
        public double getFiber() { return fiber; }
        public int getMealCount() { return mealCount; }
        public List<String> getRecipeTitles() { return recipeTitles; }

        // Setters
        public void setDate(String date) { this.date = date; }
        public void setCalories(double calories) { this.calories = calories; }
        public void setProtein(double protein) { this.protein = protein; }
        public void setCarbs(double carbs) { this.carbs = carbs; }
        public void setFat(double fat) { this.fat = fat; }
        public void setFiber(double fiber) { this.fiber = fiber; }
        public void setMealCount(int mealCount) { this.mealCount = mealCount; }
        public void setRecipeTitles(List<String> recipeTitles) { this.recipeTitles = recipeTitles; }

        // 获取餐次描述
        public String getMealCountDesc() {
            return mealCount + "餐";
        }

        // 获取食谱标题拼接字符串
        public String getRecipeTitlesString() {
            if (recipeTitles == null || recipeTitles.isEmpty()) {
                return "暂无记录";
            }
            return String.join("、", recipeTitles);
        }

        @Override
        public String toString() {
            return String.format("%s: %.0f大卡, %d餐", date, calories, mealCount);
        }
    }

    // ==================== 构造函数 ====================

    public WeeklyReport() {
    }

    public WeeklyReport(String startDate, String endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.generateTime = getCurrentDateTime();
    }

    // ==================== 日期相关方法 ====================

    private String getCurrentDateTime() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.CHINA).format(new java.util.Date());
    }

    // ==================== Getter 和 Setter ====================

    // startDate
    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    // endDate
    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    // generateTime
    public String getGenerateTime() {
        return generateTime;
    }

    public void setGenerateTime(String generateTime) {
        this.generateTime = generateTime;
    }

    // totalDays
    public int getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(int totalDays) {
        this.totalDays = totalDays;
    }

    // totalMeals
    public int getTotalMeals() {
        return totalMeals;
    }

    public void setTotalMeals(int totalMeals) {
        this.totalMeals = totalMeals;
    }

    // uniqueRecipes
    public int getUniqueRecipes() {
        return uniqueRecipes;
    }

    public void setUniqueRecipes(int uniqueRecipes) {
        this.uniqueRecipes = uniqueRecipes;
    }

    // weeklyTotal
    public NutritionSummary getWeeklyTotal() {
        return weeklyTotal;
    }

    public void setWeeklyTotal(NutritionSummary weeklyTotal) {
        this.weeklyTotal = weeklyTotal;
    }

    // dailyAvg
    public NutritionSummary getDailyAvg() {
        return dailyAvg;
    }

    public void setDailyAvg(NutritionSummary dailyAvg) {
        this.dailyAvg = dailyAvg;
    }

    // ratio
    public NutritionRatio getRatio() {
        return ratio;
    }

    public void setRatio(NutritionRatio ratio) {
        this.ratio = ratio;
    }

    // calorieTrend
    public List<Double> getCalorieTrend() {
        return calorieTrend;
    }

    public void setCalorieTrend(List<Double> calorieTrend) {
        this.calorieTrend = calorieTrend;
    }

    // proteinTrend
    public List<Double> getProteinTrend() {
        return proteinTrend;
    }

    public void setProteinTrend(List<Double> proteinTrend) {
        this.proteinTrend = proteinTrend;
    }

    // carbsTrend
    public List<Double> getCarbsTrend() {
        return carbsTrend;
    }

    public void setCarbsTrend(List<Double> carbsTrend) {
        this.carbsTrend = carbsTrend;
    }

    // fatTrend
    public List<Double> getFatTrend() {
        return fatTrend;
    }

    public void setFatTrend(List<Double> fatTrend) {
        this.fatTrend = fatTrend;
    }

    // strengths
    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    // weaknesses
    public List<String> getWeaknesses() {
        return weaknesses;
    }

    public void setWeaknesses(List<String> weaknesses) {
        this.weaknesses = weaknesses;
    }

    // suggestions
    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    // dailyDetails
    public Map<String, DailyNutrition> getDailyDetails() {
        return dailyDetails;
    }

    public void setDailyDetails(Map<String, DailyNutrition> dailyDetails) {
        this.dailyDetails = dailyDetails;
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取周范围描述
     */
    public String getWeekRangeDesc() {
        return String.format("%s 至 %s", startDate, endDate);
    }

    /**
     * 获取生成日期描述
     */
    public String getGenerateDateDesc() {
        return "生成于 " + generateTime;
    }

    /**
     * 判断是否有数据
     */
    public boolean hasData() {
        return totalMeals > 0;
    }

    /**
     * 获取餐次描述
     */
    public String getMealsDesc() {
        return String.format("本周共记录 %d 餐，%d 种不同菜品", totalMeals, uniqueRecipes);
    }

    /**
     * 获取日均营养描述
     */
    public String getDailyAvgDesc() {
        if (dailyAvg == null) return "暂无数据";
        return String.format("日均摄入: %.0f大卡 | 蛋白质:%.1fg | 碳水:%.1fg | 脂肪:%.1fg",
                dailyAvg.getCalories(),
                dailyAvg.getProtein(),
                dailyAvg.getCarbs(),
                dailyAvg.getFat());
    }

    /**
     * 获取营养比例描述
     */
    public String getRatioDesc() {
        if (ratio == null) return "暂无数据";
        return String.format("供能比例: 蛋白质%s | 碳水%s | 脂肪%s",
                ratio.getProteinPercent(),
                ratio.getCarbsPercent(),
                ratio.getFatPercent());
    }

    /**
     * 获取优点列表文本
     */
    public String getStrengthsText() {
        if (strengths == null || strengths.isEmpty()) {
            return "暂无特别突出的优点";
        }
        return "• " + String.join("\n• ", strengths);
    }

    /**
     * 获取缺点列表文本
     */
    public String getWeaknessesText() {
        if (weaknesses == null || weaknesses.isEmpty()) {
            return "无明显问题";
        }
        return "• " + String.join("\n• ", weaknesses);
    }

    /**
     * 获取建议列表文本
     */
    public String getSuggestionsText() {
        if (suggestions == null || suggestions.isEmpty()) {
            return "继续保持健康的饮食习惯！";
        }
        return "• " + String.join("\n• ", suggestions);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WeeklyReport{\n");
        sb.append("  week: ").append(startDate).append(" 至 ").append(endDate).append("\n");
        sb.append("  generateTime: ").append(generateTime).append("\n");
        sb.append("  totalMeals: ").append(totalMeals).append("\n");
        sb.append("  uniqueRecipes: ").append(uniqueRecipes).append("\n");
        sb.append("  dailyAvg: ").append(dailyAvg).append("\n");
        sb.append("  ratio: ").append(ratio).append("\n");
        sb.append("  strengths: ").append(strengths).append("\n");
        sb.append("  weaknesses: ").append(weaknesses).append("\n");
        sb.append("  suggestions: ").append(suggestions).append("\n");
        sb.append("}");
        return sb.toString();
    }
}