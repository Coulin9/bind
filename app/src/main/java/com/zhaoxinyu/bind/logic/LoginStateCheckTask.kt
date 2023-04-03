package com.zhaoxinyu.bind.logic

class LoginStateCheckTask:Thread() {
    var valid=true
    var checkGap=2L
    override fun run() {
        while(valid){
            UserRepository.testLoginState()
            sleep(1100)
        }
    }
}