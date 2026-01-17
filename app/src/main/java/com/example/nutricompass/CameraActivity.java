package com.example.nutricompass;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_PICK_IMAGE = 2; // 新增：相册请求码
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final String TAG = "CameraActivity_ShanYu";

    private ImageView imageView;
    private TextView tvStatus;
    private Button btnTakePhoto, btnConfirmPhoto, btnPickPhoto; // 新增：btnPickPhoto

    private String userGoal;
    private Bitmap currentImageBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // 接收 MainActivity 传递的数据
        userGoal = getIntent().getStringExtra("user_goal");

        initViews();
        tvStatus.setText("当前目标: " + userGoal + "\n下一步：请拍摄或从相册选择食材照片");

        btnTakePhoto.setOnClickListener(v -> checkCameraPermissionAndOpenCamera());

        // 补全相册点击逻辑
        btnPickPhoto.setOnClickListener(v -> openGallery());

        btnConfirmPhoto.setOnClickListener(v -> {
            if (currentImageBitmap != null) {
                processImageWithAI(currentImageBitmap);
            }
        });
        btnConfirmPhoto.setEnabled(false);
    }

    private void initViews() {
        imageView = findViewById(R.id.imageView);
        tvStatus = findViewById(R.id.tv_status);
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        btnConfirmPhoto = findViewById(R.id.btn_confirm_photo);
        btnPickPhoto = findViewById(R.id.btn_pick_photo); // 初始化新增的相册按钮
    }

    // 补全：打开相册逻辑
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    private void checkCameraPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                Bundle extras = data.getExtras();
                currentImageBitmap = (Bitmap) extras.get("data");
            } else if (requestCode == REQUEST_PICK_IMAGE) {
                Uri imageUri = data.getData();
                try {
                    currentImageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
                }
            }

            if (currentImageBitmap != null) {
                imageView.setImageBitmap(currentImageBitmap);
                tvStatus.setText("照片已就绪，开始识别！");

                btnConfirmPhoto.setEnabled(true);
                // 设置为绿色 (十六进制颜色)
                btnConfirmPhoto.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
            }
        }
    }

    private void processImageWithAI(Bitmap bitmap) {
        // 第一阶段：刚点击按钮
        tvStatus.setText("🚀 正在上传并识别食材...");
        btnConfirmPhoto.setEnabled(false);
        // 变回灰色，表示处理中不可点击
        btnConfirmPhoto.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF9E9E9E));

        String imageBase64 = convertBitmapToBase64(bitmap);

        new Thread(() -> {
            try {
                // 模拟一个极短的视觉延迟，让用户看到“识别”和“分析”的文字切换（可选）
                Thread.sleep(800);

                // 第二阶段：更新文字（必须在 UI 线程执行）
                runOnUiThread(() -> tvStatus.setText("🔍 已识别食材，正在 AI 分析食谱..."));

                RecipeAnalyzer analyzer = new RecipeAnalyzer(this);
                // 执行核心 AI 分析逻辑
                Recipe recipe = analyzer.analyzeRecipe(imageBase64, userGoal, "正常");

                // 第三阶段：分析完成
                runOnUiThread(() -> {
                    if (recipe != null) {
                        tvStatus.setText("✨ 食谱生成成功！正在跳转...");

                        // 原有的跳转逻辑（一点没删）
                        Intent resultIntent = new Intent(CameraActivity.this, RecipeResultActivity.class);
                        resultIntent.putExtra("recipe_name", recipe.getName());
                        resultIntent.putExtra("recipe_description", recipe.getDescription());
                        resultIntent.putExtra("recipe_reason", recipe.getReason());
                        resultIntent.putExtra("recipe_weather", recipe.getWeatherCondition());
                        resultIntent.putExtra("recipe_nutrition", recipe.getBriefNutrition());
                        resultIntent.putExtra("recipe_prep_time", recipe.getPreparationTime());
                        resultIntent.putExtra("recipe_cook_time", recipe.getCookingTime());

                        if (recipe.getIngredients() != null) {
                            resultIntent.putExtra("recipe_ingredients", recipe.getIngredients().toArray(new String[0]));
                        }

                        if (recipe.getCookingSteps() != null) {
                            List<String> stepsList = new ArrayList<>();
                            String tips = "";
                            for (String step : recipe.getCookingSteps()) {
                                if (step.contains("烹饪小贴士") || step.contains("小贴士：")) {
                                    tips = step.replace("烹饪小贴士:", "").replace("小贴士：", "").trim();
                                } else {
                                    stepsList.add(step);
                                }
                            }
                            resultIntent.putExtra("recipe_steps", stepsList.toArray(new String[0]));
                            resultIntent.putExtra("recipe_cooking_tips", tips);
                        }

                        resultIntent.putExtra("recipe_difficulty_num", recipe.getDifficulty());

                        startActivity(resultIntent);
                        finish();
                    } else {
                        tvStatus.setText("❌ AI 识别失败，请重新拍摄");
                        btnConfirmPhoto.setEnabled(true);
                        btnConfirmPhoto.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Process AI Error", e);
                runOnUiThread(() -> {
                    tvStatus.setText("⚠️ 服务连接超时，请检查网络");
                    btnConfirmPhoto.setEnabled(true);
                    btnConfirmPhoto.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
                });
            }
        }).start();
    }

    private String convertBitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
        return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT);
    }
}