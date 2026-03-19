package com.example.nutricompass;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NutritionReviewActivity extends AppCompatActivity {

    private static final String TAG = "NutritionReview";
    private static final int REQUEST_BEFORE_IMAGE = 1;
    private static final int REQUEST_AFTER_IMAGE = 2;
    private static final int REQUEST_PICK_BEFORE_IMAGE = 3;
    private static final int REQUEST_PICK_AFTER_IMAGE = 4;

    private ImageView ivBeforeMeal, ivAfterMeal;
    private TextView tvBeforeStatus, tvAfterStatus, tvAnalysisResult;
    private Button btnTakeBefore, btnPickBefore, btnTakeAfter, btnPickAfter, btnAnalyze;
    private ProgressBar progressBar;

    private Bitmap beforeImageBitmap = null;
    private Bitmap afterImageBitmap = null;
    private String originalRecipeIngredients = "";
    private NutritionAnalysis currentAnalysis = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nutrition_review);

        BackButtonUtil.setupBackButton(this);

        // 获取食谱信息（从Intent或数据库）
        Intent intent = getIntent();
        if (intent != null) {
            Recipe recipe = (Recipe) intent.getSerializableExtra("recipe");
            if (recipe != null && recipe.getIngredients() != null) {
                StringBuilder ingredients = new StringBuilder();
                for (String ingredient : recipe.getIngredients()) {
                    if (ingredients.length() > 0) ingredients.append(", ");
                    ingredients.append(ingredient);
                }
                originalRecipeIngredients = ingredients.toString();
            }
        }

        initViews();
        setupListeners();
    }

    private void initViews() {
        ivBeforeMeal = findViewById(R.id.iv_before_meal);
        ivAfterMeal = findViewById(R.id.iv_after_meal);
        tvBeforeStatus = findViewById(R.id.tv_before_status);
        tvAfterStatus = findViewById(R.id.tv_after_status);
        tvAnalysisResult = findViewById(R.id.tv_analysis_result);
        btnTakeBefore = findViewById(R.id.btn_take_before);
        btnPickBefore = findViewById(R.id.btn_pick_before);
        btnTakeAfter = findViewById(R.id.btn_take_after);
        btnPickAfter = findViewById(R.id.btn_pick_after);
        btnAnalyze = findViewById(R.id.btn_analyze);
        progressBar = findViewById(R.id.progress_bar);
        btnAnalyze.setEnabled(false);
    }

    private void setupListeners() {
        btnTakeBefore.setOnClickListener(v -> openCameraForImage(REQUEST_BEFORE_IMAGE));
        btnPickBefore.setOnClickListener(v -> openGalleryForImage(REQUEST_PICK_BEFORE_IMAGE));
        btnTakeAfter.setOnClickListener(v -> openCameraForImage(REQUEST_AFTER_IMAGE));
        btnPickAfter.setOnClickListener(v -> openGalleryForImage(REQUEST_PICK_AFTER_IMAGE));

        btnAnalyze.setOnClickListener(v -> {
            if (beforeImageBitmap != null && afterImageBitmap != null) {
                analyzeFoodLeftovers();
            } else {
                Toast.makeText(this, "请先拍摄或选择餐前和餐后照片", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openCameraForImage(int requestCode) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, requestCode);
        } else {
            Toast.makeText(this, "无法打开相机", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGalleryForImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {

            Bitmap imageBitmap = null;

            // 处理相机拍摄的照片
            if (requestCode == REQUEST_BEFORE_IMAGE || requestCode == REQUEST_AFTER_IMAGE) {
                Bundle extras = data.getExtras();
                imageBitmap = (Bitmap) extras.get("data");
            }
            // 处理相册选择的照片
            else if (requestCode == REQUEST_PICK_BEFORE_IMAGE || requestCode == REQUEST_PICK_AFTER_IMAGE) {
                Uri imageUri = data.getData();
                try {
                    imageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (imageBitmap != null) {
                // 更新UI
                if (requestCode == REQUEST_BEFORE_IMAGE || requestCode == REQUEST_PICK_BEFORE_IMAGE) {
                    beforeImageBitmap = imageBitmap;
                    ivBeforeMeal.setImageBitmap(imageBitmap);
                    tvBeforeStatus.setText("✓ 餐前照片已上传");
                    tvBeforeStatus.setTextColor(0xFF4CAF50);
                } else if (requestCode == REQUEST_AFTER_IMAGE || requestCode == REQUEST_PICK_AFTER_IMAGE) {
                    afterImageBitmap = imageBitmap;
                    ivAfterMeal.setImageBitmap(imageBitmap);
                    tvAfterStatus.setText("✓ 餐后照片已上传");
                    tvAfterStatus.setTextColor(0xFF4CAF50);
                }

                // 检查是否可以开始分析
                if (beforeImageBitmap != null && afterImageBitmap != null) {
                    btnAnalyze.setEnabled(true);
                    btnAnalyze.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
                }
            }
        }
    }

    private void analyzeFoodLeftovers() {
        progressBar.setVisibility(View.VISIBLE);
        tvAnalysisResult.setText("🔄 正在分析剩余食物...");

        new Thread(() -> {
            try {
                String beforeBase64 = convertBitmapToBase64(beforeImageBitmap);
                String afterBase64 = convertBitmapToBase64(afterImageBitmap);

                FoodLeftoversAnalyzer analyzer = new FoodLeftoversAnalyzer();
                currentAnalysis = analyzer.analyzeLeftovers(beforeBase64, afterBase64, originalRecipeIngredients);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (currentAnalysis != null) {
                        displayAnalysisResult(currentAnalysis);
                    } else {
                        tvAnalysisResult.setText("❌ 分析失败，请重试");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "分析剩余食物出错", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvAnalysisResult.setText("⚠️ 分析过程中出错: " + e.getMessage());
                });
            }
        }).start();
    }

    private void displayAnalysisResult(NutritionAnalysis analysis) {
        StringBuilder result = new StringBuilder();
        result.append("📊 营养复盘分析结果\n\n");
        result.append("🍽️ 剩余食物比例: ").append(analysis.getEstimatedLeftoverPercentage()).append("%\n");
        result.append("📉 实际摄入比例: ").append(String.format("%.0f", analysis.getActualIntakeRatio() * 100)).append("%\n");
        result.append("🥗 主要剩余食材: ").append(analysis.getLeftoverItems()).append("\n\n");
        result.append("🏆 完成度评价: ").append(analysis.getCompletionStatus()).append("\n\n");
        result.append("💪 营养影响分析:\n").append(analysis.getNutritionImpact()).append("\n\n");
        result.append("💡 后续建议:\n").append(analysis.getSuggestions());
        tvAnalysisResult.setText(result.toString());
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