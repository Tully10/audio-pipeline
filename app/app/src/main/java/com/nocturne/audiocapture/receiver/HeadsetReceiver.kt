package com.nocturne.audiocapture.receiver

import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class HeadsetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_HEADSET_PLUG,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED ->
                LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(Intent("com.nocturne.audiocapture.HEADSET_CHANGED"))
        }
    }
}
