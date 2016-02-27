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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.fastbootmobile.ownpushclient.OwnPushClient;
import com.fastbootmobile.ownpushclient.OwnPushCrypto;
import com.fastbootmobile.ownpushclient.OwnPushRegistrant;
import com.joshdholtz.sentry.Sentry;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;


public class MainActivity extends AppCompatActivity {

    private OwnPushRegistrant mReg;
    private RegisterReceiver receiver;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_info);

        // Initialise global variables
        mReg = new OwnPushRegistrant(this);
        mHandler = new Handler();
        receiver = new RegisterReceiver(new Handler());

        // Create the intent filter to receive the register intents from OwnPush Service
        IntentFilter filter = new IntentFilter(OwnPushClient.INTENT_REGISTER);
        filter.addCategory(BuildConfig.APP_PUBLIC_KEY); // Use the app public key as the category
        registerReceiver(receiver, filter); // Register our RegisterReceiver object for this intent

        // UI init for registration
        Button regButton = (Button) findViewById(R.id.register);

        // Get the shared preferences to store the per install encrypt keys
        final SharedPreferences prefs = this.getApplicationContext().getSharedPreferences(
                OwnPushClient.PREF_PUSH,Context.MODE_PRIVATE);

        // Check if we are already registered
        if (!prefs.getBoolean(OwnPushClient.PREF_REG_DONE,false)) {

            regButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    OwnPushCrypto fp = new OwnPushCrypto(); // Create an OwnPush crypto object
                    OwnPushCrypto.AppKeyPair keys = fp.generateInstallKey(); // Generate new keypair

                    //Register this install with the ownpush services (uses the install public key)
                    boolean ret = mReg.register(BuildConfig.APP_PUBLIC_KEY, keys.getPublicKey());

                    if (ret) {
                        // If the service returns true store the keypair for later
                        prefs.edit().putString(OwnPushClient.PREF_PUBLIC_KEY, keys.getPublicKey()).commit();
                        prefs.edit().putString(OwnPushClient.PREF_PRIVATE_KEY, keys.getPrivateKey()).commit();
                    }
                }
            });
        } else {

            // If we have not stored the setup ok flag re send the app key to the RSS server
            if (!prefs.getBoolean("setup_ok",false)) {
                registerWithBackend();
            }

            updateUI();
        }
    }

    protected void updateUI(){
        TextView txt = (TextView) findViewById(R.id.txt);

        final SharedPreferences prefs = this.getApplicationContext().getSharedPreferences(
                OwnPushClient.PREF_PUSH,Context.MODE_PRIVATE);

        Button regButton = (Button) findViewById(R.id.register);

        // If the registration is done do not allow another register attempt (disable button)
        if (prefs.getBoolean(OwnPushClient.PREF_REG_DONE, false)){
            txt.setText("Setup Done");
            regButton.setVisibility(View.GONE);
        }
    }

    protected void registerWithBackend(){

        final SharedPreferences pref = this.getSharedPreferences(OwnPushClient.PREF_PUSH, Context.MODE_PRIVATE);


        Thread httpThread = new Thread(new Runnable() {

            private String TAG = "httpThread";
            private String ENDPOINT = "https://rss.demo.ownpush.com/push/register"; // Debug location for server


            @Override
            public void run() {
                URL urlObj;

                try {
                    urlObj = new URL(ENDPOINT); // Create URL

                    // Get the install public key
                    String  install_id = pref.getString(OwnPushClient.PREF_PUBLIC_KEY, null);

                    if (install_id == null){
                        return; // This should never happen
                    }

                    // Create POST string data with install ID
                    String mPostData = "push_id=" + install_id;

                    // Create HTTP request / connection
                    HttpsURLConnection con = (HttpsURLConnection) urlObj.openConnection();
                    con.setRequestProperty("User-Agent","Mozilla/5.0 ( compatible ) ");
                    con.setRequestProperty("Accept","*/*");
                    con.setDoInput(true);
                    con.setRequestMethod("POST");
                    con.getOutputStream().write(mPostData.getBytes());

                    // Connect to HTTP server
                    con.connect();

                    // Only continue if the respond is OK
                    int http_status = con.getResponseCode();
                    if (http_status != 200){
                        Log.e(TAG, "ERROR IN HTTP REPONSE");
                        return;
                    }

                    InputStream stream;
                    stream = con.getInputStream();

                    // Build response into string format
                    BufferedReader br = new BufferedReader(new InputStreamReader(stream));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line+"\n");
                    }
                    br.close();
                    String data = sb.toString();

                    Log.i(TAG,"HTTP_OK : " + data);

                    // Check data for OK, store the setup_ok flag and update the UI as needed
                    if (data.contains("OK")){
                        pref.edit().putBoolean("setup_ok", true).commit();

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                updateUI();
                            }
                        });
                    }


                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        httpThread.start(); // Start the HTTP request to the RSS server
    }

    public class RegisterReceiver extends BroadcastReceiver {

        private final Handler handler; // Handler used to execute code on the UI thread
        private String TAG = "RegisterReceiver";

        public RegisterReceiver(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            // Until we get the registration intent they keys generated above are no use
            if (intent.getAction().equals(OwnPushClient.INTENT_REGISTER)){
                boolean status = intent.getExtras().getBoolean(OwnPushClient.EXTRA_STATUS);
                SharedPreferences pref = context.getApplicationContext().getSharedPreferences(OwnPushClient.PREF_PUSH, Context.MODE_PRIVATE);


                if (status){

                    /* The registration was confirmed by the OwnPush services
                       the install public key should be send to the RSS server to be used as the
                       address for push messages
                    */

                    String install_id = intent.getExtras().getString(OwnPushClient.EXTRA_INSTALL_ID);
                    Log.d(TAG, "INSTALL REGISTERED WITH ID : " + install_id);

                    pref.edit().putBoolean(OwnPushClient.PREF_REG_DONE,true).commit();
                    updateUI(); //Update the UI (Disable The Button)
                    registerWithBackend(); //Send the install public key to the RSS server

                } else {
                    // Something went wrong and the registration failed (try again later)
                    Log.d(TAG, "REGISTRATION FAILED ... TRY AGAIN");
                    pref.edit().remove(OwnPushClient.PREF_REG_DONE).commit();
                    pref.edit().remove(OwnPushClient.PREF_PUBLIC_KEY).commit();
                    pref.edit().remove(OwnPushClient.PREF_PRIVATE_KEY).commit();
                }
            }

        }
    }

}
