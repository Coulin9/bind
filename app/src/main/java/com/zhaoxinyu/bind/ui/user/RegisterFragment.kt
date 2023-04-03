package com.zhaoxinyu.bind.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.zhaoxinyu.bind.R
import com.zhaoxinyu.bind.databinding.RegisterFragmentBinding
import com.zhaoxinyu.bind.logic.UserRepository
import com.zhaoxinyu.bind.logic.entities.User

class RegisterFragment:Fragment() {
    private lateinit var binding: RegisterFragmentBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding=RegisterFragmentBinding.inflate(inflater,container,false)

        //设置gender_selector的选项
        val items = arrayOf(getString(R.string.gender_male),getString(R.string.gender_female))
        (binding.genderSelectInputText.editText as? MaterialAutoCompleteTextView)?.setSimpleItems(items)

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {

        //邮件地址校验
        binding.accountInputText.editText?.addTextChangedListener {
            //[\\w]匹配数字字母下划线，+表示重复一次或多次，*表示重复0次或多次。
            val pattern="[\\w]+(\\.[\\w]+)*@[\\w]+(\\.[\\w]+)+"
            val regex=Regex(pattern)
            //如果不匹配
            val inputToCharSequence=it?.subSequence(0,it.length)!!
            if(!regex.matches(inputToCharSequence)){
                binding.accountInputText.error=getString(R.string.wrong_account_format)
            }else binding.accountInputText.error=null
        }

        //二次密码校验
        binding.ensurePasswordInputText.editText?.addTextChangedListener {
            if(binding.passwordInputText.editText?.text?.toString()!=it?.toString()){
                binding.ensurePasswordInputText.error=getString(R.string.wrong_ensure_password)
            }else binding.ensurePasswordInputText.error=null
        }

        //性别选择校验
        binding.genderSelectInputText.editText?.addTextChangedListener {
            if(binding.genderSelectInputText.editText?.text?.toString()==""){
                binding.genderSelectInputText.error=getString(R.string.empty_gender_error)
            }else binding.genderSelectInputText.error=null
        }

        //注册按钮
        binding.registerBtn.setOnClickListener {
            //账户不能为空
            if(binding.accountInputText.editText?.text?.toString()==""){
                binding.accountInputText.error=getString(R.string.empty_account_error)
            }else binding.accountInputText.error=null
            //密码不能为空
            if(binding.passwordInputText.editText?.text?.toString()==""){
                binding.passwordInputText.error = getString(R.string.empty_password_error)
            }else binding.passwordInputText.error=null
            //确认密码不能为空
            if(binding.ensurePasswordInputText.editText?.text?.toString()==""){
                binding.ensurePasswordInputText.error=getString(R.string.empty_ensure_password_error)
            }else binding.ensurePasswordInputText.error=null
            //性别不能为空
            if(binding.genderSelectInputText.editText?.text?.toString()==""){
                binding.genderSelectInputText.error=getString(R.string.empty_gender_error)
            }else binding.genderSelectInputText.error=null
            //没有格式错误就提交
            if(binding.accountInputText.error==null&&binding.passwordInputText.error==null
                &&binding.ensurePasswordInputText.error==null&&binding.genderSelectInputText.error==null){
                val account=binding.accountInputText.editText?.text?.toString()!!
                val password=binding.passwordInputText.editText?.text?.toString()!!
                val gender=when(binding.genderSelectInputText.editText?.text?.toString()){
                    getString(R.string.gender_male)->"male"
                    getString(R.string.gender_female)->"female"
                    else -> ""
                }
                val user= User(null,account,"",password,gender,null,null,null)
                UserRepository.register(user)
            }
        }
        super.onActivityCreated(savedInstanceState)
    }

}