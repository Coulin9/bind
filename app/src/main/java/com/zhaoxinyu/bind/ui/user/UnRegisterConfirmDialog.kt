package com.zhaoxinyu.bind.ui.user

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.logic.UserRepository
import com.zhaoxinyu.bind.ui.HomeFragment

class UnRegisterConfirmDialog:DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog=AlertDialog.Builder(requireContext())
            .setMessage("你确定要注销账号吗？")
            .setPositiveButton("确定",object :DialogInterface.OnClickListener{
                override fun onClick(dialog: DialogInterface?, which: Int) {
                    HomeFragment.iconFile?.delete()
                    HomeFragment.taIconFile?.delete()
                    val user=MainActivity.loggedUser
                    MainActivity.loggedUser=null
                    UserRepository.logout()
                    UserRepository.unRegister(user!!)
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