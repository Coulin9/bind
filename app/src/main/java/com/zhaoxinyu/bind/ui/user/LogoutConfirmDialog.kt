package com.zhaoxinyu.bind.ui.user

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.logic.UserRepository
import com.zhaoxinyu.bind.ui.HomeFragment
import com.zhaoxinyu.bind.ui.diary.DiaryFragment
import com.zhaoxinyu.bind.ui.watchTogether.WatchTestFragment
import com.zhaoxinyu.bind.utils.Utils

class LogoutConfirmDialog:DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog=AlertDialog.Builder(requireContext())
            .setMessage("你确定要退出登陆吗？")
            .setPositiveButton("确认",object :DialogInterface.OnClickListener{
                override fun onClick(dialog: DialogInterface?, which: Int) {
                    //登出操作首先需要清除所有保存在本地的信息
                    //已登陆用户，用户及另一半的头像。
                    HomeFragment.iconFile?.delete()
                    HomeFragment.taIconFile?.delete()
                    MainActivity.loggedUser=null
                    Utils.setToken("")
                    MainActivity.diaryVersion=-1
                    MainActivity.taDiaryVersion=-1
                    MainActivity.sharedPreference.edit().apply {
                        remove(MainActivity.TOKEN_KEY)
                        remove(MainActivity.LOGGED_USER_KEY)
                        remove(DiaryFragment.DIARY_RECORD)
                        putInt(DiaryFragment.DIARY_VERSION_TA,-1)
                        putInt(DiaryFragment.DIARY_VERSION,-1)
                        apply()
                    }
                    UserRepository.logout()
                }
            })
            .setNegativeButton("取消",object :DialogInterface.OnClickListener{
                override fun onClick(dialog: DialogInterface?, which: Int) {
                    dialog?.cancel()
                }
            })
            .create()
        return dialog
    }
}