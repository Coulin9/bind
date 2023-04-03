package com.zhaoxinyu.bind.utils

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class DateJsonAdapter:TypeAdapter<Date>() {
    override fun  write(out: JsonWriter?, value: Date?) {
        val dateFormat=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.CHINA)
        val dString=dateFormat.format(value!!)
        out?.value(dString)
    }

    override fun read(`in`: JsonReader?): Date {
        val fString=`in`?.nextString()
        val dateFormat=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.CHINA)
        val res=dateFormat.parse(fString!!)
        if(res!=null) return res
        else throw IOException()
    }
}