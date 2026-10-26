import com.franchiseguide.app.model.ProtectedBackup
import java.io.File
fun main(args:Array<String>){val root=File(args[0]);val expected=File(root,"expected.json").readBytes();val passwords=listOf("Une longue phrase ASCII", "Les étoiles 🎮 du soir 2026", "  cafe\u0301 spécial 🐈  ")
 for((i,password)in passwords.withIndex()){val pass=password.toCharArray();val plain=ProtectedBackup.unprotect(File(root,"web-$i.fgbackup").readBytes(),pass);check(plain.contentEquals(expected)){"JS to JVM mismatch $i"};plain.fill(0);pass.fill('\u0000')}
 println("Protected backup interop: three JS files decrypted by JVM byte-for-byte.")
}
