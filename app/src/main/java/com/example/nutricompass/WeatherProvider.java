package com.example.nutricompass.provider;

import android.util.Log;
import com.example.nutricompass.BuildConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;

public class WeatherProvider {
    private static final String TAG = "WeatherProvider_Debug";

    /**
     * 根据经纬度动态获取天气信息
     * @param location 格式为 "经度,纬度" (例如 "118.86,37.95")
     * @return 包含天气信息的 JSON 字符串
     */
    public static String fetchWeather(String location) {
        // 从 BuildConfig 获取 local.properties 中配置的 Key
        String apiKey = BuildConfig.QWEATHER_API_KEY;
        OkHttpClient client = new OkHttpClient();

        try {
            Log.d(TAG, ">>> 开始准备动态获取天气，坐标: " + location);

            // 步骤 1：逆地理编码 - 通过经纬度获取 adcode (行政区划代码)
            String adcode = getAdcodeByLocation(client, apiKey, location);
            if (adcode == null || adcode.isEmpty()) {
                Log.e(TAG, "无法解析坐标对应的城市代码 (adcode)");
                return null;
            }

            // 步骤 2：天气查询 - 通过 adcode 获取实况天气
            return getWeatherByAdcode(client, apiKey, adcode);

        } catch (Exception e) {
            Log.e(TAG, "获取天气流程发生异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 内部方法：获取城市代码 (adcode)
     */
    private static String getAdcodeByLocation(OkHttpClient client, String key, String location) throws IOException {
        String geoUrl = "https://restapi.amap.com/v3/geocode/regeo?location=" + location + "&key=" + key;
        Request request = new Request.Builder().url(geoUrl).build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                // 状态 1 代表成功
                if ("1".equals(json.optString("status"))) {
                    return json.getJSONObject("regeocode")
                            .getJSONObject("addressComponent")
                            .optString("adcode");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析 adcode 失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 内部方法：获取实况天气
     */
    private static String getWeatherByAdcode(OkHttpClient client, String key, String adcode) throws IOException {
        String weatherUrl = "https://restapi.amap.com/v3/weather/weatherInfo?city=" + adcode + "&key=" + key;
        Request request = new Request.Builder().url(weatherUrl).build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String result = response.body().string();
                Log.d(TAG, "高德天气数据获取成功: " + result);
                return result;
            }
        }
        return null;
    }
}