package dev.fourwags.sumi.inputmethod.latin;
import java.util.*;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class PetalGifSanitizerTest {
    private byte[] gif(){return Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==");}
    private void rejects(byte[] data){try{PetalGifSanitizer.sanitize(data);fail("Malformed GIF accepted");}catch(IOException expected){}}
    @Test public void validImagePreserved()throws Exception{assertArrayEquals(gif(),PetalGifSanitizer.sanitize(gif()));}
    @Test public void stripsComments()throws Exception{byte[] g=gif();ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(g,0,g.length-1);out.write(new byte[]{33,(byte)254,3,65,66,67,0,59});assertArrayEquals(g,PetalGifSanitizer.sanitize(out.toByteArray()));}
    @Test public void rejectsEveryTruncation(){byte[] g=gif();for(int i=0;i<g.length;i++)rejects(Arrays.copyOf(g,i));}
    @Test public void rejectsTrailingPayload(){byte[] g=Arrays.copyOf(gif(),gif().length+1);rejects(g);}
    @Test public void rejectsOversizedScreen(){byte[] g=gif();g[6]=1;g[7]=9;rejects(g);}
    @Test public void rejectsFrameOutsideScreen(){byte[] g=gif();for(int n=13;n<g.length;n++)if(g[n]==44){g[n+1]=2;break;}rejects(g);}
    @Test public void rejectsAnimationFrameBomb()throws Exception{byte[] g=gif();int image=0;for(int i=13;i<g.length;i++)if(g[i]==44){image=i;break;}ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(g,0,image);for(int n=0;n<301;n++)out.write(g,image,g.length-image-1);out.write(59);rejects(out.toByteArray());}
    @Test public void rejectsNonGif(){rejects(new byte[100]);}
}
