package com.chenney.wifip2pchart.LinkManager;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpServer extends Thread {
    private static final String TAG = "TcpServer";
    private int mListenPort;
    private TcpConnectCallBack callBack;
    private ServerSocket ss = null;


    public TcpServer(int listenPort, @NonNull TcpConnectCallBack cb) {
        mListenPort = listenPort;
        callBack = cb;
    }

    @Override
    public void run() {
        super.run();
        try {
            Log.i(TAG, "run: start listen on port " + mListenPort);
            ss = new ServerSocket(mListenPort);
            Socket sock = ss.accept();
            Log.i(TAG, "run: client connect " + sock.getInetAddress().getHostAddress());
            callBack.onTcpClientConnect(sock);
        } catch (IOException e) {
            callBack.onServerError(e);
        } finally {
            close();
            Log.i(TAG, "run: end of thread");
        }
    }

    public void close() {
        Log.i(TAG, "close: ");
        if (ss != null) {
            try {
                ss.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public interface TcpConnectCallBack {
        void onTcpClientConnect(Socket clientSocket);
        void onServerError(Exception e);
    }

}
