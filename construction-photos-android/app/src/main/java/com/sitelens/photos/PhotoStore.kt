package com.sitelens.photos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PhotoStore(context: Context) : SQLiteOpenHelper(context, "photos.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE photos (key TEXT PRIMARY KEY, hash TEXT NOT NULL, suggested INTEGER NOT NULL, reason TEXT NOT NULL, choice INTEGER)")
        db.execSQL("CREATE TABLE uploads (scope TEXT NOT NULL, hash TEXT NOT NULL, remoteId TEXT NOT NULL, confirmed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(scope, hash))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 hashes may describe Android's GPS-redacted bytes. Keep manual choices,
        // but force a fresh hash of the original before using upload history.
        if (oldVersion < 2) db.execSQL("UPDATE photos SET hash=''")
    }
    fun cached(key: String): Photo? = readableDatabase.rawQuery("SELECT * FROM photos WHERE key=?", arrayOf(key)).use {
        if (!it.moveToFirst()) null else Photo(key, "", "", "", it.getString(1), it.getInt(2) == 1, it.getString(3), if (it.isNull(4)) null else it.getInt(4) == 1)
    }
    fun save(photo: Photo) {
        writableDatabase.insertWithOnConflict("photos", null, ContentValues().apply {
            put("key", photo.key); put("hash", photo.hash); put("suggested", if (photo.suggested) 1 else 0); put("reason", photo.reason)
            if (photo.override == null) putNull("choice") else put("choice", if (photo.override) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun receipt(scope: String, hash: String): Pair<String, Boolean>? = readableDatabase.rawQuery("SELECT remoteId,confirmed FROM uploads WHERE scope=? AND hash=?", arrayOf(scope, hash)).use {
        if (it.moveToFirst()) it.getString(0) to (it.getInt(1) == 1) else null
    }
    fun record(scope: String, hash: String, id: String, confirmed: Boolean) {
        writableDatabase.insertWithOnConflict("uploads", null, ContentValues().apply {
            put("scope", scope); put("hash", hash); put("remoteId", id); put("confirmed", if (confirmed) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun forget(scope: String, hash: String) {
        writableDatabase.delete("uploads", "scope=? AND hash=?", arrayOf(scope, hash))
    }
}
