package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class GdumpListener implements View.OnClickListener {
    private static final String TAG = LdumpListener.class.getName();
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    private final TextView mTextView;
    private final ContentResolver mContentResolver;
    private final Uri mUri;
    private final String local_queryall="*";

    public GdumpListener(TextView _tv, ContentResolver _cr) {
        mTextView = _tv;
        mContentResolver = _cr;
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    public void onClick(View v) {
        new GdumpListener.Task().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private class Task extends AsyncTask<Void, String, Void> {

        @Override
        protected Void doInBackground(Void... params) {

            if (testQuery()) {
                publishProgress("Local Query success\n");
            } else {
                publishProgress("Local Query fail\n");
            }

            return null;
        }

        protected void onProgressUpdate(String...strings) {
            mTextView.append(strings[0]);

            return;
        }



        private boolean testQuery() {
            try {
                Cursor resultCursor = mContentResolver.query(mUri, null,
                        local_queryall, null, null);
                if (resultCursor == null) {
                    Log.e(TAG, "Result null");
                    throw new Exception();
                }
                int keyIndex = resultCursor.getColumnIndex(KEY_FIELD);
                int valueIndex = resultCursor.getColumnIndex(VALUE_FIELD);
                if (keyIndex == -1 || valueIndex == -1) {
                    Log.e(TAG, "Wrong columns");
                    resultCursor.close();
                    throw new Exception();
                }
                if (resultCursor.moveToFirst()) {
                    String returnKey="";
                    String returnValue="";
                    while (!resultCursor.isAfterLast()) {
                        returnKey = resultCursor.getString(keyIndex);
                        returnValue = resultCursor.getString(valueIndex);
                        resultCursor.moveToNext();
                        Log.v(TAG,"Key :"+returnKey);
                        Log.v(TAG,"Value :"+returnValue);
                    }
                }
                resultCursor.close();
            } catch (Exception e) {
                return false;
            }

            return true;
        }
    }
}
