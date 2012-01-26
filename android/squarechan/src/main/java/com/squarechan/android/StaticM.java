package com.squarechan.android;

import android.provider.MediaStore;
import android.database.Cursor;
import android.net.Uri;
import android.app.Activity;

// copied from
// http://stackoverflow.com/questions/2169649/open-an-image-in-androids-built-in-gallery-app-programmatically

// this is implemented in java rather than scala because for some
// reason the scala compiler couldn't see the symbol
// MediaStore.Images.Media.DATA

public class StaticM {
    public static String getPath(Activity act, Uri uri) {
	String[] projection = { MediaStore.Images.Media.DATA };
	Cursor cursor = act.managedQuery(uri, projection, null, null, null);
	if (cursor == null) {
	    return null;
	}
	int column_index = cursor
            .getColumnIndex(MediaStore.Images.Media.DATA);
	if (column_index == -1) {
	    return null;
	}
	cursor.moveToFirst();
	return cursor.getString(column_index);
    }
}