package kernel.debug

fun dump(vararg values: Any?, printer: (String) -> Unit = ::printAndFlush) {
    Debug.dump(*values, printer = printer)
}

fun dd(vararg values: Any?, printer: (String) -> Unit = ::printAndFlush): Nothing {
    Debug.dd(*values, printer = printer)
}
