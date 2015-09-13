package io.github.phora.androptpb;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.List;

/**
 * Created by phora on 8/19/15.
 */
public class DBHelper extends SQLiteOpenHelper {

    private static DBHelper sInstance;
    private final static String DATABASE_NAME = "uploads.db";
    private final static int DATABASE_VERSION = 1;

    public final static String TABLE_UPLOADS = "uploads";
    public final static String COLUMN_ID = "_id";
    public final static String BASE_URL = "base_url";

    /* UPLOAD TABLE EXCLUSIVE FIELDS */
    public final static String UPLOAD_TOKEN  = "token";
    public final static String UPLOAD_VANITY = "vanity";
    //either short or long
    public final static String UPLOAD_UUID  = "uuid";
    public final static String UPLOAD_SHA1  = "sha1sum";
    public final static String UPLOAD_HINT = "preferred_hint";
    //eg: https://ptpb.pw/Ejaw.jpg OR https://ptpb.pw/Ejaw/java
    public final static String UPLOAD_SUNSET = "sunset";
    public final static String UPLOAD_PRIVATE = "is_private";

    /* SERVER TABLE EXCLUSIVE FIELDS */
    public final static String TABLE_SERVERS = "servers";
    //also has _id and base_url fields
    public final static String SERVER_DEFAULT = "is_default";

    public final static String TABLE_PASTE_HINTS = "paste_hints";
    public final static String PASTE_HINTS_GID = "_gid";
    public final static String PASTE_HINTS_SID = "_sid";
    public final static String PASTE_HINTS_NAME = "name";

    private final static String UPLOADS_CREATE = "CREATE TABLE " + TABLE_UPLOADS +
        " ( " + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + BASE_URL + " TEXT NOT NULL, "
        + UPLOAD_TOKEN + " TEXT NOT NULL, "
        + UPLOAD_VANITY + " TEXT, "
        + UPLOAD_UUID + " TEXT, "
        + UPLOAD_SHA1 + " TEXT, "
        + UPLOAD_HINT + " TEXT, "
        + UPLOAD_SUNSET + " INT, "
        + UPLOAD_PRIVATE + " INT)";

    private final static String SERVERS_CREATE = "CREATE TABLE " + TABLE_SERVERS +
            " ( " + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + BASE_URL + " TEXT NOT NULL, "
            + SERVER_DEFAULT + " INT)";

    private final static String PASTE_HINTS_CREATE = "CREATE TABLE " + TABLE_PASTE_HINTS +
            " ( " + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + PASTE_HINTS_SID + " INTEGER NOT NULL, "
            + PASTE_HINTS_GID + " INTEGER NOT NULL, "
            + PASTE_HINTS_NAME + " STRING NOT NULL)";

    public static synchronized DBHelper getInstance(Context context) {

        // Use the application context, which will ensure that you
        // don't accidentally leak an Activity's context.
        // See this article for more information: http://bit.ly/6LRzfx
        if (sInstance == null) {
            sInstance = new DBHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    private DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase database) {
        database.execSQL(UPLOADS_CREATE);
        database.execSQL(SERVERS_CREATE);
        database.execSQL(PASTE_HINTS_CREATE);

        ContentValues cv = new ContentValues();
        cv.put(BASE_URL, "https://ptpb.pw");
        cv.put(SERVER_DEFAULT, true);

        database.insert(TABLE_SERVERS, null, cv);
    }

    private Long getMaxHintGroupID() {
        String[] fields = {"max("+PASTE_HINTS_GID+")+1 as max_gid"};
        Cursor c = getReadableDatabase().query(TABLE_PASTE_HINTS, fields, null, null,
                null, null, null, null);
        if (c.getCount() == 0) {
            //nothing's been created
            return 1L;
        }
        else {
            c.moveToFirst();
            return c.getLong(c.getColumnIndex("max_gid"));
        }
    }

    public void addHintGroup(long server_id, String... aliases) {
        Long gid = getMaxHintGroupID();
        SQLiteDatabase db = getWritableDatabase();

        try
        {
            db.beginTransaction();
            ContentValues cv = new ContentValues();
            cv.put(PASTE_HINTS_GID, gid);
            cv.put(PASTE_HINTS_SID, server_id);

            for (String alias: aliases)
            {
                cv.remove(PASTE_HINTS_NAME);
                cv.put(PASTE_HINTS_NAME, alias);
                db.insert(TABLE_PASTE_HINTS, null, cv);
            }

            db.setTransactionSuccessful();
        }
        catch (SQLException e) {
            Log.d("DBHelper", "Unable to add hint group: "+e.getMessage());
        }
        finally
        {
            db.endTransaction();
        }
    }

    public Cursor getHintGroups(long server_id) {
        String[] fields = {PASTE_HINTS_NAME, PASTE_HINTS_GID};

        String whereClause = "_sid = ?";
        String[] whereArgs = {String.valueOf(server_id)};

        return getReadableDatabase().query(TABLE_PASTE_HINTS, fields, whereClause, whereArgs,
                PASTE_HINTS_GID, null, null, null);
    }

    public void addServer(String base_url)
    {
        ContentValues cv = new ContentValues();
        cv.put(BASE_URL, base_url);
        cv.put(SERVER_DEFAULT, false);

        getWritableDatabase().insert(TABLE_SERVERS, null, cv);
    }

    public long addUpload(String base_url, String token, String vanity, String uuid, String sha1sum, boolean is_private, Long sunset) {
        ContentValues cv = new ContentValues();
        cv.put(BASE_URL, base_url);
        cv.put(UPLOAD_TOKEN, token);
        cv.put(UPLOAD_VANITY, vanity);
        cv.put(UPLOAD_UUID, uuid);
        cv.put(UPLOAD_SHA1, sha1sum);
        cv.put(UPLOAD_PRIVATE, is_private);
        cv.put(UPLOAD_SUNSET, sunset);

        SQLiteDatabase database = getWritableDatabase();
        return database.insert(TABLE_UPLOADS, null, cv);
    }

    public void trimHistory() {
        String whereClause = "strftime('%s', 'now') >= "+UPLOAD_SUNSET;
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_UPLOADS, whereClause, null);
    }

