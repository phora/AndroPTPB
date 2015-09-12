package io.github.phora.androptpb.activities;

import android.app.ListFragment;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import io.github.phora.androptpb.DBHelper;
import io.github.phora.androptpb.R;
import io.github.phora.androptpb.adapters.ServerChooserAdapter;

public class ServersFragment extends ListFragment {

    private ServerChooserAdapter serv_adap;
    private TextView mUrlEdit;
    private Spinner mPrefixSpinner;
    private ImageButton mSubmitServer;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_server_settings, null);

        serv_adap = new ServerChooserAdapter(getActivity(),
                 DBHelper.getInstance(getActivity()).getAllServers(false), false);
        setListAdapter(serv_adap);

        mUrlEdit = (TextView) view.findViewById(R.id.editText);
        mPrefixSpinner = (Spinner) view.findViewById(R.id.spinner);
        mSubmitServer = (ImageButton) view.findViewById(R.id.submitServer);
        mSubmitServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submitNewServer();
            }
        });

        //Log.d("ServersActivity", "_id " + serv_adap.getCurId() + ", pos " + serv_adap.getCurPos() + ", getCount() " + serv_adap.getCount());
        //uncomment these to see what I mean
        //setSelection(serv_adap.getCurPos());
        //getListView().setItemChecked(serv_adap.getCurPos(), true);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int pos, long id) {
                Cursor c = (Cursor) getListView().getItemAtPosition(pos);

                DBHelper sql_helper = DBHelper.getInstance(getActivity().getApplicationContext());
                sql_helper.deleteServer(c.getLong(c.getColumnIndex(sql_helper.COLUMN_ID)));

                serv_adap.swapCursor(sql_helper.getAllServers(false));
                return true;
            }
        });
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long id) {
                Cursor currentItem = (Cursor) getListView().getItemAtPosition(pos);
                DBHelper sql_helper = DBHelper.getInstance(getActivity().getApplicationContext());

                long newId = currentItem.getLong(currentItem.getColumnIndex(sql_helper.COLUMN_ID));
                sql_helper.setDefaultServer(newId, serv_adap.getCurId());
                serv_adap.setCurId(newId);
                //serv_adap.setCurPos(pos);

                //do we need to swap cursors?
                //serv_adap.swapCursor(sql_helper.getAllServers(false));
            }
        });
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getActivity().getMenuInflater().inflate(R.menu.menu_server_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }*/

    private void submitNewServer()
    {
        String prefix = mPrefixSpinner.getSelectedItem().toString();

        DBHelper sql_helper = DBHelper.getInstance(getActivity().getApplicationContext());
        //add support for changing duration when transfer.sh and similar sites support it
        sql_helper.addServer(prefix + mUrlEdit.getText().toString());

        serv_adap.swapCursor(sql_helper.getAllServers(false));
    }
}
