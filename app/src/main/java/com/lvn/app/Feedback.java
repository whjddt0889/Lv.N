package com.lvn.app;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.speech.tts.TextToSpeech;

import com.lvn.app.engine.DriveEvent;

import java.util.Locale;

/** 주행 중 피드백은 화면이 아니라 소리로만: 짧은 효과음 + 중요한 순간에만 음성. */
public final class Feedback {
    private ToneGenerator tone;
    private TextToSpeech tts;
    private boolean ttsReady;
    private long lastToneAt;
    public boolean voiceOn = true;

    public Feedback(Context c) {
        try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 70); } catch (Throwable ignored) { }
        try {
            tts = new TextToSpeech(c.getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS) {
                    int r = tts.setLanguage(Locale.KOREAN);
                    ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                }
            });
        } catch (Throwable ignored) { }
    }

    public void onEvent(DriveEvent e) {
        long now = System.currentTimeMillis();
        int t;
        switch (e.kind) {
            case DriveEvent.SCORE: t = ToneGenerator.TONE_PROP_ACK; break;
            case DriveEvent.BONUS: t = ToneGenerator.TONE_PROP_BEEP2; break;
            case DriveEvent.RECORD: t = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD; break;
            case DriveEvent.WARN: t = ToneGenerator.TONE_PROP_NACK; break;
            default: t = -1;
        }
        if (t >= 0 && tone != null && now - lastToneAt > 180) {
            lastToneAt = now;
            try { tone.startTone(t, 160); } catch (Throwable ignored) { }
        }
        if (e.voice != null) say(e.voice);
    }

    public void say(String s) {
        if (!voiceOn || !ttsReady || tts == null) return;
        try { tts.speak(s, TextToSpeech.QUEUE_ADD, null, "lvn" + s.hashCode()); } catch (Throwable ignored) { }
    }

    public void levelUp(int level) {
        if (tone != null) try { tone.startTone(ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING, 600); } catch (Throwable ignored) { }
        say("레벨 업! 레벨 " + level);
    }

    public void release() {
        try { if (tts != null) tts.shutdown(); } catch (Throwable ignored) { }
        try { if (tone != null) tone.release(); } catch (Throwable ignored) { }
    }
}