    public Cursor getAllUploads() {
        String MAKE_URL_EXPR = "CASE WHEN (" + UPLOAD_HINT + " IS NULL ) " +
                "THEN ( "+ BASE_URL + " || '/' || " + UPLOAD_TOKEN + " ) " +
                "ELSE ( "+ BASE_URL + " || '/' || " + UPLOAD_TOKEN + " || " + UPLOAD_HINT +" ) " +
                "END AS complete_url";
        String MAKE_HVANITY_EXPR = "CASE WHEN ("+ UPLOAD_HINT +" IS NULL) " +
                "THEN ( "+ BASE_URL +" || '/~' || "+ UPLOAD_VANITY +" ) " +
                "ELSE ( "+ BASE_URL +" || '/~' || "+ UPLOAD_VANITY +" || "+UPLOAD_HINT+" ) " +
                "END AS hvanity_url";
        /* String BIG_HEADER = "CASE WHEN (" + UPLOADED_FPATH + " IS NULL ) " +
                "THEN '(multiple)'  " +
                "ELSE " + UPLOADED_FPATH +
                " END AS header"; */
        String PRETTY_DATE = "'Expires ' || DATETIME(" + UPLOAD_SUNSET + ", 'unixepoch', 'localtime') AS dt";
        String[] fields = {COLUMN_ID, MAKE_URL_EXPR, MAKE_HVANITY_EXPR, PRETTY_DATE,
                BASE_URL, UPLOAD_UUID, UPLOAD_PRIVATE};
        return getReadableDatabase().query(TABLE_UPLOADS, fields, null, null,
                null, null, COLUMN_ID+" DESC", null);
    }

    public void setDefaultServer(long newID, long oldID)
    {
        if (newID == oldID) {
            return;
        }

        String whereClause = "_id = ?";
        ContentValues cv = new ContentValues();
        String[] whereArgs = new String[1];

        whereArgs[0] = String.valueOf(newID);
        cv.put(SERVER_DEFAULT, true);
        getWritableDatabase().update(TABLE_SERVERS, cv, whereClause, whereArgs);

        if (oldID != -1) {
            cv.clear();
            cv.put(SERVER_DEFAULT, false);
            whereArgs[0] = String.valueOf(oldID);
            getWritableDatabase().update(TABLE_SERVERS, cv, whereClause, whereArgs);
        }
    }

    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        //nothing for now
    }

    public Cursor getAllServers()
    {
        return getAllServers(true);
    }

    public Cursor getAllServers(boolean ordering) {
        String[] fields = {COLUMN_ID, BASE_URL, SERVER_DEFAULT };
        String order_by = null;
        if (ordering) {
            order_by = SERVER_DEFAULT+" DESC";
        }
        return getReadableDatabase().query(TABLE_SERVERS, fields, null, null,
                null, null, order_by);
    }

    public void deleteServer(long oldID)
    {
        String whereClause = "_id = ?";
        String[] whereArgs = new String[1];
        whereArgs[0] = String.valueOf(oldID);

        getWritableDatabase().delete(TABLE_SERVERS, whereClause, whereArgs);
    }

    public void deleteUploads(List<Long> ids) {
        int count = ids.size();
        if (count == 0)
            return;
        String whereClause = String.format("_id in (%s)", makePlaceholders(count));
        String[] whereArgs = new String[count];
        for (int i = 0; i<count; i++) {
            whereArgs[i] = ids.get(i).toString();
        }

        getWritableDatabase().delete(TABLE_UPLOADS, whereClause, whereArgs);
    }

    public static String makePlaceholders(int len) {
        if (len < 1) {
            // It will lead to an invalid query anyway ..
            throw new RuntimeException("No placeholders");
        } else {
            StringBuilder sb = new StringBuilder(len * 2 - 1);
            sb.append("?");
            for (int i = 1; i < len; i++) {
                sb.append(",?");
            }
            return sb.toString();
        }
    }
}
