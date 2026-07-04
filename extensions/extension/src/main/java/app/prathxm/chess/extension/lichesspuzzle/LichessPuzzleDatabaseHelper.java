/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.lichesspuzzle;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class LichessPuzzleDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "lichess_puzzles.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_PUZZLES = "puzzles";

    public LichessPuzzleDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PUZZLES + " (" +
                "id TEXT PRIMARY KEY, " +
                "fen TEXT, " +
                "moves TEXT, " +
                "rating INTEGER, " +
                "theme TEXT, " +
                "solved INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_solved ON " + TABLE_PUZZLES + "(solved)");
        db.execSQL("CREATE INDEX idx_theme ON " + TABLE_PUZZLES + "(theme)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PUZZLES);
        onCreate(db);
    }

    public void insertPuzzles(List<JSONObject> puzzles) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            for (JSONObject p : puzzles) {
                cv.put("id", p.getString("id"));
                cv.put("fen", p.getString("fen"));
                cv.put("moves", p.getString("moves"));
                cv.put("rating", p.getInt("rating"));
                cv.put("theme", p.optString("theme", "tactics"));
                db.insertWithOnConflict(TABLE_PUZZLES, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e("LichessDb", "Failed to insert puzzles", e);
        } finally {
            db.endTransaction();
        }
    }

    public int getPuzzlesCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PUZZLES, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    public int getUnsolvedCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PUZZLES + " WHERE solved = 0", null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    public void markAsSolved(String puzzleId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("solved", 1);
        db.update(TABLE_PUZZLES, cv, "id = ?", new String[]{puzzleId});
    }

    public JSONObject getSpecificPuzzleByOffset(int offset) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_PUZZLES + " LIMIT 1 OFFSET ?", new String[]{String.valueOf(offset)});
        JSONObject puzzle = null;
        if (cursor.moveToFirst()) {
            puzzle = cursorToJSON(cursor);
        }
        cursor.close();
        return puzzle;
    }

    public int getOffsetOfPuzzleId(String id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT rowid FROM " + TABLE_PUZZLES + " WHERE id = ?", new String[]{id});
        int offset = -1;
        if (cursor.moveToFirst()) {
            offset = cursor.getInt(0) - 1;
        }
        cursor.close();
        return offset;
    }

    public JSONObject getRandomUnsolvedPuzzle(String theme) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor;
        if (theme == null || theme.equalsIgnoreCase("healthyMix")) {
            cursor = db.rawQuery("SELECT * FROM " + TABLE_PUZZLES + " WHERE solved = 0 ORDER BY RANDOM() LIMIT 1", null);
        } else {
            // Try exact match first
            cursor = db.rawQuery("SELECT * FROM " + TABLE_PUZZLES + " WHERE solved = 0 AND theme = ? ORDER BY RANDOM() LIMIT 1", new String[]{theme});
            if (cursor.getCount() == 0) {
                cursor.close();
                // Try substring/contains match
                cursor = db.rawQuery("SELECT * FROM " + TABLE_PUZZLES + " WHERE solved = 0 AND theme LIKE ? ORDER BY RANDOM() LIMIT 1", new String[]{"%" + theme + "%"});
            }
            if (cursor.getCount() == 0) {
                cursor.close();
                // Fallback to any theme
                cursor = db.rawQuery("SELECT * FROM " + TABLE_PUZZLES + " WHERE solved = 0 ORDER BY RANDOM() LIMIT 1", null);
            }
        }
        
        JSONObject puzzle = null;
        if (cursor.moveToFirst()) {
            puzzle = cursorToJSON(cursor);
        }
        cursor.close();
        return puzzle;
    }

    public JSONObject getRushPuzzle(int targetRating) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_PUZZLES + " WHERE rating >= ? ORDER BY rating ASC LIMIT 15", new String[]{String.valueOf(targetRating)});
        List<JSONObject> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            JSONObject obj = cursorToJSON(cursor);
            if (obj != null) {
                list.add(obj);
            }
        }
        cursor.close();
        if (list.isEmpty()) {
            cursor = db.rawQuery("SELECT * FROM " + TABLE_PUZZLES + " ORDER BY rating DESC LIMIT 1", null);
            if (cursor.moveToFirst()) {
                JSONObject obj = cursorToJSON(cursor);
                cursor.close();
                return obj;
            }
            cursor.close();
            return null;
        }
        int randIdx = (int) (Math.random() * list.size());
        return list.get(randIdx);
    }

    public int getThemeCount(String theme) {
        try {
            SQLiteDatabase db = getReadableDatabase();
            if (theme == null || theme.equalsIgnoreCase("healthyMix")) {
                Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PUZZLES, null);
                int count = 0;
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0);
                }
                cursor.close();
                return count;
            } else {
                Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PUZZLES + " WHERE theme = ?", new String[]{theme});
                int count = 0;
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0);
                }
                cursor.close();
                if (count == 0) {
                    cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PUZZLES + " WHERE theme LIKE ?", new String[]{"%" + theme + "%"});
                    if (cursor.moveToFirst()) {
                        count = cursor.getInt(0);
                    }
                    cursor.close();
                }
                return count;
            }
        } catch (Exception e) {
            Log.e("LichessDb", "Failed to get theme count", e);
            return 0;
        }
    }

    public void clearAllPuzzles() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("DELETE FROM " + TABLE_PUZZLES);
        } catch (Exception e) {
            Log.e("LichessDb", "Failed to clear all puzzles", e);
        }
    }

    private JSONObject cursorToJSON(Cursor cursor) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")));
            obj.put("fen", cursor.getString(cursor.getColumnIndexOrThrow("fen")));
            obj.put("moves", cursor.getString(cursor.getColumnIndexOrThrow("moves")));
            obj.put("rating", cursor.getInt(cursor.getColumnIndexOrThrow("rating")));
            obj.put("theme", cursor.getString(cursor.getColumnIndexOrThrow("theme")));
            return obj;
        } catch (Exception e) {
            return null;
        }
    }
}
