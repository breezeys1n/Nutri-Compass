package com.example.nutricompass;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import java.util.ArrayList;
import java.util.List;
public class CameraActivity extends AppCompatActivity {

    // 定义请求码常量
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final String TAG = "CameraActivity";

    // 视图控件
    private ImageView imageView;
    private TextView tvStatus;
    private Button btnTakePhoto, btnConfirmPhoto;

    // 数据变量
    private String userGoal;
    private String userHeight;
    private String userWeight;
    private Bitmap currentImageBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // 1. 接收从 MainActivity 传递过来的用户数据
        Intent intent = getIntent();
        userGoal = intent.getStringExtra("user_goal");
        userHeight = intent.getStringExtra("user_height");
        userWeight = intent.getStringExtra("user_weight");

        // 2. 初始化界面控件
        initViews();

        // 3. 显示当前目标状态
        tvStatus.setText("当前目标: " + userGoal + "\n下一步：请拍摄食材照片");

        // 4. 拍照按钮点击事件
        btnTakePhoto.setOnClickListener(v -> checkCameraPermissionAndOpenCamera());

        // 5. 确认识别按钮点击事件
        btnConfirmPhoto.setOnClickListener(v -> {
            if (currentImageBitmap != null) {
                processImageWithAI(currentImageBitmap);
            } else {
                Toast.makeText(this, "请先拍摄一张照片", Toast.LENGTH_SHORT).show();
            }
        });

        // 初始状态禁用确认按钮
        btnConfirmPhoto.setEnabled(false);
    }

    private void initViews() {
        imageView = findViewById(R.id.imageView);
        tvStatus = findViewById(R.id.tv_status);
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        btnConfirmPhoto = findViewById(R.id.btn_confirm_photo);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "相机权限被拒绝", Toast.LENGTH_LONG).show();
                tvStatus.setText("相机权限被拒绝，请检查设置。");
            }
        }
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } else {
            Toast.makeText(this, "未找到可用的相机应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                currentImageBitmap = (Bitmap) extras.get("data");
                if (currentImageBitmap != null) {
                    // 强制刷新 UI 确保图片显示
                    imageView.setImageBitmap(currentImageBitmap);
                    imageView.invalidate();

                    // 更新状态并启用按钮
                    tvStatus.setText("照片已获取，可以开始识别！");
                    btnConfirmPhoto.setEnabled(true);
                    btnConfirmPhoto.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
                }
            }
        } else if (resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "取消了拍照", Toast.LENGTH_SHORT).show();
        }
    }

    private void processImageWithAI(Bitmap bitmap) {
        tvStatus.setText("正在连接AI服务，识别食材中...");
        btnConfirmPhoto.setEnabled(false);
        Toast.makeText(this, "开始识别食材...", Toast.LENGTH_SHORT).show();

        // 将图片转为 Base64
        String imageBase64 = convertBitmapToBase64(bitmap);
        Log.d(TAG, "图片转换完成，Base64长度: " + imageBase64.length());

        // 调用 API
        callAIRecipeAPI(imageBase64, userGoal, userHeight, userWeight);
    }

    private String convertBitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    private void callAIRecipeAPI(String imageBase64, String goal, String height, String weight) {
        // 获取用户信息
        UserProfile userProfile = new UserProfile(this);

        // 获取用户状态（可以扩展让用户输入）
        String userCondition = "正常"; // 默认状态，后续可以让用户选择

        // 调用AI分析服务
        new Thread(() -> {
            try {
                // 这里调用真正的AI服务
                RecipeAnalyzer analyzer = new RecipeAnalyzer(this);
                Recipe recipe = analyzer.analyzeRecipe(imageBase64, goal, userCondition);

                // 切换到主线程更新UI
                runOnUiThread(() -> {
                    if (recipe != null) {
                        // 跳转到结果页面
                        Intent resultIntent = new Intent(CameraActivity.this, RecipeResultActivity.class);

                        // 传递完整的食谱数据
                        resultIntent.putExtra("recipe_name", recipe.getName());
                        resultIntent.putExtra("recipe_description", recipe.getDescription());
                        resultIntent.putExtra("recipe_reason", recipe.getReason());
                        resultIntent.putExtra("recipe_weather", recipe.getWeatherCondition());
                        resultIntent.putExtra("recipe_nutrition", recipe.getBriefNutrition());

                        // 传递食材数组
                        if (recipe.getIngredients() != null && !recipe.getIngredients().isEmpty()) {
                            resultIntent.putExtra("recipe_ingredients",
                                    recipe.getIngredients().toArray(new String[0]));
                        }

                        // 传递烹饪步骤数组
                        if (recipe.getCookingSteps() != null && !recipe.getCookingSteps().isEmpty()) {
                            // 过滤掉小贴士
                            List<String> steps = new ArrayList<>();
                            for (String step : recipe.getCookingSteps()) {
                                if (!step.contains("烹饪小贴士") && !step.contains("小贴士：")) {
                                    steps.add(step);
                                }
                            }
                            resultIntent.putExtra("recipe_steps", steps.toArray(new String[0]));

                            // 提取小贴士
                            for (String step : recipe.getCookingSteps()) {
                                if (step.contains("烹饪小贴士") || step.contains("小贴士：")) {
                                    String tips = step.replace("烹饪小贴士:", "")
                                            .replace("小贴士：", "")
                                            .trim();
                                    resultIntent.putExtra("recipe_cooking_tips", tips);
                                    break;
                                }
                            }
                        }

                        // 传递时间信息
                        resultIntent.putExtra("recipe_prep_time", recipe.getPreparationTime());
                        resultIntent.putExtra("recipe_cook_time", recipe.getCookingTime());
                        resultIntent.putExtra("recipe_difficulty_num", recipe.getDifficulty());

                        startActivity(resultIntent);
                    } else {
                        Toast.makeText(CameraActivity.this, "AI分析失败，请重试", Toast.LENGTH_SHORT).show();
                        btnConfirmPhoto.setEnabled(true);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(CameraActivity.this, "网络连接失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnConfirmPhoto.setEnabled(true);
                });
            }
        }).start();
    }
}