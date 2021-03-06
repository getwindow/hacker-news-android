package io.dwak.holohackernews.app.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import io.dwak.holohackernews.app.R;

public final class IntentUtils {
    /**
     * Opens the given url in the system browser
     * @param context Context with which to open the browser from
     * @param uri Url to open in the browser
     * @see Intent#ACTION_VIEW
     */
    public static void openInBrowser(final Context context, final String uri){
        Intent browserIntent = new Intent();
        browserIntent.setAction(Intent.ACTION_VIEW);
        browserIntent.setData(Uri.parse(uri));
        try{
            context.startActivity(browserIntent);
        }
        catch (ActivityNotFoundException  e){
            Toast.makeText(context, R.string.browser_not_found_error_toast, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Share plain text using system share
     * @param context Context to share from
     * @param text plain text to share
     * @see Intent#ACTION_SEND
     */
    public static void share(final Context context, final String text){
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(shareIntent);
    }
}
