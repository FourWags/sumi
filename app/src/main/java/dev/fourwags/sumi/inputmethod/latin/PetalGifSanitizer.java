package dev.fourwags.sumi.inputmethod.latin;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounded GIF structure validation; removes comments/plain text and non-loop application metadata. */
public final class PetalGifSanitizer {
    private PetalGifSanitizer() {}
    public static byte[] sanitize(byte[] data)throws IOException {
        if(data.length<14||data.length>8*1024*1024)throw new IOException("Invalid image");
        String header=new String(data,0,6,StandardCharsets.US_ASCII);
        if(!header.equals("GIF87a")&&!header.equals("GIF89a"))throw new IOException("Invalid image");
        Cursor c=new Cursor(data);c.p=6;int width=c.word(),height=c.word();
        if(width<1||height<1||width>2048||height>2048)throw new IOException("Image dimensions too large");
        int packed=c.next();c.skip(2);if((packed&128)!=0)c.skip(3*(1<<((packed&7)+1)));
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(data,0,c.p);int frames=0;long pixels=0;
        while(c.p<data.length){
            int start=c.p,kind=c.next();
            if(kind==0x3b){if(frames==0||c.p!=data.length)throw new IOException("Invalid image");out.write(kind);return out.toByteArray();}
            if(kind==0x2c){
                int x=c.word(),y=c.word(),w=c.word(),h=c.word();
                if(w<1||h<1||(long)x+w>width||(long)y+h>height||++frames>300||(pixels+=(long)w*h)>128000000L)throw new IOException("Animation too large");
                int flags=c.next();if((flags&128)!=0)c.skip(3*(1<<((flags&7)+1)));
                int lzw=c.next();if(lzw<2||lzw>8)throw new IOException("Invalid image");c.blocks();out.write(data,start,c.p-start);
            }else if(kind==0x21){
                int label=c.next();boolean keep=false;
                if(label==0xf9){if(c.next()!=4)throw new IOException("Invalid image");c.skip(4);if(c.next()!=0)throw new IOException("Invalid image");keep=true;}
                else if(label==0xff){int n=c.next();int app=c.p;c.skip(n);int block=c.p;c.blocks();
                    // Retain only the known animation-loop extension, never arbitrary application payloads.
                    keep=n==11&&new String(data,app,n,StandardCharsets.US_ASCII).equals("NETSCAPE2.0")&&c.p-block==5&&data[block]==3&&data[block+1]==1;
                }else if(label==0xfe){c.blocks();}
                else if(label==0x01){int n=c.next();if(n!=12)throw new IOException("Invalid image");c.skip(n);c.blocks();}
                else throw new IOException("Unsupported image extension");
                if(keep)out.write(data,start,c.p-start);
            }else throw new IOException("Invalid image");
        }throw new IOException("Incomplete image");
    }
    private static final class Cursor {
        final byte[] data;int p;Cursor(byte[] data){this.data=data;}
        int next()throws IOException{if(p>=data.length)throw new EOFException();return data[p++]&255;}
        int word()throws IOException{return next()|(next()<<8);}
        void skip(int n)throws IOException{if(n<0||n>data.length-p)throw new EOFException();p+=n;}
        void blocks()throws IOException{int n;while((n=next())!=0)skip(n);}
    }
}
