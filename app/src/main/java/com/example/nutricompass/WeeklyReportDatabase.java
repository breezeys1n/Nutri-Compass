
package com.example.nutricompass;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class WeeklyReportDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "weekly_reports.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "weekly_reports";

    private static final String COLUMN_ID = "id";
    private static final String COLUMN_START_DATE = "start_date";
    private static final String COLUMN_END_DATE = "end_date";
    private static final String COLUMN_GENERATE_TIME = "generate_time";
    private static final String COLUMN_REPORT_JSON = "report_json";
    private static final String COLUMN_IS_READ = "is_read";

    private Gson gson = new Gson();

    public WeeklyReportDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_START_DATE + " TEXT NOT NULL," +
                COLUMN_END_DATE + " TEXT NOT NULL," +
                COLUMN_GENERATE_TIME + " TEXT NOT NULL," +
                COLUMN_REPORT_JSON + " TEXT NOT NULL," +
                COLUMN_IS_READ + " INTEGER DEFAULT 0)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    // 保存周报告
    public long saveWeeklyReport(WeeklyReport report) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_START_DATE, report.getStartDate());
        values.put(COLUMN_END_DATE, report.getEndDate());
        values.put(COLUMN_GENERATE_TIME, report.getGenerateTime());
        values.put(COLUMN_REPORT_JSON, gson.toJson(report));
        values.put(COLUMN_IS_READ, 0);

        return db.insert(TABLE_NAME, null, values);
    }

    // 获取最新周报告
    public WeeklyReport getLatestReport() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_NAME +
                " ORDER BY " + COLUMN_GENERATE_TIME + " DESC LIMIT 1";
        Cursor cursor = db.rawQuery(query, null);

        WeeklyReport report = null;
        if (cursor.moveToFirst()) {
            String json = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REPORT_JSON));
            report = gson.fromJson(json, WeeklyReport.class);

            // 更新已读状态
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
            markAsRead(id);
        }
        cursor.close();
        return report;
    }

    // 获取指定周的报告
    public WeeklyReport getReportByWeek(String startDate, String endDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_NAME +
                " WHERE " + COLUMN_START_DATE + "=? AND " +
                COLUMN_END_DATE + "=?";
        Cursor cursor = db.rawQuery(query, new String[]{startDate, endDate});

        WeeklyReport report = null;
        if (cursor.moveToFirst()) {
            String json = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REPORT_JSON));
            report = gson.fromJson(json, WeeklyReport.class);
        }
        cursor.close();
        return report;
    }

    // 标记为已读
    private void markAsRead(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_READ, 1);
        db.update(TABLE_NAME, values, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
    }

    // 获取所有周报告列表（用于历史查看）
    public List<WeeklyReport> getAllReports() {
        List<WeeklyReport> reports = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_NAME + " ORDER BY " +
                COLUMN_GENERATE_TIME + " DESC";
        Cursor cursor = db.rawQuery(query, null);

        while (cursor.moveToNext()) {
            String json = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REPORT_JSON));
            WeeklyReport report = gson.fromJson(json, WeeklyReport.class);
            reports.add(report);
        }
        cursor.close();
        return reports;
    }
}