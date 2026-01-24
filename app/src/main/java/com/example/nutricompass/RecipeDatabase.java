package com.example.nutricompass;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import java.util.Locale;

/**
 * 严谨版食谱数据库
 */
public class RecipeDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "recipe_history.db";
    private static final int DATABASE_VERSION = 1;

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
                "nutrition_json TEXT, " +
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

    public long addRecipe(Recipe recipe) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_TITLE, recipe.getTitle());
        values.put(COLUMN_DESCRIPTION, recipe.getDescription());
        values.put(COLUMN_DATE, recipe.getDate());
        values.put(COLUMN_CALORIES, recipe.getCalories());

        List<String> ingredientsList = recipe.getIngredients();
        values.put(COLUMN_INGREDIENTS, arrayToString(ingredientsList.toArray(new String[0])));

        List<String> stepsList = recipe.getCookingSteps();
        values.put(COLUMN_STEPS, arrayToString(stepsList.toArray(new String[0])));

        values.put(COLUMN_NUTRITION_INFO, recipe.getReason());
        values.put("weather_condition", recipe.getWeatherCondition());
        values.put("user_condition", recipe.getUserCondition());
        values.put("preparation_time", recipe.getPreparationTime());
        values.put("cooking_time", recipe.getCookingTime());
        values.put("difficulty", recipe.getDifficulty());

        if (recipe.getNutrition() != null) {
            NutritionInfo nutrition = recipe.getNutrition();
            try {
                JSONObject json = new JSONObject();
                json.put("calories", nutrition.getCalories());
                json.put("protein", nutrition.getProtein());
                json.put("carbs", nutrition.getCarbs());
                json.put("fat", nutrition.getFat());
                values.put("nutrition_json", json.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        long id = db.insert(TABLE_RECIPES, null, values);
        db.close();
        return id;
    }

    public List<Recipe> getAllRecipes() {
        List<Recipe> recipeList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_RECIPES + " ORDER BY " + COLUMN_ID + " DESC";
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                Recipe recipe = new Recipe();

                // 安全获取索引的方法
                recipe.setId(safeGetInt(cursor, COLUMN_ID));
                recipe.setTitle(safeGetString(cursor, COLUMN_TITLE));
                recipe.setDescription(safeGetString(cursor, COLUMN_DESCRIPTION));
                recipe.setDate(safeGetString(cursor, COLUMN_DATE));

                int baseCal = safeGetInt(cursor, COLUMN_CALORIES);
                recipe.setCalories(baseCal);

                recipe.setIngredients(new ArrayList<>(java.util.Arrays.asList(stringToArray(safeGetString(cursor, COLUMN_INGREDIENTS)))));
                recipe.setCookingSteps(new ArrayList<>(java.util.Arrays.asList(stringToArray(safeGetString(cursor, COLUMN_STEPS)))));

                recipe.setReason(safeGetString(cursor, COLUMN_NUTRITION_INFO));
                recipe.setWeatherCondition(safeGetString(cursor, "weather_condition"));
                recipe.setUserCondition(safeGetString(cursor, "user_condition"));
                recipe.setPreparationTime(safeGetString(cursor, "preparation_time"));
                recipe.setCookingTime(safeGetString(cursor, "cooking_time"));
                recipe.setDifficulty(safeGetInt(cursor, "difficulty"));

                String nutritionJson = safeGetString(cursor, "nutrition_json");
                if (nutritionJson != null && !nutritionJson.isEmpty()) {
                    try {
                        JSONObject jsonObject = new JSONObject(nutritionJson);
                        NutritionInfo nutrition = new NutritionInfo();
                        nutrition.setCalories(jsonObject.optDouble("calories", baseCal));
                        nutrition.setProtein(jsonObject.optDouble("protein", 0));
                        nutrition.setCarbs(jsonObject.optDouble("carbs", 0));
                        nutrition.setFat(jsonObject.optDouble("fat", 0));
                        recipe.setNutrition(nutrition);
                        recipe.setCalories((int) nutrition.getCalories());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                recipeList.add(recipe);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return recipeList;
    }

    // 严谨：安全读取 String 避免 -1 索引
    private String safeGetString(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        return (index != -1) ? cursor.getString(index) : "";
    }

    // 严谨：安全读取 Int 避免 -1 索引
    private int safeGetInt(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        return (index != -1) ? cursor.getInt(index) : 0;
    }

    private String arrayToString(String[] array) {
        if (array == null || array.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (String item : array) sb.append(item).append(";");
        return sb.toString();
    }

    private String[] stringToArray(String str) {
        if (str == null || str.isEmpty()) return new String[0];
        return str.split(";");
    }

    public void deleteRecipe(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RECIPES, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void clearAllRecipes() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RECIPES, null, null);
        db.close();
    }
}