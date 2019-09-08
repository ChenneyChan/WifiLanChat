package com.chenney.wifip2pchart.LinkManager;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.net.Socket;

public class TcpClient{
    private static final String TAG = "TcpClient";
    private Socket sock;
    private SockSendPktThread sendPktThread = null;
    private SockReceivePktThread receivePktThread = null;

    public TcpClient(Socket sock) {
        this.sock = sock;
    }

    public void startServer(@NonNull SockReceivePktThread.SockReceivePktCallBack callBack) {
        Log.i(TAG, "startServer: ");
        try {
            sendPktThread = new SockSendPktThread(sock.getOutputStream());
            sendPktThread.start();

            receivePktThread = new SockReceivePktThread(sock.getInputStream(), callBack);
            receivePktThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        Log.i(TAG, "close: ");

        if (sendPktThread != null)
            sendPktThread.clear();

        if (receivePktThread != null)
            receivePktThread.clear();

        if (sock != null) {
            try {
                sock.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendData(byte[] data) {
        if (sendPktThread != null) {
            sendPktThread.sendData(data);
        }
    }

    public boolean isConnect() {
        try{
            sock.sendUrgentData(0xFF);//发送1个字节的紧急数据，默认情况下，服务器端没有开启紧急数据处理，不影响正常通信
            return true;
        }catch(Exception se){
            Log.i(TAG, "isConnect: " + se.toString());
            return false;
        }
    }
}
