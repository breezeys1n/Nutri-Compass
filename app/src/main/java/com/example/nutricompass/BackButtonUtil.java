package com.example.nutricompass;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;

public class BackButtonUtil {

    /**
     * 设置返回按钮点击事件
     */
    public static void setupBackButton(Activity activity) {
        View backButton = activity.findViewById(R.id.back_button);
        if (backButton != null) {
            if (backButton instanceof ImageView) {
                ((ImageView) backButton).setOnClickListener(v -> activity.finish());
            } else {
                backButton.setOnClickListener(v -> activity.finish());
            }
        }
    }

    /**
     * 设置返回按钮点击事件（带自定义点击监听）
     */
    public static void setupBackButton(Activity activity, View.OnClickListener listener) {
        View backButton = activity.findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(listener);
        }
    }
}