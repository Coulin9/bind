package com.zhaoxinyu.bind

import android.app.Application
import com.baidu.location.LocationClient
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.zhaoxinyu.bind.utils.LogUtil

class BindApplication:Application() {
    override fun onCreate() {
        LogUtil.d("BindApplication","onCreate!")

//百度地图api初始化
        SDKInitializer.setAgreePrivacy(this,true)
        LocationClient.setAgreePrivacy(true)
        try {
            SDKInitializer.initialize(this)
            SDKInitializer.setCoordType(CoordType.BD09LL)
        }catch (e:Exception){
            e.printStackTrace()
        }
        super.onCreate()
    }
}