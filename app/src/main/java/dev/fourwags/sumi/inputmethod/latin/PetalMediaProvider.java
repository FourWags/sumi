package dev.fourwags.sumi.inputmethod.latin;

import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.core.content.FileProvider;
import java.io.FileNotFoundException;

/** Narrow read grants only; personal share URIs stop opening after ten minutes. */
public final class PetalMediaProvider extends FileProvider {
    private void check(Uri uri){
        if(!uri.getPathSegments().isEmpty()&&"personal_shares".equals(uri.getPathSegments().get(0))){
            long expiry=PetalPersonalMediaStore.expiry(uri.getLastPathSegment());long now=System.currentTimeMillis();
            if(expiry<=now||expiry>now+PetalPersonalMediaStore.SHARE_LIFETIME)throw new SecurityException("Media access expired");
        }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new SecurityException("Read-only media");check(uri);return super.openFile(uri,mode);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){check(uri);return super.query(uri,projection,selection,selectionArgs,sortOrder);}
    @Override public String getType(Uri uri){check(uri);return super.getType(uri);}
}
