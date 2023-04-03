package com.zhaoxinyu.bind.logic.entities

import com.baidu.location.BDLocation

data class BinderInfo(val location:BDLocation,val batteryCapacity:Int,val netWork:String){
    companion object{
        const val WIFI="wifi"
        const val FOUR_G="4G"
        const val FIVE_G="5G"
        const val NET_ERROR=""
    }
}
