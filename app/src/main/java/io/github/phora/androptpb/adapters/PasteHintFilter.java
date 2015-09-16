package io.github.phora.androptpb.adapters;

import android.database.Cursor;
import android.widget.FilterQueryProvider;

import io.github.phora.androptpb.DBHelper;

/**
 * Created by phora on 9/15/15.
 */
public class PasteHintFilter implements FilterQueryProvider {

    private long server_id;
    private PasteHintsCursorAdapter filteree;

    public PasteHintFilter(long server_id, PasteHintsCursorAdapter filteree) {
        this.server_id = server_id;
        this.filteree = filteree;
    }

    @Override
    public Cursor runQuery(CharSequence charSequence) {
        String filter = charSequence.toString();

        filteree.setFilterString(filter);
        DBHelper sqlhelper = DBHelper.getInstance(filteree.getContext());

        if (charSequence == null) {
            return sqlhelper.getHintGroups(server_id);
        }
        else {
            return sqlhelper.getHintGroups(server_id, filter);
        }
    }
}
