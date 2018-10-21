package e3factory.com.microbitble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity{

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private MyScancallback scancallback;
    private BluetoothDevice device;
    private BluetoothGatt mGatt;
    private BluetoothGattCallback gattCallback;
    private BluetoothGattService mService;

    private final String ACCEL_SERVICE = "e95d0753-251d-470a-a062-fa1922dfa9a8";
    private final String ACCEL_DATA = "e95dca4b-251d-470a-a062-fa1922dfa9a8";
    private final String ACCEL_PERIOD = "e95dfb24-251d-470a-a062-fa1922dfa9a8";
    private final String NOTIFY_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb";

    private boolean mScanned = false;

    private final int PERMISSION_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //BLE対応端末かどうかを調べる。対応していない場合はメッセージを出して終了
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        //Bluetoothアダプターを初期化する
        BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();

        //bluetoothの使用が許可されていない場合は許可を求める。
        if( adapter == null || !adapter.isEnabled() ){
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent,PERMISSION_REQUEST);
        }
        else{
            Button button = findViewById(R.id.button_connect);
            button.setText("CONNECT");
            button.setEnabled(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if( requestCode == PERMISSION_REQUEST ){
            Button button = findViewById(R.id.button_connect);
            button.setEnabled(true);
        }
    }

    private Handler handler;
    private final int SCAN_PERIOD = 10000;

    class MyScancallback extends ScanCallback{
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d("scanResult","start");
            if( mScanned == true ) return;
            if( result.getDevice() == null ) return;
            if( result.getDevice().getName() == null )return;
            if( result.getDevice().getName().contains("BBC micro:bit") ){
                //BLE端末情報の保持
                device = result.getDevice();
                mScanned = true;
                //UIスレッドでボタン名称変更
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Button button = findViewById(R.id.button_connect);
                        button.setText("GETTING");
                        button.setEnabled(true);
                    }
                });
                //スキャン停止
                scanner.stopScan(scancallback);
                return;
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
        }

        @Override
        public void onScanFailed(int errorCode) {
        }
    }

    class MyGattcallback extends BluetoothGattCallback{
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d("onConnect","change");
            if( newState == BluetoothProfile.STATE_CONNECTED){
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mGatt = gatt;
            if( status == BluetoothGatt.GATT_SUCCESS ){
                Log.d("onServicesDiscovered","success");
                List<BluetoothGattService> list = gatt.getServices();
                for( BluetoothGattService service : list){
                    //加速度サービスの確保
                    if( service.getUuid().toString().equals(ACCEL_SERVICE) ) {
                        mService = service;
                        //速度落とす
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(ACCEL_PERIOD));
                        characteristic.setValue(160,BluetoothGattCharacteristic.FORMAT_UINT16,0);
                        //Descriptorの記述
                        characteristic = service.getCharacteristic(UUID.fromString(ACCEL_DATA));
                        mGatt.setCharacteristicNotification(characteristic,true);
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(NOTIFY_DESCRIPTOR));
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mGatt.writeDescriptor(descriptor);
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d("onCharacteristic","change");
            if( characteristic.getUuid().toString().equals(ACCEL_DATA) ){
                byte[] t = characteristic.getValue();
                Log.d("length:::","::"+t.length);
                Log.d("value:::",String.format("%x:%x:%x:%x:%x:%x",t[0],t[1],t[2],t[3],t[4],t[5]));
                final int x = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,0);
                final int y = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,2);
                final int z = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,4);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView textView = findViewById(R.id.accel_x);
                        textView.setText("加速度X:"+x);
                        textView = findViewById(R.id.accel_y);
                        textView.setText("加速度Y:"+y);
                        textView = findViewById(R.id.accel_z);
                        textView.setText("加速度Z:"+z);
                    }
                });
            }
        }
    }

    public void pushConnect(View view) {
        Button button = (Button) view;
        if (button.getText().equals("CONNECT")) {
            scanner = adapter.getBluetoothLeScanner();
            scancallback = new MyScancallback();

            //スキャニングを10秒後に停止
            handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanner.stopScan(scancallback);
                }
            }, SCAN_PERIOD);
            //スキャンの開始
            scanner.startScan(scancallback);
        }
        else if (button.getText().equals("GETTING")) {
            if (device != null) {
                gattCallback = new MyGattcallback();
                device.connectGatt(this, false, gattCallback);
            }
        }
    }
}
