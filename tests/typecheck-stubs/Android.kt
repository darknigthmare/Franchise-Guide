@file:Suppress("UNUSED_PARAMETER")
package android.content
// Signature stubs for non-UI Kotlin compile checks; no Android execution is claimed.
open class Context {
 companion object {const val MODE_PRIVATE=0}
 val applicationContext:Context get()=error("TYPECHECK ONLY")
 fun getSharedPreferences(name:String,mode:Int):SharedPreferences=error("TYPECHECK ONLY")
}
interface SharedPreferences {
 fun getString(key:String,default:String?):String?
 fun getLong(key:String,default:Long):Long
 fun getBoolean(key:String,default:Boolean):Boolean
 fun edit():Editor
 interface Editor {
  fun putString(key:String,value:String?):Editor
  fun putLong(key:String,value:Long):Editor
  fun putBoolean(key:String,value:Boolean):Editor
  fun remove(key:String):Editor
  fun commit():Boolean
  fun apply()
 }
}
