package dev.fourwags.sumi.inputmethod.latin;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import static org.junit.Assert.*;

/** Synthetic generated images only; never reads phone photos, clipboard, or external providers. */
public class PetalPersonalMediaTest {
    private Context app,isolated;private File temporary;
    @Before public void setup(){app=InstrumentationRegistry.getInstrumentation().getTargetContext();temporary=new File(app.getCacheDir(),"synthetic-media-test-"+UUID.randomUUID());assertTrue(temporary.mkdirs());isolated=new ContextWrapper(app){@Override public Context getApplicationContext(){return this;}@Override public File getNoBackupFilesDir(){File f=new File(temporary,"no_backup");f.mkdirs();return f;}@Override public File getCacheDir(){File f=new File(temporary,"cache");f.mkdirs();return f;}};}
    @After public void cleanup(){removeTree(temporary);}
    private void removeTree(File f){if(f.isDirectory()){File[] files=f.listFiles();if(files!=null)for(File child:files)removeTree(child);}f.delete();}
    private static byte[] all(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}
    private byte[] png()throws Exception{Bitmap b=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888);try{b.eraseColor(0xffab5577);ByteArrayOutputStream out=new ByteArrayOutputStream();assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));return out.toByteArray();}finally{b.recycle();}}
    private String add(Context c,byte[] data)throws Exception{return PetalPersonalMediaStore.importStream(c,new ByteArrayInputStream(data),new AtomicBoolean());}
    @Test public void importNormalizesStaticAndPreservesPixels()throws Exception{String id=add(isolated,png());assertTrue(id.matches("[0-9a-f]{32}\\.png"));assertEquals(Collections.singletonList(id),PetalPersonalMediaStore.list(isolated));Bitmap preview=PetalPersonalMediaStore.thumbnail(isolated,id);try{assertEquals(0xffab5577,preview.getPixel(0,0));}finally{preview.recycle();}PetalPersonalMediaStore.remove(isolated,id);assertTrue(PetalPersonalMediaStore.list(isolated).isEmpty());}
    @Test public void gifStaysAnimatedFormatAndNoCommentMetadata()throws Exception{byte[] g;try(InputStream in=app.getAssets().open("bloom.gif")){g=all(in);}ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(g,0,g.length-1);out.write(new byte[]{33,(byte)254,3,65,66,67,0,59});String id=add(isolated,out.toByteArray());assertTrue(id.endsWith(".gif"));byte[] stored=java.nio.file.Files.readAllBytes(new File(isolated.getNoBackupFilesDir(),"personal_media/"+id).toPath());assertArrayEquals(PetalGifSanitizer.sanitize(g),stored);}
    @Test public void rejectsMalformedAndCancelledWithoutPartialFiles()throws Exception{for(byte[] data:new byte[][]{new byte[0],"not an image".getBytes(),Arrays.copyOf(png(),12)})try{add(isolated,data);fail();}catch(IOException expected){}try{PetalPersonalMediaStore.importStream(isolated,new ByteArrayInputStream(png()),new AtomicBoolean(true));fail();}catch(IOException expected){}assertTrue(PetalPersonalMediaStore.list(isolated).isEmpty());File[] files=new File(isolated.getNoBackupFilesDir(),"personal_media").listFiles();assertNotNull(files);assertEquals(0,files.length);}
    @Test public void rejectsOversizedStreamBeforeDecode()throws Exception{InputStream endless=new InputStream(){int remaining=PetalPersonalMediaStore.MAX_BYTES+1;@Override public int read(){return remaining-->0?1:-1;}@Override public int read(byte[] b,int off,int length){if(remaining==0)return -1;int n=Math.min(length,remaining);Arrays.fill(b,off,off+n,(byte)1);remaining-=n;return n;}};try{PetalPersonalMediaStore.importStream(isolated,endless,new AtomicBoolean());fail();}catch(IOException expected){}assertTrue(PetalPersonalMediaStore.list(isolated).isEmpty());}
    @Test public void countQuotaDoesNotEvictUserItems()throws Exception{File folder=new File(isolated.getNoBackupFilesDir(),"personal_media");assertTrue(folder.mkdirs());byte[] data=png();for(int n=0;n<128;n++)try(OutputStream out=new FileOutputStream(new File(folder,String.format(java.util.Locale.US,"%032x.png",n)))){out.write(data);}try{add(isolated,data);fail();}catch(IOException expected){}assertEquals(128,PetalPersonalMediaStore.list(isolated).size());}
    @Test public void pathTraversalAndNonContentUrisRejected()throws Exception{try{PetalPersonalMediaStore.thumbnail(isolated,"../../secret.png");fail();}catch(IOException expected){}for(String s:new String[]{"file:///tmp/image.png","https://example.invalid/image.gif"})try{PetalPersonalMediaStore.importUri(isolated,Uri.parse(s),new AtomicBoolean());fail();}catch(IOException expected){}}
    @Test public void personalSourceCannotBeSharedThroughProvider()throws Exception{String id=add(app,png());try{File privateFile=new File(app.getNoBackupFilesDir(),"personal_media/"+id);assertTrue(privateFile.exists());try{FileProvider.getUriForFile(app,app.getPackageName()+".media",privateFile);fail();}catch(IllegalArgumentException expected){}}finally{PetalPersonalMediaStore.remove(app,id);}}
    @Test public void shareIsReadOnlyExpiresAndRemovalRevokesCopy()throws Exception{String id=add(app,png());File share=null;try{share=PetalPersonalMediaStore.prepareShare(app,id);long expiry=PetalPersonalMediaStore.expiry(share.getName());assertTrue(expiry>System.currentTimeMillis());Uri uri=FileProvider.getUriForFile(app,app.getPackageName()+".media",share);try(InputStream in=app.getContentResolver().openInputStream(uri)){assertNotNull(in);assertEquals(137,in.read());}try{app.getContentResolver().openFileDescriptor(uri,"w");fail();}catch(SecurityException expected){}String expiredName=share.getName().replace("_"+expiry,"_"+(System.currentTimeMillis()-1000));File expired=new File(share.getParentFile(),expiredName);assertTrue(share.renameTo(expired));share=expired;Uri expiredUri=FileProvider.getUriForFile(app,app.getPackageName()+".media",expired);try{app.getContentResolver().openInputStream(expiredUri);fail();}catch(SecurityException expected){}PetalPersonalMediaStore.remove(app,id);assertFalse(share.exists());}finally{PetalPersonalMediaStore.remove(app,id);if(share!=null)share.delete();}}
    @Test public void collectionIsCredentialPrivateAndNotBackedUp()throws Exception{String id=add(app,png());try{File file=new File(app.getNoBackupFilesDir(),"personal_media/"+id);assertTrue(file.exists());assertFalse(app.isDeviceProtectedStorage());assertFalse(new File(app.createDeviceProtectedStorageContext().getNoBackupFilesDir(),"personal_media/"+id).exists());assertFalse((app.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)!=0);}finally{PetalPersonalMediaStore.remove(app,id);}}

    @Test public void pickerCopyDoesNotPersistUriOrChangeOriginal()throws Exception {
        File folder=new File(app.getCacheDir(),"media");folder.mkdirs();File original=new File(folder,"synthetic-"+UUID.randomUUID()+".png");byte[] bytes=png();try(OutputStream out=new FileOutputStream(original)){out.write(bytes);}
        String id=null;try{int grants=app.getContentResolver().getPersistedUriPermissions().size();Uri uri=FileProvider.getUriForFile(app,app.getPackageName()+".media",original);id=PetalPersonalMediaStore.importUri(app,uri,new AtomicBoolean());assertArrayEquals(bytes,java.nio.file.Files.readAllBytes(original.toPath()));assertEquals(grants,app.getContentResolver().getPersistedUriPermissions().size());}finally{if(id!=null)PetalPersonalMediaStore.remove(app,id);original.delete();}
    }
    @Test public void staticMetadataIsNotCopiedIntoStoredPng()throws Exception {
        byte[] bytes=png(),type="tEXt".getBytes(java.nio.charset.StandardCharsets.US_ASCII),metadata="Comment\0SYNTHETIC PRIVATE METADATA".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        ByteArrayOutputStream stream=new ByteArrayOutputStream();stream.write(bytes,0,bytes.length-12);java.io.DataOutputStream out=new java.io.DataOutputStream(stream);out.writeInt(metadata.length);out.write(type);out.write(metadata);java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(type);crc.update(metadata);out.writeInt((int)crc.getValue());out.write(bytes,bytes.length-12,12);
        String id=add(isolated,stream.toByteArray());byte[] stored=java.nio.file.Files.readAllBytes(new File(isolated.getNoBackupFilesDir(),"personal_media/"+id).toPath());assertFalse(new String(stored,java.nio.charset.StandardCharsets.ISO_8859_1).contains("SYNTHETIC PRIVATE METADATA"));
    }

    @Test public void byteQuotaRefusesWithoutEvictingSources()throws Exception {
        File folder=new File(isolated.getNoBackupFilesDir(),"personal_media");assertTrue(folder.mkdirs());
        for(int n=0;n<8;n++)try(RandomAccessFile file=new RandomAccessFile(new File(folder,String.format(java.util.Locale.US,"%032x.png",n)),"rw")){file.setLength(PetalPersonalMediaStore.MAX_BYTES);}
        try{add(isolated,png());fail();}catch(IOException expected){}assertEquals(8,PetalPersonalMediaStore.list(isolated).size());
        for(File f:folder.listFiles())assertEquals(PetalPersonalMediaStore.MAX_BYTES,f.length());
    }
    @Test public void closingGridCancelsAndReleasesItsWorker()throws Exception {
        java.util.List<String> ids=new java.util.ArrayList<>();for(int n=0;n<6;n++)ids.add(add(app,png()));
        final PetalPersonalMediaGrid[] grid={null};
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->grid[0]=new PetalPersonalMediaGrid(app,id->{}));
            Thread.sleep(25);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->grid[0].close());
            java.lang.reflect.Field field=PetalPersonalMediaGrid.class.getDeclaredField("worker");field.setAccessible(true);java.util.concurrent.ThreadPoolExecutor worker=(java.util.concurrent.ThreadPoolExecutor)field.get(grid[0]);assertTrue(worker.isShutdown());assertTrue(worker.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(worker.getQueue().isEmpty());
        }finally{if(grid[0]!=null)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->grid[0].close());for(String id:ids)PetalPersonalMediaStore.remove(app,id);}
    }

    @Test public void slowProviderCannotBlockCollectionListing()throws Exception {
        byte[] bytes=png();java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);AtomicBoolean cancel=new AtomicBoolean();
        InputStream stream=new ByteArrayInputStream(bytes){boolean waited;@Override public synchronized int read(byte[] b,int off,int len){if(!waited){waited=true;entered.countDown();try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();return -1;}}return super.read(b,off,len);}};
        java.util.concurrent.Future<String> importing=pool.submit(()->PetalPersonalMediaStore.importStream(isolated,stream,cancel));
        try{assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));assertTrue(pool.submit(()->PetalPersonalMediaStore.list(isolated)).get(1,java.util.concurrent.TimeUnit.SECONDS).isEmpty());release.countDown();assertNotNull(importing.get(3,java.util.concurrent.TimeUnit.SECONDS));}finally{cancel.set(true);release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));}
    }
    @Test public void abandonedImportCleanupPreservesCompleteCopies()throws Exception {
        String id=add(isolated,png());File folder=new File(isolated.getNoBackupFilesDir(),"personal_media"),partial=new File(folder,".00000000000000000000000000000000.png.part");try(OutputStream out=new FileOutputStream(partial)){out.write(png());}assertEquals(Collections.singletonList(id),PetalPersonalMediaStore.list(isolated));assertFalse(partial.exists());assertTrue(new File(folder,id).exists());
    }

    @Test public void staticStickerDimensionsAndTransparencyRemainBounded()throws Exception {
        Bitmap image=Bitmap.createBitmap(2048,1,Bitmap.Config.ARGB_8888);ByteArrayOutputStream out=new ByteArrayOutputStream();try{image.eraseColor(Color.TRANSPARENT);image.setPixel(1024,0,0xffab5577);assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
        String id=add(isolated,out.toByteArray());File file=new File(isolated.getNoBackupFilesDir(),"personal_media/"+id);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);assertEquals(1024,bounds.outWidth);assertEquals(1,bounds.outHeight);Bitmap bitmap=BitmapFactory.decodeFile(file.getPath());try{assertEquals(0,Color.alpha(bitmap.getPixel(0,0)));assertTrue(bitmap.hasAlpha());}finally{bitmap.recycle();}
    }
}
