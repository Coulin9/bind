package com.zhaoxinyu.bind.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.R
import com.zhaoxinyu.bind.databinding.LoginFragmentBinding
import com.zhaoxinyu.bind.logic.UserRepository

class LoginFragment:Fragment() {
    private lateinit var binding:LoginFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding=LoginFragmentBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        //注册登陆按钮的点击事件
        binding.loginBtn.setOnClickListener {
            val account=binding.accountInputText.editText?.text?.toString()
            val password=binding.passwordInputText.editText?.text?.toString()
            if(account!=null&&password!=null){
                UserRepository.login(account, password)
            }
        }

        //跳转到注册界面
        binding.toRegisterBtn.setOnClickListener {
            MainActivity.context?.navController?.navigate(R.id.action_loginFragment_to_registerFragment)
        }
        super.onActivityCreated(savedInstanceState)
    }
}