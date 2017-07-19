package com.meraglove.meraglove;

import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.util.Log;

public class MainActivity extends Activity {

	private BluetoothAdapter mBluetoothAdapter = null;
	private static final int REQUEST_LOCATION = 0;
	private static String[] PERMISSIONS_LOCATION = {Manifest.permission.ACCESS_COARSE_LOCATION};
	private BluetoothLeScanner scanner = null;
	private boolean permissions_granted=false;
	private boolean mScanning = false;
	private Handler mHandler = new Handler();
	private ListAdapter mLeDeviceListAdapter;

	private static final long SCAN_TIMEOUT = 5000;

	static class ViewHolder {
		public TextView text;

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_main);

		// Initializes Bluetooth adapter.
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		mLeDeviceListAdapter = new ListAdapter();

		ListView listView = (ListView) this.findViewById(R.id.deviceList);
		listView.setAdapter(mLeDeviceListAdapter);

		listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                if (mScanning) {
                    setScanState(false);
                    scanner.stopScan(mLeScanCallback);
                }
                BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                Intent intent = new Intent(MainActivity.this, PeripheralControlActivity.class);
                intent.putExtra(PeripheralControlActivity.EXTRA_NAME, device.getName());
                intent.putExtra(PeripheralControlActivity.EXTRA_ID, device.getAddress());
                startActivity(intent);

            }
        });
	}

	public void onScan(View view) {
		// check bluetooth is available on on
		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(	BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivity(enableBtIntent);
			return;
		}
        Log.d(Constants.TAG, "Bluetooth is switched on");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions_granted = false;
                requestLocationPermission();
            } else {
                Log.i(Constants.TAG, "Location permission has already been granted. Starting scanning.");
                permissions_granted = true;
            }
        } else {
            // the ACCESS_COARSE_LOCATION permission did not exist before M so....
            permissions_granted = true;
        }
        if (permissions_granted) {
            if (!mScanning) {
                scanLeDevices();
            } else {
                setScanState(false);
                scanner.stopScan(mLeScanCallback);
            }
        }
	}

	private void setScanState(boolean value) {
		mScanning = value;
		((Button) this.findViewById(R.id.scanButton)).setText(value ? "Stop" : "Scan");
        if (mScanning) {
            showMsg("Scanning...");
        } else {
            showMsg("");
        }
	}

    private void requestLocationPermission() {
        Log.i(Constants.TAG, "Location permission has NOT yet been granted. Requesting permission.");
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)){
            Log.i(Constants.TAG, "Displaying location permission rationale to provide additional context.");
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Permission Required");
            builder.setMessage("Please grant Location access so this application can perform Bluetooth scanning");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    Log.d(Constants.TAG, "Requesting permissions after explanation");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
                }
            });
            builder.show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION) {
            Log.i(Constants.TAG, "Received response for location permission request.");
            // Check if the only required permission has been granted
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Location permission has been granted
                Log.i(Constants.TAG, "Location permission has now been granted. Scanning.....");
                permissions_granted = true;
                scanLeDevices();
            }else{
                Log.i(Constants.TAG, "Location permission was NOT granted.");
                showMsg("Required permissions not granted so cannot start");
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
	private void scanLeDevices() {
		scanner = mBluetoothAdapter.getBluetoothLeScanner();
		List<ScanFilter> filters;
		filters = new ArrayList<ScanFilter>();
		//ScanFilterFactory filter_factory = ScanFilterFactory.getInstance();
		//filters.add(filter_factory.getScanFilter());
		ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
		if (permissions_granted) {
            setScanState(true);
			scanner.startScan(filters, settings, mLeScanCallback);
		} else {
			Log.d(Constants.TAG,"Application lacks permission to start Bluetooth scanning");
            showMsg("Required permissions not granted so cannot start");
		}
	}

	private ScanCallback mLeScanCallback = new ScanCallback() {

		public void onScanResult(int callbackType, final ScanResult result) {

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mLeDeviceListAdapter.addDevice(result.getDevice());
					mLeDeviceListAdapter.notifyDataSetChanged();
				}
			});
		}
	};

    private void showMsg(String msg) {
        Log.d(Constants.TAG, msg);
        ((TextView) MainActivity.this.findViewById(R.id.msgTextView)).setText(msg);
    }

	// adaptor
	private class ListAdapter extends BaseAdapter {
		private ArrayList<BluetoothDevice> mLeDevices;

		public ListAdapter() {
			super();
			mLeDevices = new ArrayList<BluetoothDevice>();

		}

		public void addDevice(BluetoothDevice device) {
			if (!mLeDevices.contains(device)) {
				mLeDevices.add(device);
			}
		}

		public BluetoothDevice getDevice(int position) {
			return mLeDevices.get(position);
		}

		public void clear() {
			mLeDevices.clear();
		}

		@Override
		public int getCount() {
			return mLeDevices.size();
		}

		@Override
		public Object getItem(int i) {
			return mLeDevices.get(i);
		}

		@Override
		public long getItemId(int i) {
			return i;
		}

		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			ViewHolder viewHolder;
			// General ListView optimization code.
			if (view == null) {
				view = MainActivity.this.getLayoutInflater().inflate(
						R.layout.list_row, null);
				viewHolder = new ViewHolder();
				viewHolder.text = (TextView) view.findViewById(R.id.textView);
				view.setTag(viewHolder);
			} else {
				viewHolder = (ViewHolder) view.getTag();
			}
			BluetoothDevice device = mLeDevices.get(i);
			final String deviceName = device.getName();
			if (deviceName != null && deviceName.length() > 0)
				viewHolder.text.setText(deviceName);
			else
				viewHolder.text.setText("unknown device");

			return view;
		}
	}
}
