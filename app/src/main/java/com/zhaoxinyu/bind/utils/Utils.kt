package com.zhaoxinyu.bind.utils

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type


object Utils {

    //可能有多个线程同时更新token，所以应该加个锁
    fun setToken(t:String)= synchronized(this){
        token=t
    }
    fun getToken():String= synchronized(this){
        return@synchronized token
    }
    private var token=""

    fun getType(raw:Class<*>,vararg args:Type)=object :ParameterizedType{
        override fun getRawType()=raw
        override fun getOwnerType()=null
        override fun getActualTypeArguments() = args
    }
}