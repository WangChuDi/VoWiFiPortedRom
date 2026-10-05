// SPDX-License-Identifier: GPL-2.0
package me.phh.sip

/** TS24.011 tables7.8/8.4: type/ref followed by mandatory RP-Cause LV. */
fun rpDeliveryError(ref:Byte,result:Int):ByteArray {
    val cause=when(result){3->22;4->97;else->111}
    return byteArrayOf(SmsType.RP_ERROR_TO_NETWORK.value,ref,1,cause.toByte())
}
