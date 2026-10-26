package com.franchiseguide.app.model

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.io.InputStream

/** Portable binary v1, matching WebCrypto. No keys, hints or passwords are serialized. */
object ProtectedBackup {
    const val HEADER=48
    const val TAG=16
    const val ITERATIONS=600000
    const val MAX_PLAIN=32*1024*1024
    const val MAX_FILE=MAX_PLAIN+HEADER+TAG
    private val magic="FGPROT01".toByteArray(Charsets.US_ASCII)
    private val random=SecureRandom()
    data class Info(val version:Int=1,val plainBytes:Int,val fileBytes:Int)
    fun isProtected(data:ByteArray)=data.size>=magic.size && magic.indices.all { data[it]==magic[it] }
    fun inspect(data:ByteArray):Info {
        require(data.size in HEADER+TAG+1..MAX_FILE && isProtected(data)) { "Fichier protégé inconnu, tronqué ou trop grand." }
        val v=ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);val rounds=v.getInt(8);val size=v.getInt(12)
        require(rounds==ITERATIONS && v.getInt(44)==0 && size in 1..MAX_PLAIN && data.size==HEADER+TAG+size) { "En-tête ou taille du fichier protégé invalide." }
        return Info(plainBytes=size,fileBytes=data.size)
    }
    fun validatePassword(password:CharArray) {
        var i=0;var count=0;var visible=false
        while(i<password.size){val c=password[i];val code=when {
            Character.isHighSurrogate(c)->{require(i+1<password.size && Character.isLowSurrogate(password[i+1])) { "Phrase secrète Unicode invalide." };Character.toCodePoint(c,password[++i])}
            Character.isLowSurrogate(c)->error("Phrase secrète Unicode invalide.")
            else->c.code
        };require(code !in 0..31 && code!=127){"Caractère de contrôle interdit."}
            val blank=code in 0x2000..0x200A||code in intArrayOf(0x20,0x85,0xA0,0x1680,0x2028,0x2029,0x202F,0x205F,0x3000,0xFEFF)
            if(!blank)visible=true
            i++;count++
        }
        require(count in 12..256 && visible){"Choisis de 12 à 256 caractères, pas uniquement des espaces. Les espaces et accents sont significatifs."}
        val encoded=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(password))
        try{require(encoded.remaining()<=1024){"Phrase secrète supérieure à 1 024 octets UTF-8."}}finally{if(encoded.hasArray())encoded.array().fill(0)}
    }
    private fun key(password:CharArray,salt:ByteArray):ByteArray {
        val spec=PBEKeySpec(password,salt,ITERATIONS,256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    fun protect(value:ByteArray,password:CharArray):ByteArray {
        require(value.size in 1..MAX_PLAIN){"Sauvegarde vide ou supérieure à 32 Mio."};validatePassword(password)
        val plain=value.copyOf();val header=ByteArray(HEADER);magic.copyInto(header);val v=ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        v.putInt(8,ITERATIONS);v.putInt(12,plain.size);val salt=ByteArray(16).also(random::nextBytes);val iv=ByteArray(12).also(random::nextBytes)
        salt.copyInto(header,16);iv.copyInto(header,32);var raw:ByteArray?=null
        return try { raw=key(password,salt);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(raw,"AES"),GCMParameterSpec(128,iv));cipher.updateAAD(header);val encrypted=cipher.doFinal(plain);try{header+encrypted}finally{encrypted.fill(0)} }
        finally{plain.fill(0);raw?.fill(0);salt.fill(0);iv.fill(0)}
    }
    fun unprotect(value:ByteArray,password:CharArray):ByteArray {
        inspect(value);validatePassword(password);val data=value.copyOf();val header=data.copyOfRange(0,HEADER);val salt=header.copyOfRange(16,32);val iv=header.copyOfRange(32,44);var raw:ByteArray?=null
        return try {raw=key(password,salt);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(raw,"AES"),GCMParameterSpec(128,iv));cipher.updateAAD(header);cipher.doFinal(data,HEADER,data.size-HEADER)}
        catch(e:AEADBadTagException){throw IllegalArgumentException("Mot de passe incorrect ou fichier altéré. Aucun contenu importé.")}
        finally{data.fill(0);raw?.fill(0);salt.fill(0);iv.fill(0)}
    }
    fun decode(data:ByteArray):String=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString()
    fun readBounded(input:InputStream):ByteArray=input.use { stream->
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        try {while(true){val n=stream.read(buffer);if(n<0)break;if(n==0){val one=stream.read();if(one<0)break;require(out.size()<MAX_FILE){"Fichier supérieur à 32 Mio."};out.write(one);continue};require(out.size()+n<=MAX_FILE){"Fichier supérieur à la limite de 32 Mio."};out.write(buffer,0,n)};out.toByteArray()}
        finally{buffer.fill(0)}
    }
}
