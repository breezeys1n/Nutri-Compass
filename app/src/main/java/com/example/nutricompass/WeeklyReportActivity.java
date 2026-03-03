package com.example.nutricompass;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WeeklyReportActivity extends AppCompatActivity {

    // 基础统计
    private TextView tvWeekRange;
    private TextView tvGenerateTime;
    private TextView tvTotalMeals;
    private TextView tvUniqueRecipes;

    // 营养数据
    private TextView tvAvgCalories;
    private TextView tvAvgProtein;
    private TextView tvAvgCarbs;
    private TextView tvAvgFat;
    private TextView tvNutritionRatio;

    // 分析和建议（改为TextView，因为布局中用的是TextView）
    private TextView tvStrengths;
    private TextView tvSuggestions;

    // 可选的缺点TextView（如果布局中有）
    private TextView tvWeaknesses;

    // 趋势卡片（可选）
    private MaterialCardView cardTrend;

    // 按钮
    private MaterialButton btnShare;
    private MaterialButton btnHistory;

    // 进度条
    private ProgressBar progressBar;

    private WeeklyReportDatabase reportDatabase;
    private RecipeDatabase recipeDatabase;
    private WeeklyReport currentReport;
    private WeeklyReportGenerator reportGenerator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weekly_report);

        // 设置返回按钮
        BackButtonUtil.setupBackButton(this);

        // 初始化数据库和生成器
        recipeDatabase = new RecipeDatabase(this);
        reportDatabase = new WeeklyReportDatabase(this);
        reportGenerator = new WeeklyReportGenerator(this);

        initViews();
        loadReport();
    }

    private void initViews() {
        // 基础统计
        tvWeekRange = findViewById(R.id.tv_week_range);
        tvGenerateTime = findViewById(R.id.tv_generate_time);
        tvTotalMeals = findViewById(R.id.tv_total_meals);
        tvUniqueRecipes = findViewById(R.id.tv_unique_recipes);

        // 营养数据
        tvAvgCalories = findViewById(R.id.tv_avg_calories);
        tvAvgProtein = findViewById(R.id.tv_avg_protein);
        tvAvgCarbs = findViewById(R.id.tv_avg_carbs);
        tvAvgFat = findViewById(R.id.tv_avg_fat);
        tvNutritionRatio = findViewById(R.id.tv_nutrition_ratio);

        // 分析和建议（使用TextView）
        tvStrengths = findViewById(R.id.tv_strengths);
        tvSuggestions = findViewById(R.id.tv_suggestions);

        // 可选的缺点TextView（如果布局中有就取消注释）
        // tvWeaknesses = findViewById(R.id.tv_weaknesses);

        // 趋势卡片（可选）
        cardTrend = findViewById(R.id.card_trend);

        // 按钮
        btnShare = findViewById(R.id.btn_share);
        btnHistory = findViewById(R.id.btn_history);
        progressBar = findViewById(R.id.progress_bar);

        // 设置点击事件
        btnShare.setOnClickListener(v -> shareReport());

        btnHistory.setOnClickListener(v -> {
            Toast.makeText(this, "历史报告功能开发中", Toast.LENGTH_SHORT).show();
            // TODO: 跳转到历史报告列表页面
        });
    }

    private void loadReport() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (recipeDatabase == null) {
                    Toast.makeText(this, "数据库初始化失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 先获取所有数据看看
                List<Recipe> allRecipes = recipeDatabase.getAllRecipes();
                Log.d(TAG, "数据库中共有 " + allRecipes.size() + " 条记录");

                if (allRecipes.isEmpty()) {
                    tvStrengths.setText("暂无饮食记录");
                    Toast.makeText(this, "暂无数据", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 打印所有日期以便调试
                for (Recipe r : allRecipes) {
                    Log.d(TAG, "记录日期: " + r.getDate() +
                            ", 热量: " + r.getCalories() +
                            ", 蛋白质: " + r.getProtein() +
                            ", 碳水: " + r.getCarbs() +
                            ", 脂肪: " + r.getFat());
                }

                // 使用样例数据的日期范围
                String startDate = "2024-01-15";
                String endDate = "2024-01-21";

                Log.d(TAG, "使用日期范围: " + startDate + " 至 " + endDate);

                // 生成报告
                currentReport = reportGenerator.generateReport(startDate, endDate);

                if (currentReport != null) {
                    // 打印报告数据，看看实际计算出了什么
                    WeeklyReport.NutritionSummary avg = currentReport.getDailyAvg();
                    if (avg != null) {
                        Log.d(TAG, "报告日均数据 - 热量: " + avg.getCalories() +
                                ", 蛋白质: " + avg.getProtein() +
                                ", 碳水: " + avg.getCarbs() +
                                ", 脂肪: " + avg.getFat());
                    }

                    if (currentReport.hasData()) {
                        reportDatabase.saveWeeklyReport(currentReport);
                        displayReport();
                        Toast.makeText(this, "报告生成成功，共 " + currentReport.getTotalMeals() + " 条记录", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.d(TAG, "报告无数据");
                        tvStrengths.setText("该时间段无数据");
                        Toast.makeText(this, "该时间段无数据", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "报告生成失败", Toast.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                Log.e(TAG, "Error", e);
                Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            } finally {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
            }
        }, 500);
    }

    private void displayReport() {
        if (currentReport == null) return;

        // 显示周范围
        tvWeekRange.setText(currentReport.getWeekRangeDesc());

        // 显示生成时间
        tvGenerateTime.setText(currentReport.getGenerateDateDesc());

        // 显示基础统计
        tvTotalMeals.setText(String.valueOf(currentReport.getTotalMeals()));
        tvUniqueRecipes.setText(String.valueOf(currentReport.getUniqueRecipes()));

        // 显示营养数据
        WeeklyReport.NutritionSummary avg = currentReport.getDailyAvg();
        if (avg != null) {
            tvAvgCalories.setText(String.format(Locale.CHINA, "%.0f", avg.getCalories()));
            tvAvgProtein.setText(String.format(Locale.CHINA, "%.0f", avg.getProtein()));
            tvAvgCarbs.setText(String.format(Locale.CHINA, "%.0f", avg.getCarbs()));
            tvAvgFat.setText(String.format(Locale.CHINA, "%.0f", avg.getFat()));
        }

        // 显示营养比例
        WeeklyReport.NutritionRatio ratio = currentReport.getRatio();
        if (ratio != null && tvNutritionRatio != null) {
            tvNutritionRatio.setVisibility(View.VISIBLE);
            tvNutritionRatio.setText(currentReport.getRatioDesc());
        } else if (tvNutritionRatio != null) {
            tvNutritionRatio.setVisibility(View.GONE);
        }

        // 显示优点
        if (tvStrengths != null) {
            List<String> strengths = currentReport.getStrengths();
            if (strengths != null && !strengths.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String s : strengths) {
                    sb.append("• ").append(s).append("\n");
                }
                tvStrengths.setText(sb.toString().trim());
            } else {
                tvStrengths.setText("暂无特别突出的优点");
            }
        }

        // 显示建议
        if (tvSuggestions != null) {
            List<String> suggestions = currentReport.getSuggestions();
            if (suggestions != null && !suggestions.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String s : suggestions) {
                    sb.append("• ").append(s).append("\n");
                }
                tvSuggestions.setText(sb.toString().trim());
            } else {
                tvSuggestions.setText("继续保持健康的饮食习惯！");
            }
        }

        // 显示缺点（如果布局中有）
        if (tvWeaknesses != null) {
            List<String> weaknesses = currentReport.getWeaknesses();
            if (weaknesses != null && !weaknesses.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String w : weaknesses) {
                    sb.append("• ").append(w).append("\n");
                }
                tvWeaknesses.setText(sb.toString().trim());
            } else {
                tvWeaknesses.setText("无明显问题");
            }
        }

        // 控制趋势卡片显示（如果有数据且布局中有）
        if (cardTrend != null) {
            boolean hasTrendData = currentReport.getCalorieTrend() != null &&
                    !currentReport.getCalorieTrend().isEmpty();
            cardTrend.setVisibility(hasTrendData ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 分享方法
     */
    private void shareReport() {
        if (currentReport == null) {
            Toast.makeText(this, "暂无报告数据", Toast.LENGTH_SHORT).show();
            return;
        }

        String shareText = generateShareText();

        // 创建分享Intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "我的膳愈健康周报"); // 添加主题
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

        // 创建选择器
        Intent chooser = Intent.createChooser(shareIntent, "分享健康周报");

        // 检查是否有应用可以处理
        if (shareIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(chooser);
        } else {
            Toast.makeText(this, "没有可用的分享应用", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 生成分享文本
     */
    private String generateShareText() {
        StringBuilder sb = new StringBuilder();

        // 头部
        sb.append("【膳愈】我的本周健康报告\n");
        sb.append("═══════════════════\n\n");

        // 周期
        sb.append("📅 周期：").append(currentReport.getWeekRangeDesc()).append("\n\n");

        // 基础统计
        sb.append("📊 本周概况\n");
        sb.append("   • 总餐次：").append(currentReport.getTotalMeals()).append(" 餐\n");
        sb.append("   • 不同菜品：").append(currentReport.getUniqueRecipes()).append(" 种\n\n");

        // 营养数据
        WeeklyReport.NutritionSummary avg = currentReport.getDailyAvg();
        if (avg != null) {
            sb.append("🍽️ 日均摄入\n");
            sb.append("   • 热量：").append(String.format(Locale.CHINA, "%.0f", avg.getCalories())).append(" 大卡\n");
            sb.append("   • 蛋白质：").append(String.format(Locale.CHINA, "%.1f", avg.getProtein())).append(" g\n");
            sb.append("   • 碳水：").append(String.format(Locale.CHINA, "%.1f", avg.getCarbs())).append(" g\n");
            sb.append("   • 脂肪：").append(String.format(Locale.CHINA, "%.1f", avg.getFat())).append(" g\n\n");
        }

        // 营养比例
        WeeklyReport.NutritionRatio ratio = currentReport.getRatio();
        if (ratio != null) {
            sb.append("⚖️ 供能比例\n");
            sb.append("   • ").append(ratio.getProteinPercent()).append(" 蛋白质\n");
            sb.append("   • ").append(ratio.getCarbsPercent()).append(" 碳水\n");
            sb.append("   • ").append(ratio.getFatPercent()).append(" 脂肪\n\n");
        }

        // 优点
        List<String> strengths = currentReport.getStrengths();
        if (strengths != null && !strengths.isEmpty()) {
            sb.append("✅ 本周优点\n");
            for (String s : strengths) {
                sb.append("   • ").append(s).append("\n");
            }
            sb.append("\n");
        }

        // 建议
        List<String> suggestions = currentReport.getSuggestions();
        if (suggestions != null && !suggestions.isEmpty()) {
            sb.append("💡 改进建议\n");
            for (String s : suggestions) {
                sb.append("   • ").append(s).append("\n");
            }
            sb.append("\n");
        }

        // 底部
        sb.append("═══════════════════\n");
        sb.append("来自 膳愈AI 健康助手");

        return sb.toString();
    }

    /**
     * 显示/隐藏加载进度
     */
    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 显示错误信息
     */
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 手动刷新报告
     */
    private void refreshReport() {
        showLoading(true);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // 重新生成上周报告
            currentReport = reportGenerator.generateLastWeekReport();
            if (currentReport != null && currentReport.hasData()) {
                reportDatabase.saveWeeklyReport(currentReport);
                displayReport();
                Toast.makeText(this, "报告已更新", Toast.LENGTH_SHORT).show();
            }
            showLoading(false);
        }, 500);
    }

    /**
     * 获取当前时间字符串
     */
    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        return sdf.format(new Date());
    }
}