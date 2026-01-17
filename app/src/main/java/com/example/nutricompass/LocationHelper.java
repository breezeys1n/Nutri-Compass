package com.example.nutricompass;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import androidx.core.app.ActivityCompat;

public class LocationHelper {

    // 返回格式为 "经度,纬度" 的字符串，方便直接给和风天气使用
    public static String getCoordinates(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        // 检查权限
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "116.40,39.90"; // 默认北京坐标
        }

        // 尝试从网络定位（快）或GPS（准）获取
        Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (location == null) {
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }

        if (location != null) {
            double lng = location.getLongitude();
            double lat = location.getLatitude();
            // 注意：和风天气要求格式为 "经度,纬度"
            return String.format("%.2f,%.2f", lng, lat);
        }

        return "116.40,39.90"; // 获取失败返回北京坐标
    }
}