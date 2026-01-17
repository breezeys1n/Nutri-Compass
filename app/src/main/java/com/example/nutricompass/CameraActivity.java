package com.example.nutricompass;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_PICK_IMAGE = 2;
    private static final String TAG = "CameraActivity_ShanYu";

    private ImageView imageView;
    private TextView tvStatus;
    private Button btnTakePhoto, btnPickPhoto, btnConfirmPhoto;

    private String userGoal, userHeight, userWeight;
    private Bitmap currentImageBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // 1. 获取传递的数据
        userGoal = getIntent().getStringExtra("user_goal");
        userHeight = getIntent().getStringExtra("user_height");
        userWeight = getIntent().getStringExtra("user_weight");

        initViews();

        tvStatus.setText("当前目标: " + userGoal + "\n请拍摄或从相册选择食材");

        // 2. 拍照逻辑
        btnTakePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            }
        });

        // 3. 相册逻辑
        btnPickPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        });

        // 4. AI识别确认逻辑
        btnConfirmPhoto.setOnClickListener(v -> {
            if (currentImageBitmap != null) {
                processImageWithAI(currentImageBitmap);
            }
        });
    }

    private void initViews() {
        imageView = findViewById(R.id.imageView);
        tvStatus = findViewById(R.id.tv_status);
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        btnPickPhoto = findViewById(R.id.btn_pick_photo);
        btnConfirmPhoto = findViewById(R.id.btn_confirm_photo);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            try {
                if (requestCode == REQUEST_IMAGE_CAPTURE) {
                    // 处理拍照：通常返回缩略图
                    Bundle extras = data.getExtras();
                    currentImageBitmap = (Bitmap) extras.get("data");
                } else if (requestCode == REQUEST_PICK_IMAGE) {
                    // 处理相册：获取 URI 并进行解码
                    Uri imageUri = data.getData();
                    currentImageBitmap = decodeUriToBitmap(imageUri);
                }

                if (currentImageBitmap != null) {
                    imageView.setImageBitmap(currentImageBitmap);
                    tvStatus.setText("照片已就绪，可以开始识别！");
                    btnConfirmPhoto.setEnabled(true);
                    btnConfirmPhoto.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
                }
            } catch (Exception e) {
                Log.e(TAG, "获取图片失败", e);
                Toast.makeText(this, "图片加载失败，请重试", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 将 Uri 转为 Bitmap，并防止 OOM
    private Bitmap decodeUriToBitmap(Uri uri) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2; // 采样率缩放，防止图片太大内存溢出
        Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
        input.close();
        return bitmap;
    }

    private void processImageWithAI(Bitmap bitmap) {
        tvStatus.setText("膳愈 AI 正在努力分析食材...");
        btnConfirmPhoto.setEnabled(false);

        String imageBase64 = convertBitmapToBase64(bitmap);
        callAIRecipeAPI(imageBase64, userGoal, userHeight, userWeight);
    }

    private String convertBitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // 膳愈 AI 识别不需要超高清图，压缩至 80% 可极大提高上传速度
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    private void callAIRecipeAPI(String base64, String goal, String h, String w) {
        // 这里对接你之前的 RecipeAnalyzer 逻辑
        // 请参考你之前项目中通过线程调用 API 的部分
        Toast.makeText(this, "正在连接膳愈云端...", Toast.LENGTH_SHORT).show();
        // ... 原有的分析与跳转代码
    }
}