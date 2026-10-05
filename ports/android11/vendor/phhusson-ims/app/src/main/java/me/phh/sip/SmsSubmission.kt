// SPDX-License-Identifier: GPL-2.0
package me.phh.sip

/** SIP acceptance and RP acceptance are separate; neither implies recipient delivery. */
class SmsSubmission {
    private var sipAccepted = false
    private var rpAccepted = false
    private var finished = false
    @Synchronized fun onSip(status: Int): Boolean? {
        if (finished || status < 200) return null
        if (status != 200 && status != 202) return finish(false)
        sipAccepted = true
        return if (rpAccepted) finish(true) else null
    }
    @Synchronized fun onRp(accepted: Boolean): Boolean? {
        if (finished) return null
        if (!accepted) return finish(false)
        rpAccepted = true
        return if (sipAccepted) finish(true) else null
    }
    @Synchronized fun expire(): Boolean? = if (finished) null else finish(false)
    private fun finish(result: Boolean): Boolean { finished = true; return result }
}
