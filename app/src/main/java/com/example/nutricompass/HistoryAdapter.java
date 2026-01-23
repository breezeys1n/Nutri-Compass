package com.example.nutricompass;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

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

            tvTitle.setText(recipe.getTitle());
            tvDate.setText(recipe.getDate());
            tvDescription.setText(recipe.getDescription());

            // 显示热量信息
            if (recipe.getCalories() > 0) {
                tvCalories.setText(recipe.getCalories() + " 大卡");
            } else {
                tvCalories.setText("热量信息");
            }
        }

        return convertView;
    }
}