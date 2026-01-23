package com.example.nutricompass;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 简易的食谱数据库
 * 注意：实际项目中建议使用 Room 数据库
 */
public class RecipeDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "recipe_history.db";
    private static final int DATABASE_VERSION = 1;

    // 表名和列名
    public static final String TABLE_RECIPES = "recipes";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_DESCRIPTION = "description";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_CALORIES = "calories";
    public static final String COLUMN_INGREDIENTS = "ingredients";
    public static final String COLUMN_STEPS = "steps";
    public static final String COLUMN_NUTRITION_INFO = "nutrition_info";

    public RecipeDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_RECIPES + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_TITLE + " TEXT, " +
                COLUMN_DESCRIPTION + " TEXT, " +
                COLUMN_DATE + " TEXT, " +
                COLUMN_CALORIES + " INTEGER, " +
                COLUMN_INGREDIENTS + " TEXT, " +
                COLUMN_STEPS + " TEXT, " +
                COLUMN_NUTRITION_INFO + " TEXT, " +
                "weather_condition TEXT, " +
                "user_condition TEXT, " +
                "preparation_time TEXT, " +
                "cooking_time TEXT, " +
                "difficulty INTEGER)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECIPES);
        onCreate(db);
    }

    /**
     * 添加食谱到数据库
     */
    public long addRecipe(Recipe recipe) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_TITLE, recipe.getTitle());
        values.put(COLUMN_DESCRIPTION, recipe.getDescription());
        values.put(COLUMN_DATE, recipe.getDate());
        values.put(COLUMN_CALORIES, recipe.getCalories());

        // 将 List<String> 转换为字符串
        List<String> ingredientsList = recipe.getIngredients();
        String[] ingredientsArray = ingredientsList.toArray(new String[0]);
        values.put(COLUMN_INGREDIENTS, arrayToString(ingredientsArray));

        List<String> stepsList = recipe.getCookingSteps();
        String[] stepsArray = stepsList.toArray(new String[0]);
        values.put(COLUMN_STEPS, arrayToString(stepsArray));

        values.put(COLUMN_NUTRITION_INFO, recipe.getReason());
        values.put("weather_condition", recipe.getWeatherCondition());
        values.put("user_condition", recipe.getUserCondition());
        values.put("preparation_time", recipe.getPreparationTime());
        values.put("cooking_time", recipe.getCookingTime());
        values.put("difficulty", recipe.getDifficulty());

        long id = db.insert(TABLE_RECIPES, null, values);
        db.close();
        return id;
    }

    /**
     * 获取所有食谱
     */
    public List<Recipe> getAllRecipes() {
        List<Recipe> recipeList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_RECIPES + " ORDER BY " + COLUMN_ID + " DESC";
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                Recipe recipe = new Recipe();
                recipe.setId(cursor.getInt(0));
                recipe.setTitle(cursor.getString(1));
                recipe.setDescription(cursor.getString(2));
                recipe.setDate(cursor.getString(3));
                recipe.setCalories(cursor.getInt(4));

                // 转换字符串数组为 List
                String[] ingredientsArray = stringToArray(cursor.getString(5));
                List<String> ingredientsList = new ArrayList<>();
                for (String ingredient : ingredientsArray) {
                    ingredientsList.add(ingredient);
                }
                recipe.setIngredients(ingredientsList);

                // 转换字符串数组为 List
                String[] stepsArray = stringToArray(cursor.getString(6));
                List<String> stepsList = new ArrayList<>();
                for (String step : stepsArray) {
                    stepsList.add(step);
                }
                recipe.setCookingSteps(stepsList);

                // 设置推荐理由
                recipe.setReason(cursor.getString(7));
                recipe.setWeatherCondition(cursor.getString(8));
                recipe.setUserCondition(cursor.getString(9));
                recipe.setPreparationTime(cursor.getString(10));
                recipe.setCookingTime(cursor.getString(11));
                recipe.setDifficulty(cursor.getInt(12));

                recipeList.add(recipe);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return recipeList;
    }

    /**
     * 删除特定食谱
     */
    public void deleteRecipe(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RECIPES, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    /**
     * 清空所有食谱
     */
    public void clearAllRecipes() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RECIPES, null, null);
        db.close();
    }

    /**
     * 将数组转换为字符串（以分号分隔）
     */
    private String arrayToString(String[] array) {
        if (array == null || array.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String item : array) {
            sb.append(item).append(";");
        }
        return sb.toString();
    }

    /**
     * 将字符串转换为数组
     */
    private String[] stringToArray(String str) {
        if (str == null || str.isEmpty()) {
            return new String[0];
        }
        return str.split(";");
    }
}