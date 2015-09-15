package io.github.phora.androptpb.activities;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.MenuInflater;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.github.phora.androptpb.DBHelper;
import io.github.phora.androptpb.UriOrRaw;
import io.github.phora.androptpb.adapters.UploadsCursorAdapter;
import io.github.phora.androptpb.network.NetworkUtils;
import io.github.phora.androptpb.R;
import io.github.phora.androptpb.network.RequestData;
import io.github.phora.androptpb.network.UUIDLocalIDPair;
import io.github.phora.androptpb.network.UploadData;

public class MainActivity extends ListActivity {

    private static final String[] UPLOAD_TYPES = new String[]{"URL", "Raw Text", "File"};
    private Spinner server_spinner;

    static final int PICKING_FOR_UPLOAD = 1;
    static final int OPTIONS_SET_SINGLE = 2;
    static final int OPTIONS_SET_SINGLE_RAW_TEXT = 3;
    static final int OPTIONS_SET_MULTIPLE = 4;
    static final int OPTIONS_REPLACE_SINGLE = 5;

    private DBHelper sqlhelper;
    private Context context;
    private boolean actionModeEnabled;
    private UUIDLocalIDPair editingIdentifier = null;

    private class MsgOrInt {
        public String msg;
        public int progress;

        public MsgOrInt(String msg, int progress) {
            this.msg = msg;
            this.progress = progress;
        }
    }

    private class DeleteFilesTask extends AsyncTask<UUIDLocalIDPair, MsgOrInt, List<Long>> {
        ProgressDialog pd;

        @Override
        protected List<Long> doInBackground(UUIDLocalIDPair... uuidLocalIDPairs) {
            LinkedList<Long> output = new LinkedList<>();
            int count = uuidLocalIDPairs.length;
            if (count < 1) return null;

            pd.setMax(count + 1);

            NetworkUtils nm = NetworkUtils.getInstance(getApplicationContext());

            for (int i=0;i<uuidLocalIDPairs.length;i++) {
                UUIDLocalIDPair item = uuidLocalIDPairs[i];
                String server_url = item.getServer();
                String uuid = item.getUUID();
                String delete_url = String.format("%1$s/%2$s", server_url, uuid);
                HttpURLConnection connection = nm.openConnection(delete_url,
                        NetworkUtils.METHOD_DELETE);

                Log.d("DeleteFilesTask", connection.getRequestMethod());

                String filemsg = String.format("Removing %s/%s files", i+1, count);
                this.publishProgress(new MsgOrInt(filemsg, pd.getProgress()));

                NetworkUtils.DeleteResult delResult = nm.getDeleteResult(connection);
                if (delResult == NetworkUtils.DeleteResult.SUCCESS) {
                    // some message about how we're deleting local entry and remote copy
                    output.add(item.getLocalId());
                }
                else if (delResult == NetworkUtils.DeleteResult.ALREADY_GONE) {
                    // some message about how we're just deleting local entry
                    output.add(item.getLocalId());
                }
                else {
                    // some message about how we couldn't delete the file
                }
            }

            return output;
        }

        @Override
        protected void onProgressUpdate(MsgOrInt... values) {
            if (values[0].msg != null) {
                pd.setMessage(values[0].msg);
            }
            pd.setProgress(values[0].progress);
        }

        @Override
        protected void onPreExecute() {
            pd = new ProgressDialog(context);
            pd.setTitle("Submitting urls...");
            pd.setCancelable(false);
            pd.setIndeterminate(false);
            pd.show();
        }

        @Override
        protected void onPostExecute(List<Long> ids) {
            pd.dismiss();
            Log.d("MainActivity", "Got delete history "+(ids != null)+", updating GUI");
            if (ids != null) {
                deleteUploads(ids);
            }
        }
    }

    private class ShortenURLTask extends AsyncTask<String, MsgOrInt, List<UploadData>>
    {
        private String server_url;
        private ProgressDialog pd;

        public ShortenURLTask(String server_url) {
            this.server_url = server_url;
        }

