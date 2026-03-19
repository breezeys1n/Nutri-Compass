package com.example.nutricompass;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecipeDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "recipes.db";
    private static final int DATABASE_VERSION = 4; // 升级版本以匹配新字段
    private static final String TABLE_NAME = "recipes";

    // 原有字段
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_DESCRIPTION = "description";
    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_CALORIES = "calories";
    private static final String COLUMN_INGREDIENTS = "ingredients";
    private static final String COLUMN_PROTEIN = "protein";
    private static final String COLUMN_CARBS = "carbs";
    private static final String COLUMN_FAT = "fat";
    private static final String COLUMN_FIBER = "fiber";
    private static final String COLUMN_MEAL_TYPE = "meal_type";

    // 补充缺失的字段常量
    private static final String COLUMN_REASON = "reason";
    private static final String COLUMN_PREP_TIME = "prep_time";
    private static final String COLUMN_COOK_TIME = "cook_time";
    private static final String COLUMN_DIFFICULTY = "difficulty";
    private static final String COLUMN_STEPS = "steps";

    public RecipeDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_TITLE + " TEXT, " +
                COLUMN_DESCRIPTION + " TEXT, " +
                COLUMN_REASON + " TEXT, " +
                COLUMN_DATE + " TEXT, " +
                COLUMN_CALORIES + " REAL, " +
                COLUMN_PROTEIN + " REAL, " +
                COLUMN_CARBS + " REAL, " +
                COLUMN_FAT + " REAL, " +
                COLUMN_FIBER + " REAL, " +
                COLUMN_INGREDIENTS + " TEXT, " +
                COLUMN_STEPS + " TEXT, " +
                COLUMN_PREP_TIME + " TEXT, " +
                COLUMN_COOK_TIME + " TEXT, " +
                COLUMN_DIFFICULTY + " INTEGER, " +
                COLUMN_MEAL_TYPE + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public long addRecipe(Recipe recipe) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, recipe.getTitle());
        values.put(COLUMN_DESCRIPTION, recipe.getDescription());
        values.put(COLUMN_REASON, recipe.getReason()); // 恢复理由保存
        values.put(COLUMN_DATE, recipe.getDate());

        // 优先保存 NutritionInfo 对象中的数据
        if (recipe.getNutrition() != null) {
            values.put(COLUMN_CALORIES, recipe.getNutrition().getCalories());
            values.put(COLUMN_PROTEIN, recipe.getNutrition().getProtein());
            values.put(COLUMN_CARBS, recipe.getNutrition().getCarbs());
            values.put(COLUMN_FAT, recipe.getNutrition().getFat());
        } else {
            values.put(COLUMN_CALORIES, recipe.getCalories());
            values.put(COLUMN_PROTEIN, recipe.getProtein());
            values.put(COLUMN_CARBS, recipe.getCarbs());
            values.put(COLUMN_FAT, recipe.getFat());
        }

        values.put(COLUMN_FIBER, recipe.getFiber());
        values.put(COLUMN_MEAL_TYPE, recipe.getMealType());
        values.put(COLUMN_PREP_TIME, recipe.getPreparationTime());
        values.put(COLUMN_COOK_TIME, recipe.getCookingTime());
        values.put(COLUMN_DIFFICULTY, recipe.getDifficulty());

        if (recipe.getIngredients() != null) {
            values.put(COLUMN_INGREDIENTS, String.join("|", recipe.getIngredients()));
        }
        if (recipe.getCookingSteps() != null) {
            values.put(COLUMN_STEPS, String.join("|", recipe.getCookingSteps()));
        }

        return db.insert(TABLE_NAME, null, values);
    }

    // 修复 HistoryActivity 报错：添加 deleteRecipe
    public void deleteRecipe(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
    }

    // 修复 WeeklyReport 报错：添加 getDateRange
    public String[] getDateRange() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT MIN(" + COLUMN_DATE + "), MAX(" + COLUMN_DATE + ") FROM " + TABLE_NAME, null);
        String[] range = null;
        if (cursor.moveToFirst() && cursor.getString(0) != null) {
            range = new String[]{cursor.getString(0), cursor.getString(1)};
        }
        cursor.close();
        return range;
    }

    // 修复 WeeklyReport 报错：添加 getRecipesByDateRange
    public List<Recipe> getRecipesByDateRange(String startDate, String endDate) {
        List<Recipe> recipes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE date(" + COLUMN_DATE + ") BETWEEN date(?) AND date(?)", new String[]{startDate, endDate});
        if (cursor.moveToFirst()) {
            do { recipes.add(cursorToRecipe(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        return recipes;
    }

    public List<Recipe> getAllRecipes() {
        List<Recipe> recipes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY " + COLUMN_ID + " DESC", null);
        if (cursor.moveToFirst()) {
            do { recipes.add(cursorToRecipe(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        return recipes;
    }

    private Recipe cursorToRecipe(Cursor cursor) {
        Recipe recipe = new Recipe();
        // 恢复你原有的 try-catch 结构逻辑
        recipe.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)));
        recipe.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)));
        recipe.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESCRIPTION)));

        try { recipe.setReason(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REASON))); } catch (Exception e) {}
        recipe.setDate(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE)));

        // 读取数值并注入 NutritionInfo (解决显示为0的关键)
        double cal = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CALORIES));
        double pro = 0, carb = 0, fat = 0;

        try { pro = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PROTEIN)); } catch (Exception e) {}
        try { carb = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CARBS)); } catch (Exception e) {}
        try { fat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_FAT)); } catch (Exception e) {}

        // 同步到 NutritionInfo 对象
        NutritionInfo ni = new NutritionInfo();
        ni.setCalories(cal);
        ni.setProtein(pro);
        ni.setCarbs(carb);
        ni.setFat(fat);
        recipe.setNutrition(ni);

        // 恢复你原有的设置方式
        recipe.setCalories((int) cal);
        recipe.setProtein(pro);
        recipe.setCarbs(carb);
        recipe.setFat(fat);

        try { recipe.setFiber(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_FIBER))); } catch (Exception e) { recipe.setFiber(0.0); }
        try { recipe.setMealType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MEAL_TYPE))); } catch (Exception e) { recipe.setMealType("未知"); }
        try { recipe.setPreparationTime(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PREP_TIME))); } catch (Exception e) {}
        try { recipe.setCookingTime(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COOK_TIME))); } catch (Exception e) {}
        try { recipe.setDifficulty(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DIFFICULTY))); } catch (Exception e) {}

        // 解析列表
        String ingStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_INGREDIENTS));
        if (ingStr != null && !ingStr.isEmpty()) {
            List<String> ings = new ArrayList<>();
            for (String s : ingStr.split("\\|")) ings.add(s);
            recipe.setIngredients(ings);
        }

        try {
            String stepStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STEPS));
            if (stepStr != null && !stepStr.isEmpty()) {
                List<String> steps = new ArrayList<>();
                for (String s : stepStr.split("\\|")) steps.add(s);
                recipe.setCookingSteps(steps);
            }
        } catch (Exception e) {}

        return recipe;
    }

    public void clearAllRecipes() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
    }
}