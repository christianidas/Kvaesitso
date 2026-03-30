package de.mm20.launcher2.searchactions.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Toast
import de.mm20.launcher2.ktx.tryStartActivity

data class SendSmsAction(
    override val label: String,
    val phoneNumber: String,
    val messageBody: String,
    val silent: Boolean = false,
    override val icon: SearchActionIcon = SearchActionIcon.Message,
    override val iconColor: Int = 0,
    override val customIcon: String? = null,
) : SearchAction {

    override fun start(context: Context) {
        if (silent && context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(phoneNumber, null, messageBody, null, null)
            Toast.makeText(context, "SMS sent to $phoneNumber", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", messageBody)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.tryStartActivity(intent)
        }
    }
}
