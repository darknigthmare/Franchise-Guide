package androidx.compose.runtime
// Signatures only; not used by executable domain tests or the packaged application.
class MutableState<T>(var value:T)
fun <T> mutableStateOf(value:T)=MutableState(value)
fun <T> mutableStateListOf(vararg elements:T):MutableList<T> = elements.toMutableList()
