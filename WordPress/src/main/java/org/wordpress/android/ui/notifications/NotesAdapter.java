package org.wordpress.android.ui.notifications;

import android.content.Context;
import android.database.Cursor;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.simperium.client.Bucket;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.Query;

import org.wordpress.android.R;
import org.wordpress.android.models.Note;
import org.wordpress.android.util.PhotonUtils;
import org.wordpress.android.util.SqlUtils;
import org.wordpress.android.util.StringUtils;
import org.wordpress.android.widgets.NoticonTextView;
import org.wordpress.android.widgets.WPNetworkImageView;

class NotesAdapter extends CursorAdapter {

    final private int mAvatarSz;
    private final Query mQuery;
    private final Bucket<Note> mNotesBucket;
    private int mSelectedPosition = ListView.INVALID_POSITION;
    private boolean mShouldHighlightRows;
    private int mReadBackgroundResId;
    private int mUnreadBackgroundResId;

    NotesAdapter(Context context, Bucket<Note> bucket) {
        super(context, null, 0x0);

        mNotesBucket = bucket;
        // build a query that sorts by timestamp descending
        mQuery = bucket.query()
                .include(
                        Note.Schema.TIMESTAMP_INDEX,
                        Note.Schema.SUBJECT_INDEX,
                        Note.Schema.SNIPPET_INDEX,
                        Note.Schema.UNREAD_INDEX,
                        Note.Schema.ICON_URL_INDEX,
                        Note.Schema.NOTICON_INDEX)
                .order(Note.Schema.TIMESTAMP_INDEX, Query.SortType.DESCENDING);

        mAvatarSz = (int) context.getResources().getDimension(R.dimen.avatar_sz_large);
        mReadBackgroundResId = R.drawable.list_bg_selector;
        mUnreadBackgroundResId = R.drawable.list_unread_bg_selector;
    }

    public void closeCursor() {
        Cursor cursor = getCursor();
        if (cursor != null) {
            cursor.close();
        }
    }

    public Note getNote(int position) {
        if (getCursor() == null) {
            return null;
        }

        Bucket.ObjectCursor<Note> cursor = (Bucket.ObjectCursor<Note>)getCursor();

        if (cursor.moveToPosition(position)) {
            String noteId = cursor.getSimperiumKey();
            try {
                return mNotesBucket.get(noteId);
            } catch (BucketObjectMissingException e) {
                return null;
            }
        }

        return null;
    }

    public void reloadNotes() {
        changeCursor(mQuery.execute());
    }

    public void setSelectedPosition(int selectedPosition) {
        mSelectedPosition = selectedPosition;
    }

    public int getSelectedPosition() {
        return mSelectedPosition;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.notifications_list_item, parent, false);
        NoteViewHolder holder = new NoteViewHolder(view);
        view.setTag(holder);

        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (cursor.isClosed())
            return;

        Bucket.ObjectCursor<Note> objectCursor = (Bucket.ObjectCursor<Note>) cursor;

        view.setActivated(mShouldHighlightRows && objectCursor.getPosition() == mSelectedPosition);

        NoteViewHolder noteViewHolder = (NoteViewHolder) view.getTag();
        noteViewHolder.setNoteId(objectCursor.getSimperiumKey());

        // Display group header
        Note.NoteTimeGroup timeGroup = Note.getTimeGroupForTimestamp(getLongForColumnName(objectCursor, Note.Schema.TIMESTAMP_INDEX));

        Note.NoteTimeGroup previousTimeGroup = null;
        if (objectCursor.getPosition() > 0 && objectCursor.moveToPrevious()) {
            previousTimeGroup = Note.getTimeGroupForTimestamp(getLongForColumnName(objectCursor, Note.Schema.TIMESTAMP_INDEX));
            objectCursor.moveToNext();
        }

        if (previousTimeGroup != null && previousTimeGroup == timeGroup) {
            noteViewHolder.headerView.setVisibility(View.GONE);
        } else {
            if (timeGroup == Note.NoteTimeGroup.GROUP_TODAY) {
                noteViewHolder.headerText.setText(context.getString(R.string.stats_timeframe_today).toUpperCase());
            } else if (timeGroup == Note.NoteTimeGroup.GROUP_YESTERDAY) {
                noteViewHolder.headerText.setText(context.getString(R.string.stats_timeframe_yesterday).toUpperCase());
            } else if (timeGroup == Note.NoteTimeGroup.GROUP_LAST_WEEK) {
                noteViewHolder.headerText.setText(context.getString(R.string.last_week).toUpperCase());
            } else {
                noteViewHolder.headerText.setText(context.getString(R.string.older).toUpperCase());
            }

            noteViewHolder.headerView.setVisibility(View.VISIBLE);
        }

