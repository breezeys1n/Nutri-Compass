package com.example.nutricompass;

import android.content.Context;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class WeeklyReportGenerator {

    private static final String TAG = "WeeklyReportGenerator";
    private Context context;
    private RecipeDatabase recipeDatabase;
    private SimpleDateFormat dateFormat;

    public WeeklyReportGenerator(Context context) {
        this.context = context;
        this.recipeDatabase = new RecipeDatabase(context);
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    }

    /**
     * 生成上周报告
     */
    public WeeklyReport generateLastWeekReport() {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);

        // 设置为上周
        calendar.add(Calendar.WEEK_OF_YEAR, -1);

        // 设置为一周的第一天（周一）
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        String startDate = dateFormat.format(calendar.getTime());

        // 设置为一周的最后一天（周日）
        calendar.add(Calendar.DAY_OF_WEEK, 6);
        String endDate = dateFormat.format(calendar.getTime());

        Log.d(TAG, "生成上周报告: " + startDate + " 至 " + endDate);

        return generateReport(startDate, endDate);
    }

    /**
     * 生成指定日期范围的报告
     */
    public WeeklyReport generateReport(String startDate, String endDate) {
        WeeklyReport report = new WeeklyReport(startDate, endDate);

        Log.d(TAG, "生成报告，日期范围: " + startDate + " 至 " + endDate);

        // 获取该时间范围内的所有食谱
        List<Recipe> recipes = recipeDatabase.getRecipesByDateRange(startDate, endDate);

        Log.d(TAG, "找到 " + recipes.size() + " 条记录");

        // 打印每条记录的信息以便调试
        for (Recipe r : recipes) {
            Log.d(TAG, "记录: " + r.getDate() + " - " + r.getTitle() + " - " + r.getCalories() + "大卡");
        }

        if (recipes.isEmpty()) {
            Log.d(TAG, "没有找到记录，返回空报告");
            return generateEmptyReport(startDate, endDate);
        }

        // 按日期分组
        Map<String, List<Recipe>> dailyRecipes = groupByDate(recipes);

        // 计算基础统计
        calculateBasicStats(report, recipes, dailyRecipes);

        // 计算营养汇总
        calculateNutritionSummary(report, recipes, dailyRecipes);

        // 计算营养比例
        calculateNutritionRatio(report);

        // 生成趋势数据
        generateTrends(report, dailyRecipes);

        // 生成分析建议
        generateAnalysis(report);

        // 生成每日明细
        report.setDailyDetails(generateDailyDetails(dailyRecipes));

        return report;
    }

    /**
     * 生成指定周的报告（新增）
     * @param year 年份
     * @param weekOfYear 第几周（1-52）
     */
    public WeeklyReport generateWeekReport(int year, int weekOfYear) {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.WEEK_OF_YEAR, weekOfYear);

        // 设置为一周的第一天（周一）
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        String startDate = dateFormat.format(calendar.getTime());

        // 设置为一周的最后一天（周日）
        calendar.add(Calendar.DAY_OF_WEEK, 6);
        String endDate = dateFormat.format(calendar.getTime());

        Log.d(TAG, "生成第" + weekOfYear + "周报告: " + startDate + " 至 " + endDate);

        return generateReport(startDate, endDate);
    }

    /**
     * 生成当前周报告（新增）
     */
    public WeeklyReport generateCurrentWeekReport() {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);

        // 设置为一周的第一天（周一）
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        String startDate = dateFormat.format(calendar.getTime());

        // 设置为一周的最后一天（周日）
        calendar.add(Calendar.DAY_OF_WEEK, 6);
        String endDate = dateFormat.format(calendar.getTime());

        Log.d(TAG, "生成当前周报告: " + startDate + " 至 " + endDate);

        return generateReport(startDate, endDate);
    }

    /**
     * 按日期分组
     */
    private Map<String, List<Recipe>> groupByDate(List<Recipe> recipes) {
        Map<String, List<Recipe>> grouped = new HashMap<>();
        for (Recipe recipe : recipes) {
            String date = recipe.getDate();
            if (!grouped.containsKey(date)) {
                grouped.put(date, new ArrayList<>());
            }
            grouped.get(date).add(recipe);
        }
        return grouped;
    }

    /**
     * 计算基础统计
     */
    private void calculateBasicStats(WeeklyReport report, List<Recipe> recipes,
                                     Map<String, List<Recipe>> dailyRecipes) {
        report.setTotalDays(dailyRecipes.size());
        report.setTotalMeals(recipes.size());

        Set<String> uniqueTitles = new HashSet<>();
        for (Recipe recipe : recipes) {
            uniqueTitles.add(recipe.getTitle());
        }
        report.setUniqueRecipes(uniqueTitles.size());
    }

    /**
     * 计算营养汇总
     */
    private void calculateNutritionSummary(WeeklyReport report, List<Recipe> recipes,
                                           Map<String, List<Recipe>> dailyRecipes) {
        // 计算周总量
        WeeklyReport.NutritionSummary total = new WeeklyReport.NutritionSummary();
        double totalCalories = 0, totalProtein = 0, totalCarbs = 0, totalFat = 0, totalFiber = 0;

        for (Recipe recipe : recipes) {
            totalCalories += recipe.getCalories();
            totalProtein += recipe.getProtein();
            totalCarbs += recipe.getCarbs();
            totalFat += recipe.getFat();
            totalFiber += recipe.getFiber();
        }

        total.setCalories(totalCalories);
        total.setProtein(totalProtein);
        total.setCarbs(totalCarbs);
        total.setFat(totalFat);
        total.setFiber(totalFiber);

        report.setWeeklyTotal(total);

        // 计算日均
        WeeklyReport.NutritionSummary avg = new WeeklyReport.NutritionSummary();
        int dayCount = dailyRecipes.size();
        if (dayCount > 0) {
            avg.setCalories(totalCalories / dayCount);
            avg.setProtein(totalProtein / dayCount);
            avg.setCarbs(totalCarbs / dayCount);
            avg.setFat(totalFat / dayCount);
            avg.setFiber(totalFiber / dayCount);
        }

        report.setDailyAvg(avg);
    }

    /**
     * 计算营养比例
     */
    private void calculateNutritionRatio(WeeklyReport report) {
        WeeklyReport.NutritionSummary total = report.getWeeklyTotal();
        if (total == null) return;

        WeeklyReport.NutritionRatio ratio = new WeeklyReport.NutritionRatio();

        double totalCalories = total.getProtein() * 4 + total.getCarbs() * 4 + total.getFat() * 9;
        if (totalCalories > 0) {
            ratio.setProtein((total.getProtein() * 4) / totalCalories);
            ratio.setCarbs((total.getCarbs() * 4) / totalCalories);
            ratio.setFat((total.getFat() * 9) / totalCalories);
        }

        report.setRatio(ratio);
    }

    /**
     * 生成趋势数据
     */
    private void generateTrends(WeeklyReport report, Map<String, List<Recipe>> dailyRecipes) {
        List<Double> calorieTrend = new ArrayList<>();
        List<Double> proteinTrend = new ArrayList<>();
        List<Double> carbsTrend = new ArrayList<>();
        List<Double> fatTrend = new ArrayList<>();

        // 按日期排序
        List<String> sortedDates = new ArrayList<>(dailyRecipes.keySet());
        Collections.sort(sortedDates);

        for (String date : sortedDates) {
            List<Recipe> dayRecipes = dailyRecipes.get(date);
            double dayCalories = 0, dayProtein = 0, dayCarbs = 0, dayFat = 0;

            for (Recipe recipe : dayRecipes) {
                dayCalories += recipe.getCalories();
                dayProtein += recipe.getProtein();
                dayCarbs += recipe.getCarbs();
                dayFat += recipe.getFat();
            }

            calorieTrend.add(dayCalories);
            proteinTrend.add(dayProtein);
            carbsTrend.add(dayCarbs);
            fatTrend.add(dayFat);
        }

        report.setCalorieTrend(calorieTrend);
        report.setProteinTrend(proteinTrend);
        report.setCarbsTrend(carbsTrend);
        report.setFatTrend(fatTrend);
    }

    /**
     * 生成分析建议
     */
    private void generateAnalysis(WeeklyReport report) {
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        WeeklyReport.NutritionSummary avg = report.getDailyAvg();
        WeeklyReport.NutritionRatio ratio = report.getRatio();

        if (avg == null) {
            report.setStrengths(strengths);
            report.setWeaknesses(weaknesses);
            report.setSuggestions(suggestions);
            return;
        }

        // 蛋白质分析
        if (avg.getProtein() >= 60) {
            strengths.add("蛋白质摄入充足");
        } else if (avg.getProtein() < 40 && avg.getProtein() > 0) {
            weaknesses.add("蛋白质摄入不足");
            suggestions.add("建议增加鱼、肉、蛋、豆制品等优质蛋白");
        }

        // 碳水分析
        if (ratio != null) {
            if (ratio.getCarbs() > 0.65) {
                weaknesses.add("碳水化合物比例偏高");
                suggestions.add("适当减少主食，增加蔬菜比例");
            } else if (ratio.getCarbs() < 0.45 && ratio.getCarbs() > 0) {
                weaknesses.add("碳水化合物比例偏低");
                suggestions.add("保证每日主食摄入");
            }

            // 脂肪分析
            if (ratio.getFat() > 0.35) {
                weaknesses.add("脂肪摄入偏高");
                suggestions.add("控制烹饪用油，选择低脂食材");
            }
        }

        // 膳食纤维
        if (avg.getFiber() < 25 && avg.getFiber() > 0) {
            weaknesses.add("膳食纤维不足");
            suggestions.add("多吃蔬菜、水果、全谷物");
        }

        // 规律性分析
        if (report.getTotalMeals() >= report.getTotalDays() * 2.5) {
            strengths.add("饮食较为规律");
        } else if (report.getTotalMeals() < report.getTotalDays() * 2) {
            weaknesses.add("饮食不够规律");
            suggestions.add("尽量保持每日三餐");
        }

        // 多样性分析
        if (report.getUniqueRecipes() >= report.getTotalMeals() * 0.5) {
            strengths.add("食材多样，营养均衡");
        }

        // 如果没有明显的优点，加一个通用优点
        if (strengths.isEmpty()) {
            strengths.add("有记录饮食的习惯，值得保持");
        }

        // 如果没有建议，加一个鼓励性建议
        if (suggestions.isEmpty() && !weaknesses.isEmpty()) {
            suggestions.add("可以尝试调整饮食结构");
        } else if (suggestions.isEmpty()) {
            suggestions.add("本周饮食很健康，继续保持！");
        }

        report.setStrengths(strengths);
        report.setWeaknesses(weaknesses);
        report.setSuggestions(suggestions);
    }

    /**
     * 生成每日明细
     */
    private Map<String, WeeklyReport.DailyNutrition> generateDailyDetails(
            Map<String, List<Recipe>> dailyRecipes) {
        Map<String, WeeklyReport.DailyNutrition> details = new HashMap<>();

        for (Map.Entry<String, List<Recipe>> entry : dailyRecipes.entrySet()) {
            String date = entry.getKey();
            List<Recipe> recipes = entry.getValue();

            WeeklyReport.DailyNutrition daily = new WeeklyReport.DailyNutrition();
            daily.setDate(date);
            daily.setMealCount(recipes.size());

            List<String> recipeTitles = new ArrayList<>();
            double calories = 0, protein = 0, carbs = 0, fat = 0, fiber = 0;

            for (Recipe recipe : recipes) {
                recipeTitles.add(recipe.getTitle());
                calories += recipe.getCalories();
                protein += recipe.getProtein();
                carbs += recipe.getCarbs();
                fat += recipe.getFat();
                fiber += recipe.getFiber();
            }

            daily.setRecipeTitles(recipeTitles);
            daily.setCalories(calories);
            daily.setProtein(protein);
            daily.setCarbs(carbs);
            daily.setFat(fat);
            daily.setFiber(fiber);

            details.put(date, daily);
        }

        return details;
    }

    /**
     * 生成空报告
     */
    private WeeklyReport generateEmptyReport(String startDate, String endDate) {
        WeeklyReport report = new WeeklyReport(startDate, endDate);

        WeeklyReport.NutritionSummary empty = new WeeklyReport.NutritionSummary();
        empty.setCalories(0);
        empty.setProtein(0);
        empty.setCarbs(0);
        empty.setFat(0);
        empty.setFiber(0);

        report.setWeeklyTotal(empty);
        report.setDailyAvg(empty);

        WeeklyReport.NutritionRatio ratio = new WeeklyReport.NutritionRatio();
        ratio.setProtein(0);
        ratio.setCarbs(0);
        ratio.setFat(0);
        report.setRatio(ratio);

        report.setTotalDays(0);
        report.setTotalMeals(0);
        report.setUniqueRecipes(0);

        report.setStrengths(new ArrayList<>());
        report.setWeaknesses(new ArrayList<>());
        report.setSuggestions(new ArrayList<>());

        return report;
    }

    /**
     * 获取本周日期范围
     */
    public String[] getCurrentWeekRange() {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        String[] range = new String[2];

        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        range[0] = dateFormat.format(calendar.getTime());

        calendar.add(Calendar.DAY_OF_WEEK, 6);
        range[1] = dateFormat.format(calendar.getTime());

        return range;
    }

    /**
     * 获取上周日期范围
     */
    public String[] getLastWeekRange() {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        String[] range = new String[2];

        calendar.add(Calendar.WEEK_OF_YEAR, -1);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        range[0] = dateFormat.format(calendar.getTime());

        calendar.add(Calendar.DAY_OF_WEEK, 6);
        range[1] = dateFormat.format(calendar.getTime());

        return range;
    }

    /**
     * 获取指定日期的周范围（新增）
     */
    public String[] getWeekRangeByDate(String dateStr) {
        try {
            Date date = dateFormat.parse(dateStr);
            Calendar calendar = Calendar.getInstance(Locale.CHINA);
            calendar.setTime(date);

            // 设置到本周的周一
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            String startDate = dateFormat.format(calendar.getTime());

            // 设置到本周的周日
            calendar.add(Calendar.DAY_OF_WEEK, 6);
            String endDate = dateFormat.format(calendar.getTime());

            return new String[]{startDate, endDate};
        } catch (ParseException e) {
            e.printStackTrace();
            return getCurrentWeekRange();
        }
    }
}