/*
 * Copyright (C) 2016-2025 crDroid Android Project
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
package com.android.incallui;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import com.android.dialer.common.LogUtil;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.state.DialerCallState;

/**
 * Provides optional haptic feedback during a voice call. Three independent user
 * toggles are honoured (crDroid Sound settings):
 *
 * <ul>
 *   <li>{@code vibrate_on_connect} - buzz once when a call becomes active;
 *   <li>{@code vibrate_on_callwaiting} - buzz when a call-waiting call arrives;
 *   <li>{@code vibrate_on_disconnect} - buzz once when the active call ends.
 * </ul>
 *
 * The keys mirror {@code Settings.System.VIBRATE_ON_*}, which are hidden platform
 * constants, so the literal strings are used here.
 */
public class InCallVibrationHandler implements InCallStateListener {

    private static final String KEY_VIBRATE_ON_CONNECT = "vibrate_on_connect";
    private static final String KEY_VIBRATE_ON_CALLWAITING = "vibrate_on_callwaiting";
    private static final String KEY_VIBRATE_ON_DISCONNECT = "vibrate_on_disconnect";

    private static final long[] CONNECT_PATTERN = {0, 100};
    private static final long[] CALLWAITING_PATTERN = {0, 200, 300, 200};
    private static final long[] DISCONNECT_PATTERN = {0, 100, 200, 100};

    private static final AudioAttributes VIBRATION_ATTRIBUTES =
            new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                    .build();

    private final Context mContext;
    private final Vibrator mVibrator;

    // Tracks the currently active call so connect / disconnect transitions can be detected.
    private DialerCall mActiveCall;
    // Tracks the current call-waiting call so it is only signalled once.
    private DialerCall mWaitingCall;

    public InCallVibrationHandler(Context context) {
        mContext = context;
        mVibrator = context.getSystemService(Vibrator.class);
    }

    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
        // Use the active-or-background call so that merely holding a call (which clears the
        // active call) is not mistaken for a disconnect.
        DialerCall activeCall = callList.getActiveOrBackgroundCall();

        if (activeCall != null && mActiveCall == null) {
            // A call just connected.
            mActiveCall = activeCall;
            if (getSetting(KEY_VIBRATE_ON_CONNECT)) {
                vibrate(CONNECT_PATTERN);
            }
        } else if (activeCall == null && mActiveCall != null) {
            // The call disconnected.
            if (getSetting(KEY_VIBRATE_ON_DISCONNECT)) {
                vibrate(DISCONNECT_PATTERN);
            }
            mActiveCall = null;
        }

        // Detect a call-waiting call arriving while another call is in progress.
        DialerCall waitingCall = callList.getFirstCallWithState(DialerCallState.CALL_WAITING);
        if (waitingCall != null && mWaitingCall == null) {
            mWaitingCall = waitingCall;
            if (getSetting(KEY_VIBRATE_ON_CALLWAITING)) {
                vibrate(CALLWAITING_PATTERN);
            }
        } else if (waitingCall == null) {
            mWaitingCall = null;
        }
    }

    private boolean getSetting(String key) {
        return Settings.System.getInt(mContext.getContentResolver(), key, 0) != 0;
    }

    private void vibrate(long[] pattern) {
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            return;
        }
        LogUtil.d("InCallVibrationHandler.vibrate", "firing in-call vibration");
        mVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), VIBRATION_ATTRIBUTES);
    }
}
