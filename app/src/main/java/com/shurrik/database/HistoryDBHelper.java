package com.shurrik.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class HistoryDBHelper extends SQLiteOpenHelper {
    private static final int DB_VERSION = 1;
    private static final String DB_NAME = "MockGPS.db";
    public static final String TABLE_NAME = "HistoryLocation";
    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (Location VARCHAR(255), BD09Longitude DOUBLE NOT NULL, BD09Latitude DOUBLE NOT NULL, TimeStamp INTEGER NOT NULL, PRIMARY KEY ('BD09Longitude','BD09Latitude'))";
//    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (ID INTEGER PRIMARY KEY AUTOINCREMENT, Location VARCHAR(255), BD09Longitude DOUBLE NOT NULL, BD09Latitude DOUBLE NOT NULL, TimeStamp BIGINT NOT NULL)";

    public HistoryDBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        String sql = "DROP TABLE IF EXISTS " + TABLE_NAME;
        sqLiteDatabase.execSQL(sql);
        onCreate(sqLiteDatabase);
    }
}