        @Override
        protected List<UploadData> doInBackground(String... items) {
            int count = items.length;
            if (count < 1) return null;

            NetworkUtils nm = NetworkUtils.getInstance(getApplicationContext());

            pd.setMax(items.length + 1);

            LinkedList<UploadData> output = new LinkedList<UploadData>();

            for (int i=0;i<items.length;i++) {
                HttpURLConnection connection = nm.openConnection(this.server_url+"/u",
                        NetworkUtils.METHOD_POST);
                DataOutputStream dos;
                RequestData rd;

                Log.d("RedirectTask", connection.getRequestMethod());

                try {
                    dos = new DataOutputStream(connection.getOutputStream());
                    this.publishProgress(new MsgOrInt("Established connection", pd.getProgress()+1));
                    rd = new RequestData(dos, getContentResolver());
                    dos.writeBytes(RequestData.hyphens + RequestData.boundary + RequestData.crlf);
                }
                catch (IOException e) {
                    this.publishProgress(new MsgOrInt("Unable to establish connection", 0));
                    return null;
                }

                String filemsg = String.format("Putting %s/%s URLs for redirection", i+1, items.length);
                this.publishProgress(new MsgOrInt(filemsg, pd.getProgress()));
                try {
                    rd.addRawData(items[i].getBytes(), null, RequestData.NO_MIME_TYPE);
                    this.publishProgress(new MsgOrInt(null, pd.getProgress()));
                }
                catch (IOException e) {
                    this.publishProgress(new MsgOrInt("Cannot submit "+items[i], pd.getProgress()));
                    return null;
                }
                try {
                    output.add(nm.getRedirectResult(this.server_url, connection));
                }
                catch (IOException e) {
                    this.publishProgress(new MsgOrInt("Could not read redirect for "+items[i], pd.getProgress()));
                    return null;
                }
            }

            return output;
        }

        @Override
        protected void onProgressUpdate(MsgOrInt... values) {
            if (values[0].msg != null) {
                pd.setMessage(values[0].msg);
            }
            pd.setProgress(values[0].progress);
        }

        @Override
        protected void onPreExecute() {
            pd = new ProgressDialog(context);
            pd.setTitle("Submitting urls...");
            pd.setCancelable(false);
            pd.setIndeterminate(false);
            pd.show();
        }

        @Override
        protected void onPostExecute(List<UploadData> uploadDatas) {
            pd.dismiss();
            Log.d("MainActivity", "Got upload history "+(uploadDatas != null)+", updating GUI");
            if (uploadDatas != null) {
                addUploads(uploadDatas);
            }
        }
    }

    private class UploadFilesTask extends AsyncTask<UriOrRaw, MsgOrInt, List<UploadData>>
    {
        private String server_url;
        private String vanity;
        private boolean is_private;
        private Long sunset;
        private ProgressDialog pd;

        public UploadFilesTask(String server_url, boolean is_private, Long sunset) {
            this.server_url = server_url;
            this.is_private = is_private;
            this.sunset = sunset;
        }

        public UploadFilesTask(String server_url, boolean is_private, String vanity, Long sunset) {
            this.vanity = vanity;
            this.is_private = is_private;
            this.server_url = server_url;
            this.sunset = sunset;
        }

        @Override
        protected List<UploadData> doInBackground(UriOrRaw... items) {
            int count = items.length;
            if (count < 1) return null;

            NetworkUtils nm = NetworkUtils.getInstance(getApplicationContext());

            pd.setMax(items.length + 1);

            LinkedList<UploadData> output = new LinkedList<UploadData>();

            for (int i=0;i<items.length;i++) {
                HttpURLConnection connection;
                if (items.length == 1 && vanity != null) {
                   String vanurl = "%1$s/~%2$s";
                   connection = nm.openConnection(String.format(vanurl, this.server_url,
                           this.vanity), NetworkUtils.METHOD_POST);
                }
                else {
                   connection = nm.openConnection(this.server_url, NetworkUtils.METHOD_POST);
                }
                DataOutputStream dos;
                RequestData rd;

                try {
                    dos = new DataOutputStream(connection.getOutputStream());
                    this.publishProgress(new MsgOrInt("Established connection", pd.getProgress()+1));
                    rd = new RequestData(dos, getContentResolver());
                    dos.writeBytes(RequestData.hyphens + RequestData.boundary + RequestData.crlf);
                }
                catch (IOException e) {
                    this.publishProgress(new MsgOrInt("Unable to establish connection", 0));
                    return null;
                }

                String filemsg = String.format("Putting %s/%s files", i+1, items.length);
                this.publishProgress(new MsgOrInt(filemsg, pd.getProgress()));
                try {
                    if (this.is_private) {
                        rd.addPrivacy();
                    }
                    if (this.sunset != null) {
                        rd.addSunset(sunset);
                    }
                    if (items[i].hasRaw()) {
                        rd.addRawData(items[i].getRawData());
                    }
                    else if (items[i].hasUri()) {
                        rd.addFile(items[i].getUri());
                    }
                    this.publishProgress(new MsgOrInt(null, pd.getProgress()));
                }
                catch (IOException e) {
                    this.publishProgress(new MsgOrInt("Cannot submit "+items[i], pd.getProgress()));
                    return null;
                }
                try {
                    output.add(nm.getUploadResult(this.server_url, is_private, connection));
                }
                catch (IOException e) {
                    this.publishProgress(new MsgOrInt("Could not read upload data for "+items[i], pd.getProgress()));
                    return null;
                }
            }

            return output;
        }

