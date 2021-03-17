package org.brightify.hyperdrive.krpc.api

import kotlinx.serialization.Serializable

@Suppress("FunctionName")
fun RPCDataWrapper0() = Unit

@Serializable
data class RPCDataWrapper1<T1>(val t1: T1) {
    override fun toString(): String = "$t1"
}

@Serializable
data class RPCDataWrapper2<T1, T2>(val t1: T1, val t2: T2) {
    override fun toString(): String = "($t1, $t2)"
}

@Serializable
data class RPCDataWrapper3<T1, T2, T3>(val t1: T1, val t2: T2, val t3: T3) {
    override fun toString(): String = "($t1, $t2, $t3)"
}

@Serializable
data class RPCDataWrapper4<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4) {
    override fun toString(): String = "($t1, $t2, $t3, $t4)"
}

@Serializable
data class RPCDataWrapper5<T1, T2, T3, T4, T5>(val t1: T1, val t2: T2, val t3: T3, val t4: T4, val t5: T5) {
    override fun toString(): String = "($t1, $t2, $t3, $t4, $t5)"
}

@Serializable
data class RPCDataWrapper6<T1, T2, T3, T4, T5, T6>(val t1: T1, val t2: T2, val t3: T3, val t4: T4, val t5: T5, val t6: T6) {
    override fun toString(): String = "($t1, $t2, $t3, $t4, $t5, $t6)"
}

@Serializable
data class RPCDataWrapper7<T1, T2, T3, T4, T5, T6, T7>(val t1: T1, val t2: T2, val t3: T3, val t4: T4, val t5: T5, val t6: T6, val t7: T7) {
    override fun toString(): String = "($t1, $t2, $t3, $t4, $t5, $t6, $t7)"
}

@Serializable
data class RPCDataWrapper8<T1, T2, T3, T4, T5, T6, T7, T8>(val t1: T1, val t2: T2, val t3: T3, val t4: T4, val t5: T5, val t6: T6, val t7: T7, val t8: T8) {
    override fun toString(): String = "($t1, $t2, $t3, $t4, $t5, $t6, $t7, $t8)"
}
