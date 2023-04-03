package com.zhaoxinyu.bind.ui.diary

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.R
import com.zhaoxinyu.bind.databinding.DiaryItemLayoutBinding
import com.zhaoxinyu.bind.databinding.DiaryMonthItemLayoutBinding
import com.zhaoxinyu.bind.logic.entities.Diary
import com.zhaoxinyu.bind.ui.HomeFragment

class DiaryListAdapter(val data:MutableList<Diary> = mutableListOf(),val fragment:DiaryFragment):RecyclerView.Adapter<ViewHolder>() {

    companion object{
        const val TYPE_ONE=1
        const val TYPE_TWO=2
    }

    inner class ViewHolder1(val binding:DiaryItemLayoutBinding):ViewHolder(binding.root)

    inner class ViewHolder2(val binding:DiaryMonthItemLayoutBinding):ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        /**
         * 如果diary的id为-1，那么就表示它是一个月份分隔符
         * userId的值代表是几月份。
         */
        if(data[position].id!=-1){
            return TYPE_ONE
        }else return TYPE_TWO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if(viewType== TYPE_ONE){
            val binding=DiaryItemLayoutBinding.inflate(LayoutInflater.from(fragment.requireContext()),parent,false)
            return ViewHolder1(binding)
        }else {
            val binding=DiaryMonthItemLayoutBinding.inflate(LayoutInflater.from(fragment.requireContext()),parent,false)
            return ViewHolder2(binding)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if(holder is ViewHolder1){
            val d=data[position]
            val iconPath=if(d.userId==MainActivity.loggedUser?.id) HomeFragment.iconFile?.path else HomeFragment.taIconFile?.path
            val year=d.date.year-100+2000
            val month=if(d.date.month+1>=10) "${d.date.month+1}" else "0${d.date.month+1}"
            val date=if(d.date.date>=10) "${d.date.date}"  else "0${d.date.date}"
            val day=when(d.date.day){
                0->"星期日"
                1->"星期一"
                2->"星期二"
                3->"星期三"
                4->"星期四"
                5->"星期五"
                6->"星期六"
                else -> "星期日"
            }
            val hour=if(d.date.hours>=10) "${d.date.hours}" else "0${d.date.hours}"
            val minute=if(d.date.minutes>=10) "${d.date.minutes}" else "0${d.date.minutes}"
            val dateTime="${year}-${month}-${date}"
            val time="${hour}:${minute}"
            val contentHint=if(d.content.length>13) d.content.substring(0,13) else d.content

           if(iconPath!=null)  holder.binding.iconView.setImageURI(Uri.parse(iconPath))
            holder.binding.contentHintText.text=contentHint
            holder.binding.dateText.text=date
            holder.binding.dayText.text=day
            holder.binding.dateTimeText.text=dateTime
            holder.binding.timeText.text=time

            holder.itemView.setOnClickListener {
                val dialog=DiaryDetailDialog(data[position],this,fragment)
                dialog.show(fragment.parentFragmentManager,null)
            }
        }else if(holder is ViewHolder2){
            val month=when(data[position].userId){
                1->"一月"
                2->"二月"
                3->"三月"
                4->"四月"
                5->"五月"
                6->"六月"
                7->"七月"
                8->"八月"
                9->"九月"
                10->"十月"
                11->"十一月"
                12->"十二月"
                else -> "一月"
            }
            holder.binding.monthDividerText.text=month
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    /**
     * 载入日记记录
     * @param data 要载入的数据集合
     */
    fun setDiaryData(data:List<Diary>){
        this.data.clear()
        this.data.addAll(data)
        notifyDataSetChanged()
    }

}