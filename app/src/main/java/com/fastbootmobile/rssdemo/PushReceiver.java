/*
 * The MIT License (MIT)
 * Copyright (c) 2016 Fastboot Mobile LLC.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, andor sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.fastbootmobile.rssdemo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.fastbootmobile.ownpushclient.OwnPushClient;
import com.fastbootmobile.ownpushclient.OwnPushCrypto;

import org.json.JSONObject;


/*
  This BroadcastReceiver receives all push notifications from the RSS demo app it uses information
  in AndroidManifest.xml to get only the notifications meant for this app ID
*/

public class PushReceiver extends BroadcastReceiver {

    private static String TAG = "PushReceiver";

    private static int notification_num = 0; // Static counter for notification numbers

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "ACTION : " + intent.getAction()); // Debug log the intent "action"

        // Get the shared preferences for OwnPush keys
        SharedPreferences pref = context.getApplicationContext().getSharedPreferences(OwnPushClient.PREF_PUSH, Context.MODE_PRIVATE);

        if (intent.getAction().equals(OwnPushClient.INTENT_RECEIVE)) {

            // This is a push message

            Log.d(TAG, "Decrypt : " + intent.getExtras().getString(OwnPushClient.EXTRA_DATA));

            OwnPushCrypto fp = new OwnPushCrypto(); // Create a crypto object for decrypt

            // Get the app key pair from shared preferences (these have been confirmed by the register intent)
            OwnPushCrypto.AppKeyPair keys = fp.getKey(pref.getString(OwnPushClient.PREF_PUBLIC_KEY, ""), pref.getString(OwnPushClient.PREF_PRIVATE_KEY, ""));

            // Decrypt the message from the intent extra data
            String msg = fp.decryptFromIntent(intent.getExtras(), BuildConfig.APP_PUBLIC_KEY, keys);

            JSONObject jObj;
            if (msg != null) {
                Log.e(TAG, "RSS : " + msg);

                try {
                    // Decode the JOSN data in the message
                    jObj = new JSONObject(msg);

                    Intent i = new Intent(Intent.ACTION_VIEW); // Create the intent
                    i.setData(Uri.parse(jObj.getString("link"))); // Set the data using URI (these are web links)

                    // Convert to pending intent
                    PendingIntent pIntent = PendingIntent.getActivity(context, (int) System.currentTimeMillis(), i, 0);

                    // Create the notification
                    Notification n  = new Notification.Builder(context.getApplicationContext())
                            .setContentTitle("OwnPush RSS") // Main Title
                            .setContentText(jObj.getString("title")) // Set content
                            .setContentIntent(pIntent) // Add the pending intent
                            .setSmallIcon(R.drawable.ic_done)
                            .setAutoCancel(true) // Remove notification if opened
                            .build();

                    n.defaults |= Notification.DEFAULT_SOUND; // Make some noise on push

                    // Get the notification manager and display notification
                    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.notify(notification_num, n);

                    // Increase the notification counter by 1
                    notification_num++;

                } catch (Exception e){
                    return;
                }

            }
        }
    }
}
