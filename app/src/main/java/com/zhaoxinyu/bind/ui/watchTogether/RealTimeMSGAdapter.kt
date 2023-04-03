package com.zhaoxinyu.bind.ui.watchTogether

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.databinding.MsgLeftItemLayoutBinding
import com.zhaoxinyu.bind.databinding.MsgRightItemLayoutBinding
import com.zhaoxinyu.bind.logic.entities.RealTimeMSG
import com.zhaoxinyu.bind.ui.HomeFragment

class RealTimeMSGAdapter(val msgList: MutableList<RealTimeMSG>,val context: Context):RecyclerView.Adapter<ViewHolder>() {
    companion object{
        const val TYPE_RIGHT=0
        const val TYPE_LEFT=1
    }
    inner class ViewHolder1(val binding:MsgRightItemLayoutBinding):ViewHolder(binding.root)
    inner class ViewHolder2(val binding: MsgLeftItemLayoutBinding):ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return if(msgList[position].type==RealTimeMSG.TYPE_RIGHT) TYPE_RIGHT
        else TYPE_LEFT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        var viewHolder:ViewHolder?=null
        when(viewType){
            TYPE_RIGHT->{
                val binding=MsgRightItemLayoutBinding.inflate(LayoutInflater.from(context),parent,false)
                if(HomeFragment.iconFile!=null) binding.headIcon.setImageURI(Uri.parse(HomeFragment.iconFile?.path))
                viewHolder=ViewHolder1(binding)
            }
            TYPE_LEFT->{
                val binding=MsgLeftItemLayoutBinding.inflate(LayoutInflater.from(context),parent,false)
                if(HomeFragment.taIconFile!=null) binding.headIcon.setImageURI(Uri.parse(HomeFragment.taIconFile?.path))
                viewHolder=ViewHolder2(binding)
            }
        }
        return viewHolder!!
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if(holder is ViewHolder1){
            holder.binding.msgText.text=msgList[position].msg
        }else if(holder is ViewHolder2){
            holder.binding.msgText.text=msgList[position].msg
        }
    }

    override fun getItemCount(): Int {
        return msgList.size
    }
}