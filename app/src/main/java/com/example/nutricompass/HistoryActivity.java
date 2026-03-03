package com.example.nutricompass;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
public class HistoryActivity extends AppCompatActivity {

    private ListView listViewHistory;
    private TextView tvEmptyHistory;
    private MaterialButton btnClearHistory;

    private HistoryAdapter historyAdapter;
    private List<Recipe> recipeHistory;
    private RecipeDatabase recipeDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        BackButtonUtil.setupBackButton(this);

        // 初始化数据库（简化的内存存储，实际项目中可以用 Room 数据库）
        recipeDatabase = new RecipeDatabase(this);

        // 初始化控件
        listViewHistory = findViewById(R.id.listView_history);
        tvEmptyHistory = findViewById(R.id.tv_empty_history);
        btnClearHistory = findViewById(R.id.btn_clear_history);

        // 加载历史记录
        loadHistory();

        // 设置适配器
        historyAdapter = new HistoryAdapter(this, recipeHistory);
        listViewHistory.setAdapter(historyAdapter);

        // 列表项点击事件
        listViewHistory.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Recipe recipe = recipeHistory.get(position);
                // 跳转到食谱详情页面
                Intent intent = new Intent(HistoryActivity.this, RecipeResultActivity.class);
                intent.putExtra("recipe", recipe);
                startActivity(intent);
            }
        });

        // 长按删除单个记录
        listViewHistory.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Recipe recipe = recipeHistory.get(position);
                recipeDatabase.deleteRecipe(recipe.getId());
                recipeHistory.remove(position);
                historyAdapter.notifyDataSetChanged();
                updateEmptyState();
                Toast.makeText(HistoryActivity.this, "已删除记录", Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        // 清空历史按钮
        btnClearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recipeDatabase.clearAllRecipes();
                recipeHistory.clear();
                historyAdapter.notifyDataSetChanged();
                updateEmptyState();
                Toast.makeText(HistoryActivity.this, "已清空所有历史记录", Toast.LENGTH_SHORT).show();
            }
        });
        MaterialButton btnWeeklyReport = findViewById(R.id.btn_weekly_report);
        btnWeeklyReport.setOnClickListener(v -> {
            Intent intent = new Intent(HistoryActivity.this, WeeklyReportActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 重新加载数据
        loadHistory();
        if (historyAdapter != null) {
            historyAdapter.notifyDataSetChanged();
        }
        updateEmptyState();
    }

    /**
     * 加载历史记录
     */
    private void loadHistory() {
        recipeHistory = recipeDatabase.getAllRecipes();

        // 如果没有数据，添加示例数据到数据库
        if (recipeHistory.isEmpty()) {
            Log.d(TAG, "数据库为空，添加示例数据");
            addSampleDataToDatabase();
            // 重新加载
            recipeHistory = recipeDatabase.getAllRecipes();
        }
    }

    /**
     * 添加示例数据到数据库
     */
    private void addSampleDataToDatabase() {
        List<Recipe> sampleList = getSampleHistory();

        for (Recipe recipe : sampleList) {
            long id = recipeDatabase.addRecipe(recipe);
            Log.d(TAG, "添加示例数据: " + recipe.getTitle() + ", id=" + id);
        }

        Toast.makeText(this, "已添加示例数据", Toast.LENGTH_SHORT).show();
    }

    /**
     * 更新空状态显示
     */
    private void updateEmptyState() {
        if (recipeHistory.isEmpty()) {
            tvEmptyHistory.setVisibility(View.VISIBLE);
            listViewHistory.setVisibility(View.GONE);
            btnClearHistory.setVisibility(View.GONE);
        } else {
            tvEmptyHistory.setVisibility(View.GONE);
            listViewHistory.setVisibility(View.VISIBLE);
            btnClearHistory.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 获取一周的示例历史数据（用于测试）
     */
    private List<Recipe> getSampleHistory() {
        List<Recipe> sampleList = new ArrayList<>();

        // 周一 (2024-01-15)
        Recipe recipe1 = new Recipe();
        recipe1.setId(1);
        recipe1.setTitle("营养均衡早餐");
        recipe1.setDescription("富含蛋白质和纤维的健康早餐");
        recipe1.setDate("2024-01-15");
        recipe1.setMealType("早餐");
        recipe1.setCalories(350);
        recipe1.setProtein(15.5);
        recipe1.setCarbs(45.0);
        recipe1.setFat(12.0);
        recipe1.setFiber(8.5);
        recipe1.setIngredients(Arrays.asList("全麦面包", "鸡蛋", "牛油果", "西红柿"));
        sampleList.add(recipe1);

        Recipe recipe1_lunch = new Recipe();
        recipe1_lunch.setId(2);
        recipe1_lunch.setTitle("鸡胸肉沙拉");
        recipe1_lunch.setDescription("低卡高蛋白午餐");
        recipe1_lunch.setDate("2024-01-15");
        recipe1_lunch.setMealType("午餐");
        recipe1_lunch.setCalories(450);
        recipe1_lunch.setProtein(35.0);
        recipe1_lunch.setCarbs(30.0);
        recipe1_lunch.setFat(18.0);
        recipe1_lunch.setFiber(10.0);
        recipe1_lunch.setIngredients(Arrays.asList("鸡胸肉", "生菜", "圣女果", "玉米粒"));
        sampleList.add(recipe1_lunch);

        Recipe recipe1_dinner = new Recipe();
        recipe1_dinner.setId(3);
        recipe1_dinner.setTitle("三文鱼配蔬菜");
        recipe1_dinner.setDescription("富含Omega-3的健康晚餐");
        recipe1_dinner.setDate("2024-01-15");
        recipe1_dinner.setMealType("晚餐");
        recipe1_dinner.setCalories(550);
        recipe1_dinner.setProtein(40.0);
        recipe1_dinner.setCarbs(35.0);
        recipe1_dinner.setFat(25.0);
        recipe1_dinner.setFiber(12.0);
        recipe1_dinner.setIngredients(Arrays.asList("三文鱼", "西兰花", "胡萝卜", "藜麦"));
        sampleList.add(recipe1_dinner);

        // 周二 (2024-01-16)
        Recipe recipe2 = new Recipe();
        recipe2.setId(4);
        recipe2.setTitle("燕麦水果碗");
        recipe2.setDescription("高纤维抗氧化早餐");
        recipe2.setDate("2024-01-16");
        recipe2.setMealType("早餐");
        recipe2.setCalories(320);
        recipe2.setProtein(12.0);
        recipe2.setCarbs(52.0);
        recipe2.setFat(8.0);
        recipe2.setFiber(9.5);
        recipe2.setIngredients(Arrays.asList("燕麦", "蓝莓", "香蕉", "坚果"));
        sampleList.add(recipe2);

        Recipe recipe2_lunch = new Recipe();
        recipe2_lunch.setId(5);
        recipe2_lunch.setTitle("牛肉糙米饭");
        recipe2_lunch.setDescription("能量满满的午餐");
        recipe2_lunch.setDate("2024-01-16");
        recipe2_lunch.setMealType("午餐");
        recipe2_lunch.setCalories(580);
        recipe2_lunch.setProtein(38.0);
        recipe2_lunch.setCarbs(65.0);
        recipe2_lunch.setFat(20.0);
        recipe2_lunch.setFiber(11.0);
        recipe2_lunch.setIngredients(Arrays.asList("瘦牛肉", "糙米", "彩椒", "洋葱"));
        sampleList.add(recipe2_lunch);

        Recipe recipe2_dinner = new Recipe();
        recipe2_dinner.setId(6);
        recipe2_dinner.setTitle("豆腐蔬菜汤");
        recipe2_dinner.setDescription("清淡易消化的晚餐");
        recipe2_dinner.setDate("2024-01-16");
        recipe2_dinner.setMealType("晚餐");
        recipe2_dinner.setCalories(280);
        recipe2_dinner.setProtein(18.0);
        recipe2_dinner.setCarbs(22.0);
        recipe2_dinner.setFat(12.0);
        recipe2_dinner.setFiber(8.0);
        recipe2_dinner.setIngredients(Arrays.asList("豆腐", "白菜", "香菇", "海带"));
        sampleList.add(recipe2_dinner);

        // 周三 (2024-01-17)
        Recipe recipe3 = new Recipe();
        recipe3.setId(7);
        recipe3.setTitle("全麦三明治");
        recipe3.setDescription("快速营养早餐");
        recipe3.setDate("2024-01-17");
        recipe3.setMealType("早餐");
        recipe3.setCalories(380);
        recipe3.setProtein(18.0);
        recipe3.setCarbs(48.0);
        recipe3.setFat(14.0);
        recipe3.setFiber(7.5);
        recipe3.setIngredients(Arrays.asList("全麦面包", "火腿", "生菜", "芝士"));
        sampleList.add(recipe3);

        Recipe recipe3_lunch = new Recipe();
        recipe3_lunch.setId(8);
        recipe3_lunch.setTitle("烤鱼配土豆");
        recipe3_lunch.setDescription("鲜美可口的午餐");
        recipe3_lunch.setDate("2024-01-17");
        recipe3_lunch.setMealType("午餐");
        recipe3_lunch.setCalories(520);
        recipe3_lunch.setProtein(42.0);
        recipe3_lunch.setCarbs(45.0);
        recipe3_lunch.setFat(22.0);
        recipe3_lunch.setFiber(9.0);
        recipe3_lunch.setIngredients(Arrays.asList("鲈鱼", "土豆", "芦笋", "柠檬"));
        sampleList.add(recipe3_lunch);

        // 周四 (2024-01-18)
        Recipe recipe4 = new Recipe();
        recipe4.setId(9);
        recipe4.setTitle("希腊酸奶碗");
        recipe4.setDescription("高蛋白早餐");
        recipe4.setDate("2024-01-18");
        recipe4.setMealType("早餐");
        recipe4.setCalories(300);
        recipe4.setProtein(22.0);
        recipe4.setCarbs(28.0);
        recipe4.setFat(10.0);
        recipe4.setFiber(6.0);
        recipe4.setIngredients(Arrays.asList("希腊酸奶", "草莓", "格兰诺拉", "蜂蜜"));
        sampleList.add(recipe4);

        Recipe recipe4_lunch = new Recipe();
        recipe4_lunch.setId(10);
        recipe4_lunch.setTitle("虾仁意面");
        recipe4_lunch.setDescription("地中海风味午餐");
        recipe4_lunch.setDate("2024-01-18");
        recipe4_lunch.setMealType("午餐");
        recipe4_lunch.setCalories(490);
        recipe4_lunch.setProtein(32.0);
        recipe4_lunch.setCarbs(58.0);
        recipe4_lunch.setFat(16.0);
        recipe4_lunch.setFiber(8.5);
        recipe4_lunch.setIngredients(Arrays.asList("虾仁", "意面", "西兰花", "蒜蓉"));
        sampleList.add(recipe4_lunch);

        // 周五 (2024-01-19)
        Recipe recipe5 = new Recipe();
        recipe5.setId(11);
        recipe5.setTitle("鸡蛋煎饼");
        recipe5.setDescription("中式营养早餐");
        recipe5.setDate("2024-01-19");
        recipe5.setMealType("早餐");
        recipe5.setCalories(340);
        recipe5.setProtein(16.0);
        recipe5.setCarbs(42.0);
        recipe5.setFat(13.0);
        recipe5.setFiber(5.0);
        recipe5.setIngredients(Arrays.asList("鸡蛋", "面粉", "葱花", "甜面酱"));
        sampleList.add(recipe5);

        Recipe recipe5_lunch = new Recipe();
        recipe5_lunch.setId(12);
        recipe5_lunch.setTitle("韩式拌饭");
        recipe5_lunch.setDescription("营养均衡的午餐");
        recipe5_lunch.setDate("2024-01-19");
        recipe5_lunch.setMealType("午餐");
        recipe5_lunch.setCalories(560);
        recipe5_lunch.setProtein(28.0);
        recipe5_lunch.setCarbs(72.0);
        recipe5_lunch.setFat(20.0);
        recipe5_lunch.setFiber(12.0);
        recipe5_lunch.setIngredients(Arrays.asList("米饭", "菠菜", "胡萝卜", "牛肉", "鸡蛋"));
        sampleList.add(recipe5_lunch);

        // 周六 (2024-01-20)
        Recipe recipe6 = new Recipe();
        recipe6.setId(13);
        recipe6.setTitle("牛油果吐司");
        recipe6.setDescription("网红健康早餐");
        recipe6.setDate("2024-01-20");
        recipe6.setMealType("早餐");
        recipe6.setCalories(360);
        recipe6.setProtein(12.0);
        recipe6.setCarbs(38.0);
        recipe6.setFat(22.0);
        recipe6.setFiber(9.0);
        recipe6.setIngredients(Arrays.asList("全麦吐司", "牛油果", "水波蛋", "黑胡椒"));
        sampleList.add(recipe6);

        Recipe recipe6_dinner = new Recipe();
        recipe6_dinner.setId(14);
        recipe6_dinner.setTitle("火锅聚餐");
        recipe6_dinner.setDescription("周末放松大餐");
        recipe6_dinner.setDate("2024-01-20");
        recipe6_dinner.setMealType("晚餐");
        recipe6_dinner.setCalories(850);
        recipe6_dinner.setProtein(45.0);
        recipe6_dinner.setCarbs(60.0);
        recipe6_dinner.setFat(48.0);
        recipe6_dinner.setFiber(15.0);
        recipe6_dinner.setIngredients(Arrays.asList("肥牛", "羊肉", "蔬菜拼盘", "豆腐", "面条"));
        sampleList.add(recipe6_dinner);

        // 周日 (2024-01-21)
        Recipe recipe7 = new Recipe();
        recipe7.setId(15);
        recipe7.setTitle("周日早午餐");
        recipe7.setDescription("悠闲周末早午餐");
        recipe7.setDate("2024-01-21");
        recipe7.setMealType("早午餐");
        recipe7.setCalories(580);
        recipe7.setProtein(28.0);
        recipe7.setCarbs(58.0);
        recipe7.setFat(28.0);
        recipe7.setFiber(10.0);
        recipe7.setIngredients(Arrays.asList("培根", "炒蛋", "烤蘑菇", "吐司", "橙汁"));
        sampleList.add(recipe7);

        return sampleList;
    }
}