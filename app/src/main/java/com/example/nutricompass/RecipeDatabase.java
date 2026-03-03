package com.example.nutricompass;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecipeDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "recipes.db";
    private static final int DATABASE_VERSION = 3;  // 升级版本号
    private static final String TABLE_NAME = "recipes";

    // 表字段定义
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_DESCRIPTION = "description";
    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_CALORIES = "calories";
    private static final String COLUMN_INGREDIENTS = "ingredients";

    // 新增字段
    private static final String COLUMN_PROTEIN = "protein";
    private static final String COLUMN_CARBS = "carbs";
    private static final String COLUMN_FAT = "fat";
    private static final String COLUMN_FIBER = "fiber";
    private static final String COLUMN_MEAL_TYPE = "meal_type";

    public RecipeDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_TITLE + " TEXT," +
                COLUMN_DESCRIPTION + " TEXT," +
                COLUMN_DATE + " TEXT," +
                COLUMN_CALORIES + " INTEGER," +
                COLUMN_INGREDIENTS + " TEXT," +
                COLUMN_PROTEIN + " REAL DEFAULT 0," +      // 新增：蛋白质
                COLUMN_CARBS + " REAL DEFAULT 0," +        // 新增：碳水
                COLUMN_FAT + " REAL DEFAULT 0," +          // 新增：脂肪
                COLUMN_FIBER + " REAL DEFAULT 0," +        // 新增：膳食纤维
                COLUMN_MEAL_TYPE + " TEXT DEFAULT '未知')"; // 新增：餐型
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            // 版本2升级到3：添加新列
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_PROTEIN + " REAL DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_CARBS + " REAL DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_FAT + " REAL DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_FIBER + " REAL DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_MEAL_TYPE + " TEXT DEFAULT '未知'");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // 如果是更老的版本，直接删除重建
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }

    /**
     * 添加食谱记录（包含营养数据）
     */
    public long addRecipe(Recipe recipe) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_TITLE, recipe.getTitle());
        values.put(COLUMN_DESCRIPTION, recipe.getDescription());
        values.put(COLUMN_DATE, recipe.getDate());
        values.put(COLUMN_CALORIES, recipe.getCalories());

        // 将食材列表转换为字符串存储
        if (recipe.getIngredients() != null) {
            String ingredientsStr = String.join(",", recipe.getIngredients());
            values.put(COLUMN_INGREDIENTS, ingredientsStr);
        }

        // 新增营养数据
        values.put(COLUMN_PROTEIN, recipe.getProtein());
        values.put(COLUMN_CARBS, recipe.getCarbs());
        values.put(COLUMN_FAT, recipe.getFat());
        values.put(COLUMN_FIBER, recipe.getFiber());
        values.put(COLUMN_MEAL_TYPE, recipe.getMealType());

        long id = db.insert(TABLE_NAME, null, values);
        db.close();
        return id;
    }

    /**
     * 获取所有食谱记录
     */
    public List<Recipe> getAllRecipes() {
        List<Recipe> recipeList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_NAME + " ORDER BY " + COLUMN_DATE + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Recipe recipe = cursorToRecipe(cursor);
                recipeList.add(recipe);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return recipeList;
    }

    /**
     * 根据ID获取单个食谱
     */
    public Recipe getRecipe(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, COLUMN_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null);

        Recipe recipe = null;
        if (cursor != null && cursor.moveToFirst()) {
            recipe = cursorToRecipe(cursor);
            cursor.close();
        }
        db.close();
        return recipe;
    }

    /**
     * 删除单个食谱
     */
    public void deleteRecipe(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    /**
     * 清空所有食谱
     */
    public void clearAllRecipes() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_NAME);
        db.close();
    }

    /**
     * 更新食谱
     */
    public int updateRecipe(Recipe recipe) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_TITLE, recipe.getTitle());
        values.put(COLUMN_DESCRIPTION, recipe.getDescription());
        values.put(COLUMN_DATE, recipe.getDate());
        values.put(COLUMN_CALORIES, recipe.getCalories());

        if (recipe.getIngredients() != null) {
            String ingredientsStr = String.join(",", recipe.getIngredients());
            values.put(COLUMN_INGREDIENTS, ingredientsStr);
        }

        // 更新营养数据
        values.put(COLUMN_PROTEIN, recipe.getProtein());
        values.put(COLUMN_CARBS, recipe.getCarbs());
        values.put(COLUMN_FAT, recipe.getFat());
        values.put(COLUMN_FIBER, recipe.getFiber());
        values.put(COLUMN_MEAL_TYPE, recipe.getMealType());

        return db.update(TABLE_NAME, values, COLUMN_ID + "=?",
                new String[]{String.valueOf(recipe.getId())});
    }

    /**
     * ========== 新增方法：用于周报告的数据查询 ==========
     */

    /**
     * 按日期范围查询食谱
     * @param startDate 开始日期 (格式: yyyy-MM-dd)
     * @param endDate 结束日期 (格式: yyyy-MM-dd)
     * @return 该日期范围内的所有食谱
     */
    public List<Recipe> getRecipesByDateRange(String startDate, String endDate) {
        List<Recipe> result = new ArrayList<>();

        String selectQuery = "SELECT * FROM " + TABLE_NAME +
                " WHERE " + COLUMN_DATE + " BETWEEN ? AND ? " +
                " ORDER BY " + COLUMN_DATE + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, new String[]{startDate, endDate});

        if (cursor.moveToFirst()) {
            do {
                Recipe recipe = cursorToRecipe(cursor);
                result.add(recipe);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return result;
    }

    /**
     * 按日期分组获取食谱
     * @return Map<日期, 该日期的食谱列表>
     */
    public Map<String, List<Recipe>> getRecipesGroupByDate() {
        Map<String, List<Recipe>> grouped = new HashMap<>();

        String selectQuery = "SELECT * FROM " + TABLE_NAME + " ORDER BY " + COLUMN_DATE + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Recipe recipe = cursorToRecipe(cursor);
                String date = recipe.getDate();

                if (!grouped.containsKey(date)) {
                    grouped.put(date, new ArrayList<>());
                }
                grouped.get(date).add(recipe);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return grouped;
    }

    /**
     * 获取指定日期的食谱
     * @param date 日期 (格式: yyyy-MM-dd)
     * @return 该日期的所有食谱
     */
    public List<Recipe> getRecipesByDate(String date) {
        List<Recipe> result = new ArrayList<>();

        String selectQuery = "SELECT * FROM " + TABLE_NAME +
                " WHERE " + COLUMN_DATE + " = ?";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, new String[]{date});

        if (cursor.moveToFirst()) {
            do {
                Recipe recipe = cursorToRecipe(cursor);
                result.add(recipe);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return result;
    }

    /**
     * 获取最早和最晚的日期
     * @return String[] {最早日期, 最晚日期}
     */
    public String[] getDateRange() {
        String[] range = new String[2];

        String query = "SELECT MIN(" + COLUMN_DATE + "), MAX(" + COLUMN_DATE + ") FROM " + TABLE_NAME;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            range[0] = cursor.getString(0);  // 最早日期
            range[1] = cursor.getString(1);  // 最晚日期
        }

        cursor.close();
        db.close();
        return range;
    }

    /**
     * 获取指定日期范围内的营养汇总
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 营养汇总数据
     */
    public NutritionSummary getNutritionSummaryByDateRange(String startDate, String endDate) {
        NutritionSummary summary = new NutritionSummary();

        String query = "SELECT " +
                "SUM(" + COLUMN_CALORIES + ") as total_calories, " +
                "SUM(" + COLUMN_PROTEIN + ") as total_protein, " +
                "SUM(" + COLUMN_CARBS + ") as total_carbs, " +
                "SUM(" + COLUMN_FAT + ") as total_fat, " +
                "SUM(" + COLUMN_FIBER + ") as total_fiber, " +
                "COUNT(*) as total_meals, " +
                "COUNT(DISTINCT " + COLUMN_DATE + ") as total_days " +
                "FROM " + TABLE_NAME +
                " WHERE " + COLUMN_DATE + " BETWEEN ? AND ?";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, new String[]{startDate, endDate});

        if (cursor.moveToFirst()) {
            summary.totalCalories = cursor.getDouble(0);
            summary.totalProtein = cursor.getDouble(1);
            summary.totalCarbs = cursor.getDouble(2);
            summary.totalFat = cursor.getDouble(3);
            summary.totalFiber = cursor.getDouble(4);
            summary.totalMeals = cursor.getInt(5);
            summary.totalDays = cursor.getInt(6);
        }

        cursor.close();
        db.close();
        return summary;
    }

    /**
     * ========== 辅助方法 ==========
     */

    /**
     * 将Cursor转换为Recipe对象
     */
    private Recipe cursorToRecipe(Cursor cursor) {
        Recipe recipe = new Recipe();

        recipe.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)));
        recipe.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)));
        recipe.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESCRIPTION)));
        recipe.setDate(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE)));
        recipe.setCalories(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CALORIES)));

        // 处理食材列表
        String ingredientsStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_INGREDIENTS));
        if (ingredientsStr != null && !ingredientsStr.isEmpty()) {
            String[] ingredientsArray = ingredientsStr.split(",");
            recipe.setIngredients(new ArrayList<>(java.util.Arrays.asList(ingredientsArray)));
        }

        // 获取新增的营养字段（需要处理列可能存在的情况）
        try {
            recipe.setProtein(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PROTEIN)));
        } catch (Exception e) {
            recipe.setProtein(0.0);
        }

        try {
            recipe.setCarbs(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CARBS)));
        } catch (Exception e) {
            recipe.setCarbs(0.0);
        }

        try {
            recipe.setFat(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_FAT)));
        } catch (Exception e) {
            recipe.setFat(0.0);
        }

        try {
            recipe.setFiber(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_FIBER)));
        } catch (Exception e) {
            recipe.setFiber(0.0);
        }

        try {
            recipe.setMealType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MEAL_TYPE)));
        } catch (Exception e) {
            recipe.setMealType("未知");
        }

        return recipe;
    }

    /**
     * 内部类：营养汇总
     */
    public static class NutritionSummary {
        public double totalCalories;
        public double totalProtein;
        public double totalCarbs;
        public double totalFat;
        public double totalFiber;
        public int totalMeals;
        public int totalDays;

        @Override
        public String toString() {
            return String.format(Locale.CHINA,
                    "总热量:%.0f, 蛋白质:%.1f, 碳水:%.1f, 脂肪:%.1f, 餐次:%d, 天数:%d",
                    totalCalories, totalProtein, totalCarbs, totalFat, totalMeals, totalDays);
        }
    }
}