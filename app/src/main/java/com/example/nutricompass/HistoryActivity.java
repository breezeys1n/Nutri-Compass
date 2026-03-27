package com.example.nutricompass;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.nutricompass.WeeklyReportActivity;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private ListView listViewHistory;
    private TextView tvEmptyHistory;
    private Button btnClearHistory;
    private Button btnWeeklyReport;
    private LinearLayout emptyState;

    private HistoryAdapter historyAdapter;
    private List<Recipe> recipeHistory;
    private RecipeDatabase recipeDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        BackButtonUtil.setupBackButton(this);

        recipeDatabase = new RecipeDatabase(this);

        // 初始化控件
        listViewHistory = findViewById(R.id.listView_history);
        tvEmptyHistory = findViewById(R.id.tv_empty_history);
        btnClearHistory = findViewById(R.id.btn_clear_history);
        btnWeeklyReport = findViewById(R.id.btn_weekly_report);
        emptyState = findViewById(R.id.empty_state);

        // 设置空状态文本
        tvEmptyHistory.setText("📝");

        loadHistory();

        historyAdapter = new HistoryAdapter(this, recipeHistory);
        listViewHistory.setAdapter(historyAdapter);

        listViewHistory.setOnItemClickListener((parent, view, position, id) -> {
            Recipe recipe = recipeHistory.get(position);
            Intent intent = new Intent(HistoryActivity.this, RecipeResultActivity.class);
            intent.putExtra("recipe", recipe);
            startActivity(intent);
        });

        listViewHistory.setOnItemLongClickListener((parent, view, position, id) -> {
            Recipe recipe = recipeHistory.get(position);
            recipeDatabase.deleteRecipe(recipe.getId());
            recipeHistory.remove(position);
            historyAdapter.notifyDataSetChanged();
            updateEmptyState();
            Toast.makeText(HistoryActivity.this, "已删除记录", Toast.LENGTH_SHORT).show();
            return true;
        });

        btnClearHistory.setOnClickListener(v -> {
            recipeDatabase.clearAllRecipes();
            recipeHistory.clear();
            historyAdapter.notifyDataSetChanged();
            updateEmptyState();
            Toast.makeText(HistoryActivity.this, "已清空所有历史记录", Toast.LENGTH_SHORT).show();
        });

        btnWeeklyReport.setOnClickListener(v -> {
            Intent intent = new Intent(HistoryActivity.this, WeeklyReportActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
        if (historyAdapter != null) {
            historyAdapter.notifyDataSetChanged();
        }
        updateEmptyState();
    }

    private void loadHistory() {
        recipeHistory = recipeDatabase.getAllRecipes();
        Log.d(TAG, "loadHistory: 找到 " + (recipeHistory == null ? 0 : recipeHistory.size()) + " 条记录");
    }

    private void updateEmptyState() {
        Log.d(TAG, "updateEmptyState: 列表大小 = " + (recipeHistory == null ? 0 : recipeHistory.size()));

        if (recipeHistory == null || recipeHistory.isEmpty()) {
            Log.d(TAG, "updateEmptyState: 显示空状态");
            emptyState.setVisibility(View.VISIBLE);
            listViewHistory.setVisibility(View.GONE);
        } else {
            Log.d(TAG, "updateEmptyState: 显示列表");
            emptyState.setVisibility(View.GONE);
            listViewHistory.setVisibility(View.VISIBLE);

            // 底部按钮一直可见
        }
    }
}