        @Override
        protected void onProgressUpdate(MsgOrInt... values) {
            if (values[0].msg != null) {
                pd.setMessage(values[0].msg);
            }
            pd.setProgress(values[0].progress);
        }

        @Override
        protected void onPreExecute() {
            pd = new ProgressDialog(context);
            pd.setTitle("Uploading files...");
            pd.setCancelable(false);
            pd.setIndeterminate(false);
            pd.show();
        }

        @Override
        protected void onPostExecute(List<UploadData> uploadDatas) {
            pd.dismiss();
            Log.d("MainActivity", "Got upload history "+(uploadDatas != null)+", updating GUI");
            if (uploadDatas != null) {
                addUploads(uploadDatas);
            }
        }
    }

    private class ReplaceFilesTask extends AsyncTask<UUIDLocalIDPair, MsgOrInt, Map<Long, UploadData>> {
        ProgressDialog pd;

        @Override
        protected Map<Long, UploadData> doInBackground(UUIDLocalIDPair... uuidLocalIDPairs) {
            int count = uuidLocalIDPairs.length;
            if (count < 1) return null;

            NetworkUtils nm = NetworkUtils.getInstance(getApplicationContext());

            pd.setMax(uuidLocalIDPairs.length + 1);

            Map<Long, UploadData> output = new HashMap<>();

            for (int i=0;i<uuidLocalIDPairs.length;i++) {
                UUIDLocalIDPair item = uuidLocalIDPairs[i];
                String server_url = item.getServer();
                String uuid = item.getUUID();
                String update_url = String.format("%1$s/%2$s", server_url, uuid);

                HttpURLConnection connection;
                connection = nm.openConnection(update_url, NetworkUtils.METHOD_PUT);
                DataOutputStream dos;
                RequestData rd;

                try {
                    dos = new DataOutputStream(connection.getOutputStream());
                    this.publishProgress(new MsgOrInt("Established connection", pd.getProgress()+1));
                    rd = new RequestData(dos, getContentResolver());
                    dos.writeBytes(RequestData.hyphens + RequestData.boundary + RequestData.crlf);
                }
                catch (IOException e) {
                    this.publishProgress(new MsgOrInt("Unable to establish connection", 0));
                    return null;
                }

                String filemsg = String.format("Replacing %s/%s files", i+1, count);
                this.publishProgress(new MsgOrInt(filemsg, pd.getProgress()));
                try {
                    if (item.getOptData().hasRaw()) {
                        rd.addRawData(item.getOptData().getRawData());
                    }
                    else if (item.getOptData().hasUri()) {
                        rd.addFile(item.getOptData().getUri());
                    }
                    this.publishProgress(new MsgOrInt(null, pd.getProgress()));
                }
                catch (IOException e) {
                    this.publishProgress(new MsgOrInt("Cannot submit "+item, pd.getProgress()));
                    return null;
                }
                try {
                    output.put(item.getLocalId(), nm.getReplaceResult(connection, server_url, item.getOptPrivate()));
                }
                catch (IOException e) {
                    this.publishProgress(new MsgOrInt("Could not read upload data for "+item, pd.getProgress()));
                    return null;
                }
            }

            return output;
        }

        @Override
        protected void onProgressUpdate(MsgOrInt... values) {
            if (values[0].msg != null) {
                pd.setMessage(values[0].msg);
            }
            pd.setProgress(values[0].progress);
        }

        @Override
        protected void onPreExecute() {
            pd = new ProgressDialog(context);
            pd.setTitle("Uploading files...");
            pd.setCancelable(false);
            pd.setIndeterminate(false);
            pd.show();
        }

