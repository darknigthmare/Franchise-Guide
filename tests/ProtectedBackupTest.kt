import com.franchiseguide.app.model.ProtectedBackup as P
import java.io.File
import java.nio.ByteBuffer

private var count=0
private fun ok(name:String,test:Boolean){check(test){name};count++;println("PASS $name")}
private fun rejects(name:String,fn:()->Unit){ok(name,runCatching(fn).isFailure)}
fun main(args:Array<String>){
 val pass="Les étoiles 🎮 du soir 2026".toCharArray();val plain="{\"schema\":11,\"media\":[{\"id\":\"m1\",\"kind\":\"MOVIE\",\"title\":\"Fixture\",\"userNote\":\"Note privée\"}],\"episodes\":[]}".toByteArray()
 val before=plain.copyOf();val pbefore=pass.copyOf();val file=P.protect(plain,pass)
 ok("Native crypto roundtrip",P.unprotect(file,pass).contentEquals(plain))
 ok("Input and caller password unchanged",plain.contentEquals(before)&&pass.contentEquals(pbefore))
 ok("Version and fixed header overhead",P.inspect(file).version==1&&file.size==plain.size+64)
 ok("PBKDF2 rounds are fixed",ByteBuffer.wrap(file).getInt(8)==600000)
 ok("Randomness per export",!file.contentEquals(P.protect(plain,pass)))
 ok("No plaintext marker in ciphertext",!file.toString(Charsets.ISO_8859_1).contains("Fixture"))
 rejects("Wrong phrase rejected"){P.unprotect(file,"Une phrase différente et longue".toCharArray())}
 for((name,offset)in listOf("ciphertext" to 48,"salt" to 16,"iv" to 32,"tag" to file.lastIndex)) {val bad=file.copyOf();bad[offset]=(bad[offset].toInt() xor 1).toByte();rejects("Modified $name refused"){P.unprotect(bad,pass)}}
 for((name,offset)in listOf("magic" to 0,"reserved" to 47)){val bad=file.copyOf();bad[offset]=(bad[offset].toInt() xor 1).toByte();rejects("Invalid header $name"){P.inspect(bad)}}
 val badCost=file.copyOf();ByteBuffer.wrap(badCost).putInt(8,Int.MAX_VALUE);rejects("Hostile cost refused before native KDF"){P.inspect(badCost)}
 rejects("Trailing bytes refused"){P.inspect(file+byteArrayOf(0))};rejects("Truncation refused"){P.inspect(file.copyOf(file.size-1))}
 for(size in listOf(0,1,P.MAX_PLAIN+1,-1)){val bad=file.copyOf();ByteBuffer.wrap(bad).putInt(12,size);rejects("Length $size refused"){P.inspect(bad)}}
 rejects("Empty plain refused"){P.protect(byteArrayOf(),pass)}
 rejects("Oversized input refused"){P.protect(ByteArray(P.MAX_PLAIN+1),pass)}
 rejects("Non-format input refused"){P.inspect(plain)}
 ok("Header sniff short input",!P.isProtected(ByteArray(7)))
 for(text in listOf("a".repeat(11)," ".repeat(20),"\u00a0".repeat(20),"a".repeat(15)+"\n","a".repeat(15)+"\u0000","a".repeat(15)+"\uD800","a".repeat(15)+"\uDC00","🎮".repeat(257)))rejects("Invalid password #$count"){P.validatePassword(text.toCharArray())}
 ok("256 supplementary codepoints accepted",runCatching{P.validatePassword("🎮".repeat(256).toCharArray())}.isSuccess)
 val spaced="  café spécial 🎮  ";val withSpace=P.protect(plain,spaced.toCharArray())
 ok("Spaces preserved",P.unprotect(withSpace,spaced.toCharArray()).contentEquals(plain))
 rejects("Trimming changes key"){P.unprotect(withSpace,spaced.trim().toCharArray())}
 rejects("No silent Unicode normalization"){P.unprotect(withSpace,java.text.Normalizer.normalize(spaced,java.text.Normalizer.Form.NFD).toCharArray())}
 val invalid=P.protect(byteArrayOf(0xC0.toByte(),0x80.toByte()),pass);rejects("Authenticated invalid UTF8 rejected"){P.decode(P.unprotect(invalid,pass))}
 ok("Bounded file reader closes input and preserves bytes",P.readBounded(file.inputStream()).contentEquals(file))
 rejects("Oversized stream fails closed"){P.readBounded(ByteArray(P.MAX_FILE+1).inputStream())}
 if(args.isNotEmpty()){val dir=File(args[0]).apply{mkdirs()};File(dir,"native.fgbackup").writeBytes(file);File(dir,"expected.json").writeBytes(plain)
  // Standard ASCII + UTF-8 + combining marks/supplementary characters, not UI credentials.
  for((i,p)in listOf("Une longue phrase ASCII", "Les étoiles 🎮 du soir 2026", "  cafe\u0301 spécial 🐈  ").withIndex())File(dir,"native-$i.fgbackup").writeBytes(P.protect(plain,p.toCharArray()))
 }
 plain.fill(0);pass.fill('\u0000');pbefore.fill('\u0000');println("ProtectedBackup: $count JVM assertions passed. No Android SDK/device claim.")
}
