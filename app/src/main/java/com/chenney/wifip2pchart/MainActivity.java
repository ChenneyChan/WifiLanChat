package com.chenney.wifip2pchart;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.chenney.wifip2pchart.LinkManager.SockReceivePktThread;
import com.chenney.wifip2pchart.LinkManager.TcpClient;
import com.chenney.wifip2pchart.LinkManager.TcpServer;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = "MyDemo";

    private SharedPreferences sharedPreferences = null;

    private Button startServer, startConnect, sendData, disConnect;
    private TextView portView, addrView, sendDataView, recvDataView, localInfo;
    private TcpServer tcpServer = null;
    private TcpClient tcpClient = null;
    private boolean isServer = false;
    private boolean isConnect = false;

    private static final String CFG_ADDR = "addr_cfg";
    private static final String CFG_PORT = "port_cfg";

    private static final long INTERVAL = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//remove title bar  即隐藏标题栏
        getSupportActionBar().hide();// 隐藏ActionBar
        setContentView(R.layout.activity_main);

        startConnect = findViewById(R.id.start_connect);
        startServer = findViewById(R.id.start_listen);
        sendData = findViewById(R.id.send_data);
        disConnect = findViewById(R.id.stop_connect);

        portView = findViewById(R.id.connnect_port);
        addrView = findViewById(R.id.peer_addr);
        sendDataView = findViewById(R.id.info_2_send);
        recvDataView = findViewById(R.id.rev_data);
        localInfo = findViewById(R.id.local_info);


        startConnect.setOnClickListener(this);
        startServer.setOnClickListener(this);
        sendData.setOnClickListener(this);
        disConnect.setOnClickListener(this);

        sendDataView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (i == KeyEvent.KEYCODE_ENTER && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    sendData.callOnClick();
                    return true;
                }
                return false;
            }
        });

        sendData.setEnabled(false);
        disConnect.setEnabled(false);

        mContext = this;

        sharedPreferences = getSharedPreferences("data", Context.MODE_PRIVATE);
        addrView.setText(sharedPreferences.getString(CFG_ADDR, "192.168.0.1"));
        portView.setText(sharedPreferences.getString(CFG_PORT, "1025"));
        handler.sendEmptyMessageDelayed(MSG_ON_INTERVAL, INTERVAL);

        String localIpAddress = getLocalIpAddress(this);
        localInfo.setText("本机地址：" + localIpAddress);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpServer != null)
            tcpServer.close();

        if (tcpClient != null)
            tcpClient.close();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.send_data:
                Log.i(TAG, "onClick: send data");
                if (isConnect && tcpClient != null) {
                    tcpClient.sendData(sendDataView.getText().toString().getBytes());
                    recvDataView.append("我：" + sendDataView.getText().toString() + "\n");
                }
                sendDataView.setText("");
                break;
            case R.id.start_connect:
                Log.i(TAG, "onClick: start connect");
                if (!isServer && !isConnect) {
                    clientStartConnect();
                }
                break;
            case R.id.start_listen:
                Log.i(TAG, "onClick: start listen");
                startServer();
                break;
            case R.id.stop_connect:
                Log.i(TAG, "onClick: stop connect");
                stopConnect();
                break;
        }
    }

    private void clientStartConnect() {
        saveCfg();
        new Thread() {
            @Override
            public void run() {
                super.run();
                String serverIp = addrView.getText().toString();
                int serverPort = Integer.valueOf(portView.getText().toString());
                try {
                    InetSocketAddress socketAddress = new InetSocketAddress(serverIp, serverPort);
                    Socket sock = new Socket();
                    sock.connect(socketAddress);
                    if (sock.isConnected()) {
                        Log.i(TAG, "run: connect ok");
                        Message msg = handler.obtainMessage();
                        msg.what = MSG_ON_CLIENT_CONNECT;
                        msg.obj = sock;
                        handler.sendMessage(msg);
                    }
                } catch (IOException e) {
                    showToast("connect fail as client " + e.toString());
                }
            }
        }.start();
    }

    private void startServer() {
        if (isServer)
            return;
        saveCfg();
        isServer = true;
        startServer.setEnabled(false);
        tcpServer = new TcpServer(Integer.valueOf(portView.getText().toString()), new TcpServer.TcpConnectCallBack() {
            @Override
            public void onTcpClientConnect(Socket clientSocket) {
                Message msg = handler.obtainMessage();
                msg.what = MSG_ON_SERVER_CONNECT;
                msg.arg1 = 1;
                msg.obj = clientSocket;
                handler.sendMessage(msg);
            }

            @Override
            public void onServerError(Exception e) {
                Message msg = handler.obtainMessage();
                msg.what = MSG_ON_SERVER_CONNECT;
                msg.arg1 = 0;
                handler.sendMessage(msg);
            }
        });
        tcpServer.start();
    }

    private void stopConnect() {
        if (isServer) {
            if (tcpServer != null) {
                tcpServer.close();
                tcpServer = null;
            }
            isServer = false;
        }
        if (isConnect) {
            if (tcpClient != null) {
                tcpClient.close();
                tcpClient = null;
            }
            isConnect = false;
            sendData.setEnabled(false);
            disConnect.setEnabled(false);
        }
        startServer.setEnabled(true);
        startConnect.setEnabled(true);
        disConnect.setEnabled(true);
        showToast("stop connect ok");
    }

    private SockReceivePktThread.SockReceivePktCallBack receivePktCallBack = new SockReceivePktThread.SockReceivePktCallBack() {
        @Override
        public void onReceivePkt(byte[] info, int realLen) {
            Log.i(TAG, "onReceivePkt: info len is " + info.length + " real len is " + realLen);
            byte[] val= new byte[realLen];
            System.arraycopy(info, 0, val, 0, realLen);
            Message msg = handler.obtainMessage();
            msg.what = MSG_ON_REC_DATA;
            msg.obj = new String(val);
            handler.sendMessage(msg);
        }
    };

    private Context mContext = null;
    private void showToast(final String info) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, info, Toast.LENGTH_SHORT).show();
            }
        });
    }


    private static final int MSG_ON_SERVER_CONNECT = 1;
    private static final int MSG_ON_CLIENT_CONNECT = 2;
    private static final int MSG_ON_REC_DATA = 3;
    private static final int MSG_ON_TOAST_INFO = 4;
    private static final int MSG_ON_INTERVAL = 5;

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            //Log.i(TAG, "handleMessage: " + msg.what);
            switch (msg.what) {
                case MSG_ON_SERVER_CONNECT:
                    if (1 == msg.arg1) {
                        startServer.setEnabled(false);
                        startConnect.setEnabled(false);
                        sendData.setEnabled(true);
                        disConnect.setEnabled(true);
                        tcpClient = new TcpClient((Socket) msg.obj);
                        tcpClient.startServer(receivePktCallBack);
                        isConnect = true;
                        showToast("connect ok as server");
                    } else {
                        tcpServer = null;
                        isConnect = false;
                        isServer =  false;
                        showToast("Tcp Server start error");
                        startServer.setEnabled(true);
                        startConnect.setEnabled(true);
                        disConnect.setEnabled(false);
                    }
                    break;
                case MSG_ON_CLIENT_CONNECT:
                    showToast("connect ok as client");
                    isServer = false;
                    isConnect = true;
                    tcpClient = new TcpClient((Socket)msg.obj);
                    tcpClient.startServer(receivePktCallBack);
                    startConnect.setEnabled(false);
                    sendData.setEnabled(true);
                    disConnect.setEnabled(true);
                    break;
                case MSG_ON_REC_DATA:
                    String info = (String)msg.obj;
                    recvDataView.append("朋友：" + info);
                    recvDataView.append("\n");
                    break;
                case MSG_ON_TOAST_INFO:
                    showToast((String)msg.obj);
                    break;
                case MSG_ON_INTERVAL:
                    if (tcpClient != null) {
                        //Log.i(TAG, "handleMessage: isconnect = " + tcpClient.isConnect());
                        if (!tcpClient.isConnect()) {
                            stopConnect();
                        }
                    }
                    sendEmptyMessageDelayed(MSG_ON_INTERVAL, INTERVAL);
                    break;
            }
        }
    };

    private void saveCfg() {
        SharedPreferences.Editor e = sharedPreferences.edit();
        e.putString(CFG_PORT, portView.getText().toString());
        e.putString(CFG_ADDR, addrView.getText().toString());
        e.apply();
    }

    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("WifiPreference IpAddress", ex.toString());
        }
        return null;
    }

    /**
     * 将ip的整数形式转换成ip形式
     *
     * @param ipInt
     * @return
     */
    public static String int2ip(int ipInt) {
        StringBuilder sb = new StringBuilder();
        sb.append(ipInt & 0xFF).append(".");
        sb.append((ipInt >> 8) & 0xFF).append(".");
        sb.append((ipInt >> 16) & 0xFF).append(".");
        sb.append((ipInt >> 24) & 0xFF);
        return sb.toString();
    }
    /**
     * 获取当前ip地址
     *
     * @param context
     * @return
     */
    public static String getLocalIpAddress(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context
                    .getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int i = wifiInfo.getIpAddress();
            return int2ip(i);
        } catch (Exception ex) {
            return null;
        }
    }
}