        // Subject is stored in db as html to preserve text formatting
        String noteSubjectHtml = getStringForColumnName(objectCursor, Note.Schema.SUBJECT_INDEX);
        noteViewHolder.txtLabel.setText(Html.fromHtml(noteSubjectHtml));

        String noteSnippet = getStringForColumnName(objectCursor, Note.Schema.SNIPPET_INDEX);
        if (!TextUtils.isEmpty(noteSnippet)) {
            noteViewHolder.txtLabel.setMaxLines(2);
            noteViewHolder.txtDetail.setText(noteSnippet);
            noteViewHolder.txtDetail.setVisibility(View.VISIBLE);
        } else {
            noteViewHolder.txtLabel.setMaxLines(3);
            noteViewHolder.txtDetail.setVisibility(View.GONE);
        }

        String avatarUrl = PhotonUtils.fixAvatar(getStringForColumnName(objectCursor, Note.Schema.ICON_URL_INDEX), mAvatarSz);
        noteViewHolder.imgAvatar.setImageUrl(avatarUrl, WPNetworkImageView.ImageType.AVATAR);

        boolean isUnread = SqlUtils.sqlToBool(getIntForColumnName(objectCursor, Note.Schema.UNREAD_INDEX));

        String noticonCharacter = getStringForColumnName(objectCursor, Note.Schema.NOTICON_INDEX);
        if (!TextUtils.isEmpty(noticonCharacter)) {
            noteViewHolder.noteIcon.setText(noticonCharacter);
            if (isUnread) {
                noteViewHolder.noteIcon.setBackgroundResource(R.drawable.shape_oval_blue);
            } else {
                noteViewHolder.noteIcon.setBackgroundResource(R.drawable.shape_oval_grey);
            }
            noteViewHolder.noteIcon.setVisibility(View.VISIBLE);
        } else {
            noteViewHolder.noteIcon.setVisibility(View.GONE);
        }

        if (isUnread) {
            view.setBackgroundResource(mUnreadBackgroundResId);
        } else {
            view.setBackgroundResource(mReadBackgroundResId);
        }
    }

    private String getStringForColumnName(Cursor cursor, String columnName) {
        if (columnName == null || cursor == null || cursor.getColumnIndex(columnName) == -1) {
            return "";
        }

        return StringUtils.notNullStr(cursor.getString(cursor.getColumnIndex(columnName)));
    }

    private int getIntForColumnName(Cursor cursor, String columnName) {
        if (columnName == null || cursor == null || cursor.getColumnIndex(columnName) == -1) {
            return -1;
        }

        return cursor.getInt(cursor.getColumnIndex(columnName));
    }

    private long getLongForColumnName(Cursor cursor, String columnName) {
        if (columnName == null || cursor == null || cursor.getColumnIndex(columnName) == -1) {
            return -1;
        }

        return cursor.getLong(cursor.getColumnIndex(columnName));
    }

    public void setShouldHighlightRows(boolean shouldHighlightRows) {
        mShouldHighlightRows = shouldHighlightRows;
    }

    public static class NoteViewHolder {
        private final View headerView;
        private final TextView headerText;

        private final TextView txtLabel;
        private final TextView txtDetail;
        private final WPNetworkImageView imgAvatar;
        private final NoticonTextView noteIcon;

        private String mNoteId;

        public String getNoteId() {
            return mNoteId;
        }

        public void setNoteId(String noteId) {
            this.mNoteId = noteId;
        }

        NoteViewHolder(View view) {
            headerView = view.findViewById(R.id.time_header);
            headerText = (TextView)view.findViewById(R.id.header_date_text);
            txtLabel = (TextView) view.findViewById(R.id.note_label);
            txtDetail = (TextView) view.findViewById(R.id.note_detail);
            imgAvatar = (WPNetworkImageView) view.findViewById(R.id.note_avatar);
            noteIcon = (NoticonTextView) view.findViewById(R.id.note_icon);
        }
    }
}
