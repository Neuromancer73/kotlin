// PROBLEM: none

annotation class Some(val arg: Array<String>)

fun create(x: Int) = x

@Some(arg = <caret>create(123))
class My