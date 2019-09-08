package com.chenney.wifip2pchart.LinkManager;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class SockSendPktThread extends Thread {
    private static final String TAG = "SockSendPktThread";
    private OutputStream outputStream;
    private boolean isStart = false;
    private BlockingDeque<byte[]> sendQuene = new LinkedBlockingDeque<>();

    public SockSendPktThread(@NonNull OutputStream op) {
        outputStream = op;
    }

    @Override
    public void run() {
        super.run();
        isStart = true;
        try {
            while (isStart) {
                byte[] val = sendQuene.take();
                if (val != null) {
                    Log.i(TAG, "run: send byte len " + val.length);
                    outputStream.write(val);
                    outputStream.flush();
                }
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        } finally {
            Log.i(TAG, "run: end of thread");
        }
    }

    public void clear() {
        isStart = false;
    }

    public void sendData(byte[] info) {
        sendQuene.add(info);
    }
}
