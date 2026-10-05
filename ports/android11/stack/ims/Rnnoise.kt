// SPDX-License-Identifier: GPL-2.0
package me.phh.ims

/** Optional denoising is bypassed; preserves PCM without a missing JNI dependency. */
class Rnnoise : AutoCloseable {
    fun getFrameSize():Int=480
    fun processFrame(input:ByteArray,output:ByteArray){
        require(output.size>=input.size)
        input.copyInto(output)
    }
    override fun close(){}
}
