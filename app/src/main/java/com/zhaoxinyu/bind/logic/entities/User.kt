package com.zhaoxinyu.bind.logic.entities

import com.google.gson.annotations.JsonAdapter
import com.zhaoxinyu.bind.utils.DateJsonAdapter
import java.util.*

data class User(val id:Int?, var account:String, val userName:String, val password:String,
                val gender:String?,
                @JsonAdapter(DateJsonAdapter::class) val birthday:Date?, val iconPath:String?,
                var binderId:Int?)
