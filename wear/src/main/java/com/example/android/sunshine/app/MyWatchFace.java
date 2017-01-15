/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.example.android.sunshine.app.DigitalWatchFaceUtility.day;
import static com.example.android.sunshine.app.DigitalWatchFaceUtility.getIconResourceForWeatherCondition;
import static com.example.android.sunshine.app.DigitalWatchFaceUtility.getStringForWeatherCondition;
import static com.example.android.sunshine.app.DigitalWatchFaceUtility.month;
import static com.example.android.sunshine.app.R.drawable.blue;


//
//package com.example.abhi.go_ubiquitous_wear_app;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE=Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL);
    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(500);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,GoogleApiClient.ConnectionCallbacks,GoogleApiClient.OnConnectionFailedListener {
        /* Handler to update the time once a second in interactive mode. */
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver=false;
        Paint mBackgroundPaint;
        Paint mTextPaint,mSmallTmpText;
        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;
        String maxTemp="28",minTemp="19";
        int weatherId=200;
        /**
         * whether the display supports fewer bits for each color in ambient mode.when true,we
         * disable anti-aliasing in ambient mode.
         */

        boolean mLowBitAmbient;
        private GoogleApiClient googleApiClient;


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.primary));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mSmallTmpText = new Paint();
            mSmallTmpText = createTextPaint(resources.getColor(R.color.digital_text));


            mCalendar = Calendar.getInstance();

            //For weather Update
            googleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
//            googleApiClient.connect();
//            Log.i("Api is",""+googleApiClient);
        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }
        private Paint createTextPaint(int textColor) {
            Paint paint=new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                //connect for receiving message for mobile
                googleApiClient.connect();
                Log.i("Api is",""+googleApiClient);
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }
        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
            if (googleApiClient!=null&&googleApiClient.isConnected()){
                googleApiClient.disconnect();
            }
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets){
            super.onApplyWindowInsets(insets);
            Resources resources=MyWatchFace.this.getResources();
            boolean isRound=insets.isRound();
            mXOffset=resources.getDimension(isRound? R.dimen.digital_x_offset_round:R.dimen.digital_x_offset);
            float textSize=resources.getDimension(isRound?R.dimen.digital_text_size_round:R.dimen.digital_text_size);
            mTextPaint.setTextSize(textSize);
            mSmallTmpText.setTextSize(textSize/3);
        }
        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            //mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if( mAmbient!= inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mSmallTmpText.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }
            //updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
//                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
//                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            //draw the background
            if(isInAmbientMode()){
                canvas.drawColor(Color.BLACK);
            }
            else
            {
                Resources res=getResources();
                Bitmap bitmap= BitmapFactory.decodeResource(res,blue);
               canvas.drawBitmap(bitmap,0,0,mBackgroundPaint);
               // canvas.drawRect(0,0,bounds.width(),bounds.height(),mBackgroundPaint);
            }
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            String text=String.format("%02d:%02d", mCalendar.get(Calendar.HOUR),mCalendar.get(Calendar.MINUTE));
            canvas.drawText(text,70,270,mTextPaint);
            float widthOfTime=mTextPaint.measureText(text);
            float afterTimeXOffset=mXOffset+widthOfTime;
            float upperYOffset=mXOffset-mTextPaint.getTextSize()+10;
            float tempYOffset=mYOffset-mSmallTmpText.getTextSize()+10;
            canvas.drawText(maxTemp+"\u00b0"+"c",afterTimeXOffset+15,tempYOffset,mSmallTmpText);
            canvas.drawText(minTemp+"\u00b0"+"c",afterTimeXOffset+35,mYOffset+20,mSmallTmpText);

            float  baseTextYOffset=mYOffset+30;
            canvas.drawText(mCalendar.get(Calendar.DAY_OF_MONTH)+" "+
                            month(mCalendar.get(Calendar.MONTH))+" "+
                            new String(mCalendar.get(Calendar.YEAR)+"").substring(2,4)+","+
                            day(mCalendar.get(Calendar.DAY_OF_WEEK))
                    ,90,200,mSmallTmpText);
//                canvas.drawText(mCalendar.get(Calendar.DAY_OF_MONTH)+""+ month(mCalendar.get(Calendar.MONTH))
//                        +""+new String(mCalendar.get(Calendar.YEAR)+"").substring(2,4))+","
//                        +day(mCalendar.get(Calendar.DAY_OF_WEEK)),90,200,mSmallTmpText);
            canvas.drawLine(afterTimeXOffset-20,upperYOffset,afterTimeXOffset-20,baseTextYOffset,mTextPaint);
            canvas.drawText(getStringForWeatherCondition(weatherId),20,160,mSmallTmpText);

            if(!isInAmbientMode())
            {
                int icon=getIconResourceForWeatherCondition(weatherId);
                Bitmap weatherIcon=BitmapFactory.decodeResource(getResources(),icon);
                canvas.drawBitmap(weatherIcon,50,85,mTextPaint);
            }
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }
        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }



        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(googleApiClient,Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d("Connection","Fail");

        }


        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d("Data Change","Called");
            for (DataEvent event:dataEventBuffer){
                if(event.getType()==DataEvent.TYPE_CHANGED){
                    DataItem item=event.getDataItem();
                    processConfigurationFor(item);
                }

            }
            dataEventBuffer.release();
            invalidate();
        }



        private void processConfigurationFor(DataItem item) {
            if("/wear_face".equals(item.getUri().getPath())){
                DataMap dataMap= DataMapItem.fromDataItem(item).getDataMap();
                if(dataMap.containsKey("HIGH_TEMP"))
                    maxTemp=dataMap.getString("HIGH_TEMP");
                if (dataMap.containsKey("LOW_TEMP"))
                    minTemp=dataMap.getString("LOW_TEMP");
                if(dataMap.containsKey("WEATHER_ID"))
                    weatherId=dataMap.getInt("WEATHER_ID");
            }
        }




        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d("Connection","Fail"+connectionResult.getErrorMessage());
        }
    }
}