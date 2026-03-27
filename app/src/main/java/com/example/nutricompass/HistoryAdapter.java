package com.example.nutricompass;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends ArrayAdapter<Recipe> {

    private final int resource;

    public HistoryAdapter(@NonNull android.content.Context context, List<Recipe> recipes) {
        super(context, R.layout.item_history, recipes);
        this.resource = R.layout.item_history;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(resource, parent, false);
        }

        Recipe recipe = getItem(position);
        if (recipe != null) {
            TextView tvTitle = convertView.findViewById(R.id.tv_recipe_title);
            TextView tvDate = convertView.findViewById(R.id.tv_recipe_date);
            TextView tvDescription = convertView.findViewById(R.id.tv_recipe_description);
            TextView tvCalories = convertView.findViewById(R.id.tv_recipe_calories);


            String title = recipe.getTitle();
            if (title == null || title.isEmpty()) {
                title = recipe.getName();
            }
            tvTitle.setText(title != null ? title : "未知食谱");

            // 设置日期
            String date = recipe.getDate();
            if (date == null || date.isEmpty()) {
                // 如果没有日期，使用当前日期
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
                date = sdf.format(new Date());
            }
            tvDate.setText(date);

            // 设置描述
            String description = recipe.getDescription();
            tvDescription.setText(description != null && !description.isEmpty() ? description : "暂无描述");

            // 设置热量信息
            if (recipe.getCalories() > 0) {
                tvCalories.setText(recipe.getCalories() + " 大卡");
            } else if (recipe.getNutrition() != null && recipe.getNutrition().getCalories() > 0) {
                tvCalories.setText(String.format(Locale.CHINA, "%.0f 大卡", recipe.getNutrition().getCalories()));
            } else {
                tvCalories.setText("热量信息");
            }
        }

        return convertView;
    }
}