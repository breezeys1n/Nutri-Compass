// CameraActivity.java
package com.example.nutricompass;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_PICK_IMAGE = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final String TAG = "CameraActivity_ShanYu";

    // ===== 新增：调试模式开关 =====
    private static final boolean DEBUG_MODE = true;  // true = 使用默认图片，false = 正常拍照
    private static final String DEFAULT_IMAGE_ASSET = "default_food.jpg"; // 默认图片放在 assets 文件夹

    private ImageView imageView;
    private TextView tvStatus;
    private Button btnTakePhoto, btnConfirmPhoto, btnPickPhoto;

    private String userGoal;
    private String userStatusDesc = "";
    private Bitmap currentImageBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        BackButtonUtil.setupBackButton(this);

        userGoal = getIntent().getStringExtra("user_goal");
        userStatusDesc = getIntent().getStringExtra("user_status_desc");

        initViews();

        // 设置按钮监听器（先设置）
        btnTakePhoto.setOnClickListener(v -> checkCameraPermissionAndOpenCamera());
        btnPickPhoto.setOnClickListener(v -> openGallery());
        btnConfirmPhoto.setOnClickListener(v -> {
            if (currentImageBitmap != null) {
                processImageWithAI(currentImageBitmap);
            }
        });

        // 根据模式设置按钮状态
        if (DEBUG_MODE) {
            loadDefaultImage();  // 这个方法里面会 setEnabled(true)
        } else {
            tvStatus.setText("当前目标: " + userGoal + "\n下一步：请拍摄或从相册选择食材照片");
            btnConfirmPhoto.setEnabled(false);  // 非调试模式才设为 false
            btnConfirmPhoto.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF9E9E9E));
        }
    }

    // ===== 新增：从 assets 加载默认图片 =====
    private void loadDefaultImage() {
        try {
            InputStream is = getAssets().open(DEFAULT_IMAGE_ASSET);
            currentImageBitmap = BitmapFactory.decodeStream(is);
            is.close();

            if (currentImageBitmap != null) {
                imageView.setImageBitmap(currentImageBitmap);
                tvStatus.setText("📷 [调试模式] 使用默认食材图片\n当前目标: " + userGoal);

                btnConfirmPhoto.setEnabled(true);
                btnConfirmPhoto.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));

                Log.d(TAG, "调试模式：已加载默认图片");
            }
        } catch (IOException e) {
            Log.e(TAG, "加载默认图片失败", e);
            tvStatus.setText("⚠️ 调试模式：默认图片不存在，请正常拍照");
        }
    }

    private void initViews() {
        imageView = findViewById(R.id.imageView);
        tvStatus = findViewById(R.id.tv_status);
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        btnConfirmPhoto = findViewById(R.id.btn_confirm_photo);
        btnPickPhoto = findViewById(R.id.btn_pick_photo);
    }

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
                btnConfirmPhoto.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
            }
        }
    }

    private void processImageWithAI(Bitmap bitmap) {
        tvStatus.setText("🚀 正在上传并识别食材...");
        btnConfirmPhoto.setEnabled(false);
        btnConfirmPhoto.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF9E9E9E));

        String imageBase64 = convertBitmapToBase64(bitmap);

        new Thread(() -> {
            try {
                Thread.sleep(800);
                runOnUiThread(() -> tvStatus.setText("🔍 已识别食材，正在 AI 分析食谱..."));

                RecipeAnalyzer analyzer = new RecipeAnalyzer(this);
                Recipe recipe = analyzer.analyzeRecipe(imageBase64, userGoal, userStatusDesc);

                runOnUiThread(() -> {
                    if (recipe != null) {
                        tvStatus.setText("✨ 食谱生成成功！正在跳转...");
                        // ... 跳转代码不变 ...
                        navigateToResult(recipe);
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

    // ===== 新增：跳转方法 =====
    private void navigateToResult(Recipe recipe) {
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
    }

    private String convertBitmapToBase64(Bitmap bitmap) {
        int maxSize = 512;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, (int)(width * ratio), (int)(height * ratio), true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.JPEG, 40, out);
        byte[] byteArray = out.toByteArray();

        Log.d(TAG, "🚀 极度压缩后 Base64 长度: " + byteArray.length);

        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }
}