package dev.fourwags.sumi.inputmethod.latin;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.UserManager;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-imported media only. No source URI/name, typed text, account, history, or network access. */
public final class PetalPersonalMediaStore {
    private static final Object LOCK=new Object();
    public static final int MAX_BYTES=8*1024*1024,MAX_ITEMS=128;
    private static final long MAX_TOTAL=64L*1024*1024;
    public static final long SHARE_LIFETIME=10*60*1000L;
    private PetalPersonalMediaStore(){}
    private static Context credential(Context c)throws IOException{
        UserManager users=c.getSystemService(UserManager.class);if(users==null||!users.isUserUnlocked())throw new IOException("Unlock your phone first");
        Context app=c.getApplicationContext();if(app.isDeviceProtectedStorage())throw new IOException("Private storage unavailable");return app;
    }
    private static File folder(Context c)throws IOException{
        File f=new File(credential(c).getNoBackupFilesDir(),"personal_media");if(!f.isDirectory()&&!f.mkdirs())throw new IOException("Storage unavailable");return f;
    }
    private static File source(Context c,String id)throws IOException{
        if(id==null||!id.matches("[0-9a-f]{32}\\.(png|gif)"))throw new IOException("Invalid media");
        File f=new File(folder(c),id);if(!f.getCanonicalFile().getParentFile().equals(folder(c).getCanonicalFile()))throw new IOException("Invalid media");return f;
    }
    public static List<String> list(Context c)throws IOException{synchronized(LOCK){
        cleanupShares(c);
        cleanupParts(c);
        File[] files=folder(c).listFiles();if(files==null)throw new IOException("Storage unavailable");
        List<File> valid=new ArrayList<>();for(File f:files)if(f.getName().matches("[0-9a-f]{32}\\.(png|gif)")&&f.isFile())valid.add(f);
        valid.sort(Comparator.comparingLong(File::lastModified).reversed().thenComparing(File::getName));
        List<String> ids=new ArrayList<>();for(File f:valid)ids.add(f.getName());return ids;
    }}
    public static String mime(String id){return id.endsWith(".gif")?"image/gif":"image/png";}
    public static String importUri(Context c,Uri uri,AtomicBoolean cancelled)throws IOException{return importUri(c,uri,cancelled,new android.os.CancellationSignal());}
    public static String importUri(Context c,Uri uri,AtomicBoolean cancelled,android.os.CancellationSignal signal)throws IOException{
        if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("Choose a file on this phone");
        try(android.content.res.AssetFileDescriptor descriptor=c.getContentResolver().openAssetFileDescriptor(uri,"r",signal)){
            if(descriptor==null)throw new IOException("Unable to open image");
            signal.setOnCancelListener(()->{try{descriptor.close();}catch(IOException ignored){}});
            try(InputStream in=descriptor.createInputStream()){return importStream(c,in,cancelled);}
            finally{signal.setOnCancelListener(null);}
        }
    }
    static String importStream(Context c,InputStream in,AtomicBoolean cancelled)throws IOException{
        byte[] bytes=readBounded(in,cancelled);
        synchronized(LOCK){
        cleanupParts(c);boolean gif=bytes.length>=6&&bytes[0]=='G'&&bytes[1]=='I'&&bytes[2]=='F';
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>4096||bounds.outHeight>4096||(long)bounds.outWidth*bounds.outHeight>16000000L)throw new IOException("Unsupported or oversized image");
        if(!Arrays.asList("image/png","image/jpeg","image/webp","image/gif").contains(bounds.outMimeType))throw new IOException("Use PNG, JPEG, WebP, or GIF");
        if(gif)bytes=PetalGifSanitizer.sanitize(bytes);
        List<String> items=list(c);long total=0;for(String item:items)total+=source(c,item).length();
        if(items.size()>=MAX_ITEMS)throw new IOException("Collection full: remove an item first");
        final long collectionBytes=total;
        final byte[] validated=bytes;Bitmap bitmap=null;
        String id=UUID.randomUUID().toString().replace("-","")+(gif?".gif":".png");File staging=new File(folder(c),"."+id+".part");
        try{
            bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(validated)),(decoder,info,src)->{
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);int width=info.getSize().getWidth(),height=info.getSize().getHeight();float scale=Math.min(1f,(gif?128f:1024f)/Math.max(width,height));decoder.setTargetSize(Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)));
                decoder.setOnPartialImageListener(error->false);
            });
            checkCancelled(cancelled);
            try(FileOutputStream out=new FileOutputStream(staging)){
                if(gif)out.write(validated);else if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Unable to store image");
                out.getFD().sync();
            }
            if(staging.length()>MAX_BYTES)throw new IOException("Image exceeds 8 MB after conversion");
            if(collectionBytes+staging.length()>MAX_TOTAL)throw new IOException("Collection full: remove an item first");
            synchronized(cancelled){checkCancelled(cancelled);if(!staging.renameTo(source(c,id)))throw new IOException("Unable to store image");}
            return id;
        }finally{if(bitmap!=null)bitmap.recycle();if(staging.exists())staging.delete();}
    }}
    private static void cleanupParts(Context c)throws IOException{
        File[] files=folder(c).listFiles();if(files!=null)for(File f:files)if(f.getName().matches("\\.[0-9a-f]{32}\\.(gif|png)\\.part"))f.delete();
    }
    private static byte[] readBounded(InputStream in,AtomicBoolean cancelled)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){checkCancelled(cancelled);if(n==0)throw new IOException("Image provider stopped responding");if(out.size()>MAX_BYTES-n)throw new IOException("Choose an image smaller than 8 MB");out.write(buffer,0,n);}return out.toByteArray();
    }
    private static void checkCancelled(AtomicBoolean cancelled)throws IOException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new IOException("Import cancelled");}
    public static Bitmap thumbnail(Context c,String id)throws IOException{
        File f; synchronized(LOCK){f=source(c,id);if(!f.isFile())throw new IOException("Image unavailable");}
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(f),(decoder,info,src)->{decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1f,128f/Math.max(w,h));decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));decoder.setOnPartialImageListener(error->false);});
    }
    public static void remove(Context c,String id)throws IOException{synchronized(LOCK){
        File f=source(c,id);if(f.exists()&&!f.delete())throw new IOException("Unable to remove image");
        File[] shares=shareFolder(c).listFiles();if(shares!=null)for(File share:shares)if(share.getName().startsWith(id.substring(0,32)+"-"))discardShare(c,share);
    }}
    private static File shareFolder(Context c)throws IOException{File f=new File(credential(c).getCacheDir(),"personal_shares");if(!f.isDirectory()&&!f.mkdirs())throw new IOException("Storage unavailable");return f;}
    static File prepareShare(Context c,String id)throws IOException{synchronized(LOCK){
        cleanupShares(c);File original=source(c,id);if(!original.isFile())throw new IOException("Image unavailable");
        File folder=shareFolder(c);File[] shares=folder.listFiles();long size=0;if(shares!=null){if(shares.length>=16)throw new IOException("Please wait before sharing more images");for(File f:shares)size+=f.length();}
        if(size+original.length()>MAX_TOTAL)throw new IOException("Temporary media storage full");
        String name=id.substring(0,32)+"-"+UUID.randomUUID().toString().replace("-","")+"_"+(System.currentTimeMillis()+SHARE_LIFETIME)+id.substring(32);
        File output=new File(folder,name);boolean complete=false;
        try{
            try(InputStream in=new FileInputStream(original);OutputStream out=new FileOutputStream(output)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled");out.write(buffer,0,n);}}
            complete=true;return output;
        }finally{if(!complete)output.delete();}
    }}
    static long expiry(String name){try{if(!name.matches("[0-9a-f]{32}-[0-9a-f]{32}_[0-9]{13}\\.(gif|png)"))return 0;return Long.parseLong(name.substring(66,79));}catch(RuntimeException e){return 0;}}
    static void cleanupShares(Context c)throws IOException{synchronized(LOCK){File[] files=shareFolder(c).listFiles();if(files!=null)for(File f:files)if(expiry(f.getName())<=System.currentTimeMillis())discardShare(c,f);}}
    static void discardShare(Context c,File file){try{Uri uri=FileProvider.getUriForFile(c,c.getPackageName()+".media",file);c.revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(RuntimeException ignored){}file.delete();}
}
