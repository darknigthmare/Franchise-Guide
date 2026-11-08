@file:Suppress("UNUSED_PARAMETER")
package android.security.keystore
// Compile signatures only; actual Keystore behavior is tested by instrumentation.
class KeyGenParameterSpec: java.security.spec.AlgorithmParameterSpec {
 class Builder(alias:String,purposes:Int) {
  fun setKeySize(size:Int)=this
  fun setBlockModes(vararg modes:String)=this
  fun setEncryptionPaddings(vararg paddings:String)=this
  fun setRandomizedEncryptionRequired(value:Boolean)=this
  fun build()=KeyGenParameterSpec()
 }
}
object KeyProperties {
 const val KEY_ALGORITHM_AES="AES"
 const val PURPOSE_ENCRYPT=1
 const val PURPOSE_DECRYPT=2
 const val BLOCK_MODE_GCM="GCM"
 const val ENCRYPTION_PADDING_NONE="NoPadding"
}
