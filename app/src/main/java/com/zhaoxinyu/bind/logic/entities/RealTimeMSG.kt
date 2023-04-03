package com.zhaoxinyu.bind.logic.entities

import java.io.Serializable

data class RealTimeMSG(val type:Int= TYPE_RIGHT,val msg:String=""): Serializable {
    companion object{
        const val TYPE_RIGHT=0
        const val TYPE_LEFT=1
    }
}