        @Override
        protected void onPostExecute(Map<Long, UploadData> longUploadDataMap) {
            pd.dismiss();
            replaceFiles(longUploadDataMap);
            editingIdentifier = null;
        }
    }

    private class EditButtonListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            if (actionModeEnabled) {
                return;
            }
            Integer i = (Integer)view.getTag();
            CursorAdapter cadap = (CursorAdapter)getListView().getAdapter();
            Cursor c = cadap.getCursor();
            c.moveToPosition(i);

            String server_url = c.getString(c.getColumnIndex(DBHelper.BASE_URL));
            String uuid = c.getString(c.getColumnIndex(DBHelper.UPLOAD_UUID));
            long id = c.getLong(c.getColumnIndex(DBHelper.COLUMN_ID));

            editingIdentifier = new UUIDLocalIDPair(server_url, uuid, id);
            editingIdentifier.setOptPrivate(c.getInt(c.getColumnIndex(DBHelper.UPLOAD_PRIVATE)) == 1);

            final Context ctxt = MainActivity.this;

            AlertDialog.Builder builder = new AlertDialog.Builder(ctxt);
            builder.setItems(R.array.edit_options, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent intent;
                    switch(i) {
                        case 0:
                            Toast.makeText(getApplicationContext(),
                                    "This would be paste hint", Toast.LENGTH_SHORT).show();
                            break;
                        case 1:
                            Intent requestFilesIntent = new Intent(Intent.ACTION_GET_CONTENT);
                            requestFilesIntent.setType("*/*");
                            requestFilesIntent.addCategory(Intent.CATEGORY_OPENABLE);

                            intent = Intent.createChooser(requestFilesIntent, getString(R.string.choose_files));
                            startActivityForResult(intent, OPTIONS_REPLACE_SINGLE);
                            break;
                        case 2:
                            AlertDialog.Builder b2 = new AlertDialog.Builder(ctxt);
                            final EditText et = new EditText(ctxt);
                            b2.setTitle("Enter text to upload");
                            et.setRawInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                            b2.setView(et);
                            b2.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    String s = et.getText().toString();
                                    if (!TextUtils.isEmpty(s)) {
                                        editingIdentifier.setOptData(new UriOrRaw(s.getBytes()));
                                        new ReplaceFilesTask().execute(editingIdentifier);
                                    }
                                    else {
                                        Toast.makeText(MainActivity.this, R.string.MainActivity_NoBlank_Text, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                            b2.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    editingIdentifier = null;
                                }
                            });
                            b2.create().show();
                            break;
                    }
                }
            });
            builder.setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    editingIdentifier = null;
                }
            });

            builder.create().show();
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (getIsDark()) {
            setTheme(R.style.AppThemeDark);
        }
        else {
            setTheme(R.style.AppTheme);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        //populateActivity();

        server_spinner = (Spinner) findViewById(R.id.server_spinner);
        sqlhelper = DBHelper.getInstance(this);


        Intent intent = getIntent();
        final String action = intent.getAction();
        String type = intent.getType();

        ListAdapter adapter = new UploadsCursorAdapter(this, sqlhelper.getAllUploads(), false, new EditButtonListener());
        setListAdapter(adapter);

        getListView().setMultiChoiceModeListener(new MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode actionMode, int i, long l, boolean b) {
                int item_count = getListView().getCheckedItemCount();
                if (item_count == 1) {
                    actionMode.setSubtitle(getString(R.string.single_item));
                } else {
                    actionMode.setSubtitle(String.format(getString(R.string.multiple_items), item_count));
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                MenuInflater mi = getMenuInflater();
                actionMode.setTitle(getString(R.string.select_items));
                actionMode.setSubtitle(getString(R.string.single_item));
                mi.inflate(R.menu.uploads_cab_menu, menu);
                actionModeEnabled = true;
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                //getExpandableListView().get
                String s;
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                ClipData clipdata;
                Intent intent;

                switch (menuItem.getItemId()) {
                    case R.id.copy_separate:
                        Log.d("ItemCAB", "Copied things as separate links");
                        s = getBatchSeparate();
                        clipdata = ClipData.newPlainText(getResources().getString(R.string.message), s);
                        clipboard.setPrimaryClip(clipdata);
                        Toast.makeText(context, getString(R.string.toast_copy), Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.share_separate:
                        Log.d("ItemCAB", "Sharing as separate");
                        s = getBatchSeparate();
                        intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, s);
                        startActivity(Intent.createChooser(intent, getString(R.string.header_share)));
                        break;
                    case R.id.delete:
                        Log.d("ItemCAB", "Deleting");
                        UUIDLocalIDPair[] deleteItems = getDeleteCandidates();
                        new DeleteFilesTask().execute(deleteItems);
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                actionModeEnabled = false;
            }
        });

        String[] server_data = {"base_url"};
        int[] server_rsc = {R.id.server_url};
        SimpleCursorAdapter serv_adap = new SimpleCursorAdapter(this, R.layout.server_item,
                sqlhelper.getAllServers(), server_data, server_rsc, CursorAdapter.FLAG_AUTO_REQUERY);
        server_spinner.setAdapter(serv_adap);

        if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            ClipData clippy = intent.getClipData();
            Log.d("MainActivity", "Received " + clippy.getItemCount() + " files from another app");
            //shouldn't the share be sent directly to the uploads option?
            Intent preserve_this = new Intent(this, UploadOptionsActivity.class);
            preserve_this.setClipData(clippy);
            startActivityForResult(preserve_this, OPTIONS_SET_MULTIPLE);

        }
        else if (Intent.ACTION_SEND.equals(action) && type != null) {
            String mimetype = intent.getType();
            if (mimetype.equals("text/plain")) {
                String text_data = intent.getStringExtra(Intent.EXTRA_TEXT);
                boolean isUrl = (URLUtil.isHttpsUrl(text_data) || URLUtil.isHttpUrl(text_data));
                if (isUrl) {
                    Log.d("MainActivity", "Received redirect from share");
                    Cursor selected_server = (Cursor)server_spinner.getSelectedItem();
                    String server_path = selected_server.getString(selected_server.getColumnIndex(DBHelper.BASE_URL));
                    new ShortenURLTask(server_path).execute(text_data);
                } else {
                    Log.d("MainActivity", "Received raw text from share");
                    Intent preserve_this = new Intent(MainActivity.this,
                            UploadOptionsActivity.class);
                    preserve_this.putExtra(UploadOptionsActivity.EXTRA_RAW_TEXT, text_data);
                    startActivityForResult(preserve_this, OPTIONS_SET_SINGLE_RAW_TEXT);
                }
            }
            else if (intent.getClipData() != null) {
                Log.d("MainActivity", "Received 1 file from share");
                Intent preserve_this = new Intent(MainActivity.this,
                        UploadOptionsActivity.class);
                preserve_this.setData(intent.getClipData().getItemAt(0).getUri());
                startActivityForResult(preserve_this, OPTIONS_SET_SINGLE);
            }
        }
        else {
            Log.d("MainActivity", "Doing GUI interaction");
            //other stuff
        }
    }

    private UUIDLocalIDPair[] getDeleteCandidates() {
        SparseBooleanArray selection = getListView().getCheckedItemPositions();
        LinkedList<UUIDLocalIDPair> items = new LinkedList<>();
        for (int i=0;i<getListAdapter().getCount();i++) {
            if (selection.get(i, false)) {
                Cursor c = (Cursor)getListView().getItemAtPosition(i);
                String uuid = c.getString(c.getColumnIndex(DBHelper.UPLOAD_UUID));
                String base_url = c.getString(c.getColumnIndex(DBHelper.BASE_URL));
                long localID = c.getLong(c.getColumnIndex(DBHelper.COLUMN_ID));
                items.add(new UUIDLocalIDPair(base_url, uuid, localID));
            }
        }
        return items.toArray(new UUIDLocalIDPair[items.size()]);
    }

    public String getBatchSeparate() {
        StringBuilder sb = new StringBuilder();
        SparseBooleanArray selection = getListView().getCheckedItemPositions();

        for (int i=0;i<getListAdapter().getCount();i++) {
            if (selection.get(i, false)) {
                Cursor c = (Cursor)getListView().getItemAtPosition(i);
                    sb.append(c.getString(c.getColumnIndex("complete_url")));
                    sb.append("\n");
            }
        }

        return sb.toString();
    }

    private boolean getIsDark() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isdark = preferences.getBoolean("isDark", false);
        return isdark;
    }

    public void requestItems(View view) {

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.Main_AddItem);
        b.setItems(UPLOAD_TYPES, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Intent intent;
                AlertDialog.Builder b2;
                final EditText et;

                switch (i) {
                    case 0:
                        b2 = new AlertDialog.Builder(MainActivity.this);
                        et = new EditText(MainActivity.this);
                        et.setRawInputType(InputType.TYPE_TEXT_VARIATION_URI|InputType.TYPE_CLASS_TEXT);
                        b2.setTitle("Enter a URL to shorten");
                        b2.setView(et);
                        b2.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Cursor selected_server = (Cursor)server_spinner.getSelectedItem();
                                String server_path = selected_server.getString(selected_server.getColumnIndex(DBHelper.BASE_URL));
                                new ShortenURLTask(server_path).execute(et.getText().toString());
                            }
                        });
                        b2.setNegativeButton("Cancel", null);
                        b2.create().show();
                        break;
                    case 1:
                        b2 = new AlertDialog.Builder(MainActivity.this);
                        et = new EditText(MainActivity.this);
                        b2.setTitle("Enter text to upload");
                        et.setRawInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                        b2.setView(et);
                        b2.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                String s = et.getText().toString();
                                if (!TextUtils.isEmpty(s)) {
                                    Intent intent = new Intent(MainActivity.this,
                                            UploadOptionsActivity.class);
                                    intent.putExtra(UploadOptionsActivity.EXTRA_RAW_TEXT, s);
                                    startActivityForResult(intent, OPTIONS_SET_SINGLE_RAW_TEXT);
                                }
                                else {
                                    Toast.makeText(MainActivity.this, R.string.MainActivity_NoBlank_Text, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        b2.setNegativeButton("Cancel", null);
                        b2.create().show();
                        break;
                    case 2: //files
                        Intent requestFilesIntent = new Intent(Intent.ACTION_GET_CONTENT);
                        requestFilesIntent.setType("*/*");
                        requestFilesIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        requestFilesIntent.addCategory(Intent.CATEGORY_OPENABLE);

                        intent = Intent.createChooser(requestFilesIntent, getString(R.string.choose_files));
                        startActivityForResult(intent, PICKING_FOR_UPLOAD);
                        break;
                }
            }
        });
        b.setNegativeButton("Cancel", null);
        b.create().show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICKING_FOR_UPLOAD) {
            if (resultCode == RESULT_OK) {
                Uri single_data = data.getData();
                ClipData clippy = data.getClipData();

                if (clippy != null) {
                    Intent preserve_this = new Intent(this, UploadOptionsActivity.class);
                    preserve_this.setClipData(clippy);
                    startActivityForResult(preserve_this, OPTIONS_SET_MULTIPLE);
                }
                else if (single_data != null) {
                    Intent preserve_this = new Intent(this, UploadOptionsActivity.class);
                    preserve_this.setData(single_data);
                    startActivityForResult(preserve_this, OPTIONS_SET_SINGLE);
                }
                //do something
            }
        }
        else if (requestCode == OPTIONS_SET_MULTIPLE) {
            if (resultCode == RESULT_OK) {
                Cursor selected_server = (Cursor)server_spinner.getSelectedItem();
                String server_path = selected_server.getString(selected_server.getColumnIndex(DBHelper.BASE_URL));

                ClipData clippy = data.getClipData();
                Long sunset = null;
                boolean is_private = false;

                if (data.hasExtra(UploadOptionsActivity.EXTRA_SUNSET)) {
                    sunset = data.getLongExtra(UploadOptionsActivity.EXTRA_SUNSET, 0);
                }

                if (data.hasExtra(UploadOptionsActivity.EXTRA_IS_PRIVATE)) {
                    is_private = data.getBooleanExtra(UploadOptionsActivity.EXTRA_IS_PRIVATE, false);
                }

                //clipdata holds uris
                Log.d("MainActivity", String.format(getString(R.string.picked_multiple), clippy.getItemCount()));
                UriOrRaw[] all_the_files = new UriOrRaw[clippy.getItemCount()];
                for (int i=0;i<clippy.getItemCount();i++) {
                    all_the_files[i] = new UriOrRaw(clippy.getItemAt(i).getUri());
                }
                new UploadFilesTask(server_path, is_private, sunset).execute(all_the_files);
            }
        }
        else if (requestCode == OPTIONS_SET_SINGLE) {
            if (resultCode == RESULT_OK) {
                Uri single_data = data.getData();

                Cursor selected_server = (Cursor)server_spinner.getSelectedItem();
                String server_path = selected_server.getString(selected_server.getColumnIndex(DBHelper.BASE_URL));

                Long sunset = null;
                String vanity = null;
                boolean is_private = false;

                if (data.hasExtra(UploadOptionsActivity.EXTRA_SUNSET)) {
                    sunset = data.getLongExtra(UploadOptionsActivity.EXTRA_SUNSET, 0);
                }

                if (data.hasExtra(UploadOptionsActivity.EXTRA_IS_PRIVATE)) {
                    is_private = data.getBooleanExtra(UploadOptionsActivity.EXTRA_IS_PRIVATE, false);
                }

                Log.d("MainActivity", getString(R.string.picked_single));
                if (data.hasExtra(UploadOptionsActivity.EXTRA_VANITY)) {
                    vanity = data.getStringExtra(UploadOptionsActivity.EXTRA_VANITY);
                    new UploadFilesTask(server_path, is_private, vanity, sunset).execute(new UriOrRaw(single_data));
                }
                else {
                    new UploadFilesTask(server_path, is_private, sunset).execute(new UriOrRaw(single_data));
                }
                //Log.d("MainActivity", FileUtils.getPath(getApplicationContext(), single_data));
            }
        }
        else if (requestCode == OPTIONS_SET_SINGLE_RAW_TEXT) {
            if (resultCode == RESULT_OK) {
                String s = data.getStringExtra(UploadOptionsActivity.EXTRA_RAW_TEXT);

                Cursor selected_server = (Cursor)server_spinner.getSelectedItem();
                String server_path = selected_server.getString(selected_server.getColumnIndex(DBHelper.BASE_URL));

                Long sunset = null;
                String vanity = null;
                boolean is_private = false;

                if (data.hasExtra(UploadOptionsActivity.EXTRA_SUNSET)) {
                    sunset = data.getLongExtra(UploadOptionsActivity.EXTRA_SUNSET, 0);
                }

                if (data.hasExtra(UploadOptionsActivity.EXTRA_IS_PRIVATE)) {
                    is_private = data.getBooleanExtra(UploadOptionsActivity.EXTRA_IS_PRIVATE, false);
                }

                Log.d("MainActivity", getString(R.string.picked_single));
                if (data.hasExtra(UploadOptionsActivity.EXTRA_VANITY)) {
                    vanity = data.getStringExtra(UploadOptionsActivity.EXTRA_VANITY);
                    new UploadFilesTask(server_path, is_private, vanity, sunset).execute(new UriOrRaw(s.getBytes()));
                }
                else {
                    new UploadFilesTask(server_path, is_private, sunset).execute(new UriOrRaw(s.getBytes()));
                }
            }
        }
        else if (requestCode == OPTIONS_REPLACE_SINGLE) {
            if (resultCode == RESULT_OK) {
                Uri single_data = data.getData();
                editingIdentifier.setOptData(new UriOrRaw(single_data));
                new ReplaceFilesTask().execute(editingIdentifier);
            }
        }
    }

    private void replaceFiles(Map<Long, UploadData> longUploadDataMap) {
        for (Map.Entry<Long, UploadData> mapEntry: longUploadDataMap.entrySet()) {
            UploadData ud = mapEntry.getValue();
            sqlhelper.replaceEntry(mapEntry.getKey(), ud.getToken(), ud.getSha1sum(), ud.getPreferredHint());
        }

        UploadsCursorAdapter adap = (UploadsCursorAdapter)getListAdapter();
        adap.changeCursor(sqlhelper.getAllUploads()); //only do this if we really need to
    }

    private void addUploads(List<UploadData> uploads) {
        for (UploadData ud: uploads) {
            sqlhelper.addUpload(ud.getServerUrl(), ud.getToken(), ud.getVanity(), ud.getUUID(), ud.getSha1sum(),
                    ud.getIsPrivate(), ud.getSunset(), ud.getPreferredHint());
        }

        UploadsCursorAdapter adap = (UploadsCursorAdapter)getListAdapter();
        adap.changeCursor(sqlhelper.getAllUploads()); //only do this if we really need to
    }

    private void deleteUploads(List<Long> ids) {
        sqlhelper.deleteUploads(ids);
        UploadsCursorAdapter adap = (UploadsCursorAdapter)getListAdapter();
        adap.changeCursor(sqlhelper.getAllUploads());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_preferences) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        else if (id == R.id.action_prune) {
            sqlhelper.trimHistory();
            UploadsCursorAdapter adap = (UploadsCursorAdapter)getListAdapter();
            adap.changeCursor(sqlhelper.getAllUploads());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
