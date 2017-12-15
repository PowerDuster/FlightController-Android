package com.bytasaur.flightcontroller;

import android.annotation.SuppressLint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static java.lang.Math.PI;
import static java.lang.Math.abs;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor sensor;
    private TextView textView;
    private DatagramSocket socket;
    private InetAddress ip;
    private int port=6666;
    private int sensorMode=SensorManager.SENSOR_DELAY_FASTEST;
    float[] rotationMatrix=new float[16];
    float[] deltaRotationMatrix=new float[16];
    float[] orientations=new float[3];
    float offYaw=0;
//    float offPitch=0
    float offRoll=0;
    private boolean isProbing=false;
    private boolean isConnected=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        if(sensorManager!=null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            sensorManager.registerListener(this, sensor, sensorMode);
        }
        else {
            Toast.makeText(this, "Orientation service not supported", Toast.LENGTH_LONG).show();
        }
        textView=findViewById(R.id.text_box);
        try {
            discoverServer(null);

            ip=InetAddress.getByName("192.168.1.105");
            socket=new DatagramSocket();
        }
        catch (Exception ex){
            ex.printStackTrace();
            textView.setText("Err");
        }
    }

    static float translateAngle(float angle) {
        if (angle == 0) {
            return 0;
        }
        if (((int)(angle/PI))%2 == 0) {
            return (float)(angle%PI);
        }
        return (float)(angle%PI-((int)(abs(angle)/angle)*PI));
    }

    @SuppressLint("SetTextI18n")    // Everyone knows English STFU
    @Override
    public void onSensorChanged(SensorEvent event) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, deltaRotationMatrix);
        SensorManager.getOrientation(deltaRotationMatrix, orientations);
        new Thread() {
            @Override
            public void run() {
                try {
                    float nYaw=translateAngle(translateAngle(orientations[0])- offYaw);
//                    float nPitch=translateAngle(translateAngle(orientations[1])- offPitch);
                    float nRoll=translateAngle(translateAngle(orientations[2])- offRoll);
//                    String data = Math.toDegrees(nYaw)+","+Math.toDegrees(nPitch)+","+Math.toDegrees(nRoll)+"\n";
                    String data = Math.toDegrees(nYaw)+","+Math.toDegrees(nRoll);
                    socket.send(new DatagramPacket(data.getBytes(), data.length(), ip, port));
                }
                catch (Exception ex){
                    ex.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText(orientations[0]+"\n"+orientations[1]+"\n"+orientations[2]);
                    }
                });
            }
        }.start();
    }

    public void center(View v) {
        offYaw =translateAngle(orientations[0]);
//        offPitch =translateAngle(orientations[1]);
        offRoll =translateAngle(orientations[2]);
    }

    public void discoverServer(View v) {
        new Thread() {
            @Override
            public void run() {
                if(isProbing) {
                    return;
                }
                try {
                    DatagramSocket broadcastSocket=new DatagramSocket();
                    broadcastSocket.setBroadcast(true);
                    DatagramPacket packet=new DatagramPacket(new byte[1], 1, InetAddress.getByName("255.255.255.255"), 7777);
                    broadcastSocket.send(packet);
                    broadcastSocket.setSoTimeout(3000);
                    packet=new DatagramPacket(new byte[1], 1);
                    isProbing=true;
                    broadcastSocket.receive(packet);
                    ip=packet.getAddress();
                    isConnected=true;
                }
                catch(Exception ignored) {}
                isProbing=false;
            }
        }.start();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}

    @Override
    protected void onPause() {
        //if(sensor!=null) {
        sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onResume() {
        sensorManager.registerListener(this, sensor, sensorMode);
        super.onResume();
    }
}
