package com.chenney.wifip2pchart.LinkManager;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

public class SockReceivePktThread extends Thread {
    private static final String TAG = "SockReceivePktThread";
    private InputStream inputStream;
    private boolean isStart = false;
    private SockReceivePktCallBack receivePktCallBack;

    public SockReceivePktThread(InputStream ip, @NonNull SockReceivePktCallBack cb) {
        this.inputStream = ip;
        this.receivePktCallBack = cb;
    }

    @Override
    public void run() {
        super.run();
        byte[] data = new byte[1024];
        int len ;
        isStart = true;
        try {
            while (isStart) {
                while ((len = inputStream.read(data)) > -1) {
                    receivePktCallBack.onReceivePkt(data, len);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Log.i(TAG, "run: end of thread");
        }
    }

    public void clear() {
        isStart = false;
    }

    public interface SockReceivePktCallBack {
        void onReceivePkt(byte[]info, int realLen);
    }
}
