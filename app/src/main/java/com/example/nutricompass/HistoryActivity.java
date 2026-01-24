package com.example.nutricompass;

import android.content.Intent;
import android.os.Bundle;
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
        // 如果没有数据，显示示例数据（仅用于演示）
        if (recipeHistory.isEmpty()) {
            recipeHistory = getSampleHistory();
        }
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
     * 获取示例历史数据（仅用于演示）
     */
    private List<Recipe> getSampleHistory() {
        List<Recipe> sampleList = new ArrayList<>();

        // 示例数据1
        Recipe recipe1 = new Recipe();
        recipe1.setId(1);
        recipe1.setTitle("营养均衡早餐");
        recipe1.setDescription("富含蛋白质和纤维的健康早餐");
        recipe1.setDate("2024-01-15");
        recipe1.setCalories(350);
        recipe1.setIngredients(Arrays.asList(new String[]{"全麦面包", "鸡蛋", "牛油果", "西红柿"}));
        sampleList.add(recipe1);

        // 示例数据2
        Recipe recipe2 = new Recipe();
        recipe2.setId(2);
        recipe2.setTitle("低卡减脂午餐");
        recipe2.setDescription("低热量高蛋白的减脂午餐");
        recipe2.setDate("2024-01-14");
        recipe2.setCalories(450);
        recipe2.setIngredients(Arrays.asList(new String[]{"鸡胸肉", "西兰花", "糙米饭", "胡萝卜"}));
        sampleList.add(recipe2);

        // 示例数据3
        Recipe recipe3 = new Recipe();
        recipe3.setId(3);
        recipe3.setTitle("素食晚餐");
        recipe3.setDescription("纯素食的健康晚餐选择");
        recipe3.setDate("2024-01-13");
        recipe3.setCalories(400);
        recipe3.setIngredients(Arrays.asList(new String[]{"豆腐", "菠菜", "蘑菇", "藜麦"}));
        sampleList.add(recipe3);

        return sampleList;
    }
}