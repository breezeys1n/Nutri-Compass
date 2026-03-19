package com.example.nutricompass;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_PICK_IMAGE = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final String TAG = "CameraActivity";

    private ImageView imageView;
    private TextView tvStatus, tvUserGoal, tvProgressIcon, tvProgressTitle, tvProgressDescription, tvProgressPercent;
    private Button btnTakePhoto, btnConfirmPhoto, btnPickPhoto;
    private CardView cardStatus, cardProgress;
    private ProgressBar progressBar;
    private ObjectAnimator rotateAnim;

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
        setupListeners();
        initUI();
    }

    private void initViews() {
        imageView = findViewById(R.id.imageView);
        tvStatus = findViewById(R.id.tv_status);
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        btnConfirmPhoto = findViewById(R.id.btn_confirm_photo);
        btnPickPhoto = findViewById(R.id.btn_pick_photo);

        tvUserGoal = findViewById(R.id.tv_user_goal);
        cardStatus = findViewById(R.id.card_status);
        cardProgress = findViewById(R.id.card_progress);
        tvProgressIcon = findViewById(R.id.tv_progress_icon);
        tvProgressTitle = findViewById(R.id.tv_progress_title);
        tvProgressDescription = findViewById(R.id.tv_progress_description);
        tvProgressPercent = findViewById(R.id.tv_progress_percent);
        progressBar = findViewById(R.id.progress_bar);

        if (userGoal != null) {
            tvUserGoal.setText(userGoal);
        }
    }

    private void setupListeners() {
        btnTakePhoto.setOnClickListener(v -> checkCameraPermissionAndOpenCamera());
        btnPickPhoto.setOnClickListener(v -> openGallery());
        btnConfirmPhoto.setOnClickListener(v -> {
            if (currentImageBitmap != null) {
                processImageWithAI(currentImageBitmap);
            }
        });
    }

    private void initUI() {
        cardStatus.setVisibility(View.VISIBLE);
        cardProgress.setVisibility(View.GONE);
        tvStatus.setText("当前目标: " + userGoal + "\n下一步：请拍摄或从相册选择食材照片");
        btnConfirmPhoto.setEnabled(false);
        btnConfirmPhoto.setBackgroundTintList(ColorStateList.valueOf(0xFF9E9E9E));
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
                }
            }

            if (currentImageBitmap != null) {
                imageView.setImageBitmap(currentImageBitmap);
                cardStatus.setVisibility(View.VISIBLE);
                cardProgress.setVisibility(View.GONE);
                tvStatus.setText("照片已就绪，点击确认开始识别");
                btnConfirmPhoto.setEnabled(true);
                btnConfirmPhoto.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
            }
        }
    }

    private void processImageWithAI(Bitmap bitmap) {
        cardStatus.setVisibility(View.GONE);
        cardProgress.setVisibility(View.VISIBLE);
        btnConfirmPhoto.setEnabled(false);

        tvProgressIcon.setText("🔄");
        tvProgressTitle.setText("正在识别食材");
        tvProgressDescription.setText("AI正在分析图片中的食材...");
        tvProgressPercent.setText("0%");
        progressBar.setProgress(0);

        startProgressAnimation();

        String imageBase64 = convertBitmapToBase64(bitmap);

        new Thread(() -> {
            try {
                for (int i = 0; i <= 100; i += 20) {
                    final int progress = i;
                    runOnUiThread(() -> {
                        tvProgressPercent.setText(progress + "%");
                        progressBar.setProgress(progress);
                        if (progress == 20) tvProgressDescription.setText("正在识别食材种类...");
                        else if (progress == 80) tvProgressDescription.setText("生成个性化食谱...");
                    });
                    Thread.sleep(300);
                }

                RecipeAnalyzer analyzer = new RecipeAnalyzer(this);
                Recipe recipe = analyzer.analyzeRecipe(imageBase64, userGoal, userStatusDesc);

                runOnUiThread(() -> {
                    stopProgressAnimation();
                    if (isValidRecipe(recipe)) {
                        tvProgressIcon.setText("✅");
                        tvProgressTitle.setText("识别完成");
                        tvProgressDescription.setText("正在保存并跳转...");
                        tvProgressPercent.setText("100%");
                        progressBar.setProgress(100);

                        new RecipeDatabase(this).addRecipe(recipe);

                        new Handler().postDelayed(() -> navigateToResult(recipe), 800);
                    } else {
                        handleProcessError("AI 未能生成完整食谱内容");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Process AI Error", e);
                runOnUiThread(() -> handleProcessError("服务连接超时，请重试"));
            }
        }).start();
    }

    private boolean isValidRecipe(Recipe recipe) {
        return recipe != null &&
                recipe.getTitle() != null && !recipe.getTitle().isEmpty() &&
                recipe.getCookingSteps() != null && !recipe.getCookingSteps().isEmpty();
    }

    private void handleProcessError(String message) {
        stopProgressAnimation();
        cardProgress.setVisibility(View.GONE);
        cardStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("❌ " + message);
        btnConfirmPhoto.setEnabled(true);
        btnConfirmPhoto.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void startProgressAnimation() {
        if (rotateAnim != null) rotateAnim.cancel();
        rotateAnim = ObjectAnimator.ofFloat(tvProgressIcon, "rotation", 0f, 360f);
        rotateAnim.setDuration(2000);
        rotateAnim.setRepeatCount(ValueAnimator.INFINITE);
        rotateAnim.setInterpolator(new LinearInterpolator());
        rotateAnim.start();
    }

    private void stopProgressAnimation() {
        if (rotateAnim != null) {
            rotateAnim.cancel();
            rotateAnim = null;
        }
        tvProgressIcon.setRotation(0f);
    }

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
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }
}