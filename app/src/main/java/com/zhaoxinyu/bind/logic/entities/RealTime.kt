package com.zhaoxinyu.bind.logic.entities

import com.google.gson.annotations.SerializedName

data class RealTime(val status:String,@SerializedName("result") val realtimeRes:Res?)

data class Res(val realtime:RealTimeRes)

data class Precipitation(val local:PrecipiItem)
data class PrecipiItem(val status: String,val datasource:String,val intensity:Float)
data class RealTimeRes(val status: String,val temperature:Float,val humidity:Float
,val cloudrate:Float,val skycon:String,val visibility:Float,val wind:Wind,val pressure:Float
,val dswrf:Float,@SerializedName("apparent_temperature") val appTemperature:Float
,val precipitation:Precipitation,@SerializedName("air_quality")val airQuality:AirQuality)
