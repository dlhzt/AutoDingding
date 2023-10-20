package com.pengxh.autodingding.extensions

import com.pengxh.autodingding.bean.MailInfo
import java.io.File

fun File.createAttachMail(toAddress: String): MailInfo {
    val mailInfo = MailInfo()
    mailInfo.mailServerHost = "smtp.qq.com" //发送方邮箱服务器
    mailInfo.mailServerPort = "587" //发送方邮箱端口号
    mailInfo.isValidate = true
    mailInfo.userName = "33076659@qq.com" // 发送者邮箱地址
    mailInfo.password = "flmyjhomhflpbhae" //邮箱授权码，不是密码
    mailInfo.toAddress = toAddress // 接收者邮箱
    mailInfo.fromAddress = "33076659@qq.com" // 发送者邮箱
    mailInfo.subject = "打卡记录" // 邮件主题
    mailInfo.attachFile = this
    return mailInfo
}