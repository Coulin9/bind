package com.zhaoxinyu.bind.logic.entities

import com.google.gson.annotations.JsonAdapter
import com.zhaoxinyu.bind.utils.DateJsonAdapter
import java.util.*

data class Diary(val id:Int?, val userId:Int,
                 @JsonAdapter(DateJsonAdapter::class) var date: Date,var content:String)
