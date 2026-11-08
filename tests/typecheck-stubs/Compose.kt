package androidx.compose.runtime
// Signatures only; not used by executable domain tests or the packaged application.
class MutableState<T>(var value:T)
fun <T> mutableStateOf(value:T)=MutableState(value)
fun <T> mutableStateListOf(vararg elements:T):MutableList<T> = elements.toMutableList()

operator fun <T> MutableState<T>.getValue(thisRef:Any?,property:kotlin.reflect.KProperty<*>):T=value
operator fun <T> MutableState<T>.setValue(thisRef:Any?,property:kotlin.reflect.KProperty<*>,newValue:T){value=newValue}
