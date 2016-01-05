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
            + BASE_URL + " TEXT NOT NULL UNIQUE, "
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

    /* HINT GROUPS */

    private Long getMaxHintGroupID(long serverId) {
        String[] fields = {"max("+PASTE_HINTS_GID+")+1 as max_gid"};
        String whereClause = "_sid = ?";
        String[] whereArgs = {String.valueOf(serverId)};

        Cursor c = getReadableDatabase().query(TABLE_PASTE_HINTS, fields, whereClause, whereArgs,
                null, null, null, null);
        if (c.getCount() == 0) {
            //nothing's been created
            c.close();
            return 1L;
        }
        else {
            c.moveToFirst();
            long gid = c.getLong(c.getColumnIndex("max_gid"));
            c.close();
            return gid;
        }
    }

    public void clearHintGroups(long serverId) {
        getWritableDatabase().delete(TABLE_PASTE_HINTS, "_sid = ?",
                new String[]{String.valueOf(serverId)});
    }

    public boolean hasHighlighter(long serverId, String... hints) {
        int count = hints.length;

        String[] fields = new String[]{COLUMN_ID};
        String whereClause = String.format("_sid = ? AND name IN (%s)", makePlaceholders(count));

        String[] whereArgs = new String[count+1];
        whereArgs[0] = String.valueOf(serverId);
        System.arraycopy(hints, 0, whereArgs, 1, count);

        Cursor c = getReadableDatabase().query(TABLE_PASTE_HINTS, fields,
                whereClause, whereArgs, null, null, null, null);

        count = c.getCount();
        c.close();

        return count == hints.length;
    }

    public long getServerByURL(String serverUrl)
    {
        String[] fields = new String[]{COLUMN_ID};
        String whereClause = "_id = ?";
        String[] whereArgs = new String[]{serverUrl};
        Cursor c = getReadableDatabase().query(TABLE_SERVERS, fields, whereClause, whereArgs,
                null, null, null, null);
        if (c.getCount() == 0) {
            c.close();
            return -1;
        }
        else {
            c.moveToFirst();
            long id = c.getLong(c.getColumnIndex(COLUMN_ID));
            c.close();
            return id;
        }
    }

    public void addHintGroup(long serverId, String... aliases) {
        Long gid = getMaxHintGroupID(serverId);
        SQLiteDatabase db = getWritableDatabase();

        try
        {
            db.beginTransaction();
            ContentValues cv = new ContentValues();
            cv.put(PASTE_HINTS_GID, gid);
            cv.put(PASTE_HINTS_SID, serverId);

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

    public Cursor getHintGroups(long serverId) {
        //SELECT _gid as _id, max(name) as longest_name, (count(name) || ' aliases') as naliases FROM paste_hints GROUP BY _gid

        String maxName = "max("+PASTE_HINTS_NAME+") as longest_name";
        String naliases = "(count("+PASTE_HINTS_NAME+") || ' aliases') as naliases";

        String[] fields = {COLUMN_ID, maxName, naliases, PASTE_HINTS_SID, PASTE_HINTS_GID};

        String whereClause = "_sid = ?";
        String[] whereArgs = {String.valueOf(serverId)};

        return getReadableDatabase().query(TABLE_PASTE_HINTS, fields, whereClause, whereArgs,
                PASTE_HINTS_GID, null, null, null);
    }

    public Cursor getHintGroups(long serverId, String nameAlike) {
        String maxName = "max("+PASTE_HINTS_NAME+") as longest_name";
        String naliases = "(count("+PASTE_HINTS_NAME+") || ' aliases') as naliases";

        String[] fields = {COLUMN_ID, maxName, naliases, PASTE_HINTS_SID, PASTE_HINTS_GID};

        String whereClause = "_sid = ? AND name LIKE ?";
        String[] whereArgs = {String.valueOf(serverId), String.format("%%%s%%", nameAlike)};

        return getReadableDatabase().query(TABLE_PASTE_HINTS, fields, whereClause, whereArgs,
                PASTE_HINTS_GID, null, null, null);
    }

    public Cursor getHintGroupChildren(long serverId, long groupId, String nameAlike) {
        String[] fields = {COLUMN_ID, PASTE_HINTS_NAME, PASTE_HINTS_SID, PASTE_HINTS_GID};

        String whereClause = "_sid = ? AND _gid = ? AND name LIKE ?";
        String[] whereArgs = {String.valueOf(serverId), String.valueOf(groupId), String.format("%%%s%%", nameAlike)};

        return getReadableDatabase().query(TABLE_PASTE_HINTS, fields, whereClause, whereArgs,
                null, null, null, null);
    }

    public Cursor getHintGroupChildren(long serverId, long groupId) {
        String[] fields = {COLUMN_ID, PASTE_HINTS_NAME, PASTE_HINTS_SID, PASTE_HINTS_GID};

        String whereClause = "_sid = ? AND _gid = ?";
        String[] whereArgs = {String.valueOf(serverId), String.valueOf(groupId)};

        return getReadableDatabase().query(TABLE_PASTE_HINTS, fields, whereClause, whereArgs,
                null, null, null, null);
    }
    /* /HINT GROUPS */

    /* SERVERS */
    public void addServer(String base_url)
    {
        ContentValues cv = new ContentValues();
        cv.put(BASE_URL, base_url);
        cv.put(SERVER_DEFAULT, false);

        getWritableDatabase().insertOrThrow(TABLE_SERVERS, null, cv);
    }

    public Cursor getAllServers()
    {
        return getAllServers(true);
    }

    public Cursor getAllServers(boolean ordering) {
        String[] fields = {COLUMN_ID, BASE_URL, SERVER_DEFAULT };
        String orderBy = null;
        if (ordering) {
            orderBy = SERVER_DEFAULT+" DESC";
        }
        return getReadableDatabase().query(TABLE_SERVERS, fields, null, null,
                null, null, orderBy);
    }

    public void deleteServer(long oldID)
    {
        String whereClause = "_id = ?";
        String[] whereArgs = new String[1];
        whereArgs[0] = String.valueOf(oldID);

        getWritableDatabase().delete(TABLE_SERVERS, whereClause, whereArgs);
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
    /* /SERVERS */

    /* UPLOADS */
    public long addUpload(String baseUrl, String token, String vanity, String uuid, String sha1sum, boolean isPrivate, Long sunset, String uploadHint) {
        ContentValues cv = new ContentValues();
        cv.put(BASE_URL, baseUrl);
        cv.put(UPLOAD_TOKEN, token);
        cv.put(UPLOAD_VANITY, vanity);
        cv.put(UPLOAD_UUID, uuid);
        cv.put(UPLOAD_SHA1, sha1sum);
        cv.put(UPLOAD_PRIVATE, isPrivate);
        cv.put(UPLOAD_SUNSET, sunset);
        cv.put(UPLOAD_HINT, uploadHint);

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
                "THEN ( "+ BASE_URL +" || '/' || "+ UPLOAD_VANITY +" ) " +
                "ELSE ( "+ BASE_URL +" || '/' || "+ UPLOAD_VANITY +" || "+UPLOAD_HINT+" ) " +
                "END AS hvanity_url";
        /* String BIG_HEADER = "CASE WHEN (" + UPLOADED_FPATH + " IS NULL ) " +
                "THEN '(multiple)'  " +
                "ELSE " + UPLOADED_FPATH +
                " END AS header"; */
        String PRETTY_DATE = "'Expires ' || DATETIME(" + UPLOAD_SUNSET + ", 'unixepoch', 'localtime') AS dt";
        String[] fields = {COLUMN_ID, MAKE_URL_EXPR, MAKE_HVANITY_EXPR, PRETTY_DATE,
                BASE_URL, UPLOAD_UUID, UPLOAD_PRIVATE, UPLOAD_HINT};
        return getReadableDatabase().query(TABLE_UPLOADS, fields, null, null,
                null, null, COLUMN_ID + " DESC", null);
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

    public void replaceEntry(long id, String token, String sha1sum, String detectedHint) {
        String whereClause = COLUMN_ID+" = ?";
        String[] whereArgs = new String[]{String.valueOf(id)};

        ContentValues cv = new ContentValues();
        cv.put(UPLOAD_TOKEN, token);
        cv.put(UPLOAD_SHA1, sha1sum);
        cv.put(UPLOAD_HINT, detectedHint);

        getWritableDatabase().update(TABLE_UPLOADS, cv, whereClause,  whereArgs);
    }

    public void updateHint(long id, String hint) {
        String whereClause = COLUMN_ID+" = ?";
        String[] whereArgs = new String[]{String.valueOf(id)};

        ContentValues cv = new ContentValues();
        cv.put(UPLOAD_HINT, hint);

        getWritableDatabase().update(TABLE_UPLOADS, cv, whereClause, whereArgs);
    }
    /* /UPLOADS */

    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        // nothing
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
