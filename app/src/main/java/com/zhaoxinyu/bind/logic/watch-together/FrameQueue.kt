package com.zhaoxinyu.bind.logic.`watch-together`

import org.bytedeco.javacv.Frame
import java.util.ArrayDeque


class FrameQueue(var maxLength:Int=8) {
    /**
     * 用来进行缓存的队列
     */
    private val queue=ArrayDeque<Frame>()

    /**
     * 队列长度
     */
    val size get() = queue.size

    /**
     * 提供给外界进行同步的锁
     */
    val lock=Object()

    /**
     * 向队列中添加帧
     * @param frame ：待添加的帧
     * @return :添加是否成功
     */
    fun offer(frame: Frame?):Boolean{
        return if(frame!=null&&queue.size<maxLength){
            queue.offerLast(frame)
            true
        }else false
    }

    /**
     * 添加并复制帧
     * @param frame ：待添加的帧
     * @return :添加是否成功
     */
    fun offerAndCopy(frame: Frame?):Boolean{
        return if(frame!=null&&queue.size<maxLength){
            val f=frame.clone()
            queue.offerLast(f)
            true
        }else false
    }

    /**
     * 从队头取出一帧
     * @return 取到了为Frame实例，每取到为空
     */
    fun poll():Frame?{
        return if(queue.size>0){
            queue.pollFirst()
        }else null
    }

    /**
     * 获取队头的帧，但这一帧不出队
     * @return 取到了为Frame实例，每取到为空
     */
    fun peek():Frame?{
        return if(queue.size>0){
            queue.peek()
        }else null
    }

    /**
     * 清空缓存队列
     */
    fun clear()=queue.clear()

    /**
     * 判断队列是否已空
     * @return 队列空了为true，没空为false
     */
    fun isEmpty()=queue.isEmpty()

    /**
     * 判断队列是否已满
     * @return 满了为true，没满为false
     */
    fun isFull()=queue.size==maxLength
}