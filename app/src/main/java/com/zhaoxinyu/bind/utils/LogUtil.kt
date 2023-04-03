package com.zhaoxinyu.bind.utils

import android.util.Log

class LogUtil{
    companion object {
        const val VERBOSE = 1
        const val DEBUG = 2
        const val INFO = 3
        const val WARN = 4
        const val ERROR = 5
        const val NONE = 6
        var filter= VERBOSE
        fun d(tag:String?,msg:String){
            if(filter<= DEBUG){
                Log.d(tag,msg)
            }
        }
        fun e(tag:String?,msg:String){
            if(filter<= ERROR){
                Log.e(tag,msg)
            }
        }
        fun i(tag:String?,msg:String){
            if(filter<= INFO){
                Log.i(tag,msg)
            }
        }
        fun v(tag:String?,msg:String){
            if(filter<= VERBOSE){
                Log.v(tag,msg)
            }
        }
        fun w(tag:String?,msg:String){
            if(filter<= WARN){
                Log.w(tag,msg)
            }
        }
    }
}