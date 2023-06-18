package com.example.sos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnTokenCanceledListener;
import com.raghav.sos.R;

import java.util.List;

import Contacts.ContactModel;
import Contacts.CustomAdapter;
import Contacts.DbHelper;
import ShakeServices.ReactivateService;
import ShakeServices.SensorService;

public class MainActivity extends AppCompatActivity {

    private static final int IGNORE_BATTERY_OPTIMIZATION_REQUEST = 1002;
    private static final int PICK_CONTACT = 1;

    // create instances of various classes to be used
    Button button1;
    Button sosbutton;
    ListView listView;
    DbHelper db;
    List<ContactModel> list;
    CustomAdapter customAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // check for runtime permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS}, 100);
            }
        }

        // this is a special permission required only by devices using
        // Android Q and above. The Access Background Permission is responsible
        // for populating the dialog with "ALLOW ALL THE TIME" option
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 100);
        }

        // check for BatteryOptimization,
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                askIgnoreOptimization();
            }
        }

        // start the service
        SensorService sensorService = new SensorService();
        Intent intent = new Intent(this, sensorService.getClass());
        if (!isMyServiceRunning(sensorService.getClass())) {
            startService(intent);
        }


        button1 = findViewById(R.id.Button1);
        Button ambulance_button = findViewById(R.id.button2);
        Button fire_button = findViewById(R.id.button3);
        sosbutton = findViewById(R.id.Button5);
        listView = (ListView) findViewById(R.id.ListView);
        db = new DbHelper(this);
        list = db.getAllContacts();
        customAdapter = new CustomAdapter(this, list);
        listView.setAdapter(customAdapter);

        button1.setOnClickListener(new View.OnClickListener() {
                                       @Override
                                       public void onClick(View v) {
                                           // calling of getContacts()
                                           if (db.count() != 5) {
                                               Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                                               startActivityForResult(intent, PICK_CONTACT);
                                           } else {
                                               Toast.makeText(MainActivity.this, "Can't Add more than 5 Contacts", Toast.LENGTH_SHORT).show();
                                           }
                                       }
                                   }

        );



    }

    // method to check if the service is running
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.i("Service status", "Running");
                return true;
            }
        }
        Log.i("Service status", "Not running");
        return false;
    }

    @Override
    protected void onDestroy() {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, ReactivateService.class);
        this.sendBroadcast(broadcastIntent);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "Permissions Denied!\n Can't use the App!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // get the contact from the PhoneBook of device
        switch (requestCode) {
            case (PICK_CONTACT):
                if (resultCode == Activity.RESULT_OK) {

                    Uri contactData = data.getData();
                    Cursor c = managedQuery(contactData, null, null, null, null);
                    if (c.moveToFirst()) {

                        String id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                        String hasPhone = c.getString(c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER));
                        String phone = null;
                        try {
                            if (hasPhone.equalsIgnoreCase("1")) {
                                Cursor phones = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + id, null, null);
                                phones.moveToFirst();
                                phone = phones.getString(phones.getColumnIndex("data1"));
                            }
                            String name = c.getString(c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                            db.addcontact(new ContactModel(0, name, phone));
                            list = db.getAllContacts();
                            customAdapter.refresh(list);
                        } catch (Exception ex) {
                        }
                    }
                }
                break;
        }
    }

    // this method prompts the user to remove any
    // battery optimisation constraints from the App
    private void askIgnoreOptimization() {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            @SuppressLint("BatteryLife") Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, IGNORE_BATTERY_OPTIMIZATION_REQUEST);
        }

    }

    public void SosClick(View view) {//police
        Toast.makeText(this, "SOS Message sent!", Toast.LENGTH_SHORT).show();
        //FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());

        // use the PRIORITY_BALANCED_POWER_ACCURACY
        // so that the service doesn't use unnecessary power via GPS
        // it will only use GPS at this very moment
        fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, new CancellationToken() {
            @Override
            public boolean isCancellationRequested() {
                return false;
            }

            @NonNull
            @Override
            public CancellationToken onCanceledRequested(@NonNull OnTokenCanceledListener onTokenCanceledListener) {
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                // check if location is null
                // for both the cases we will
                // create different messages

                //https://www.google.com/maps/search/police+station+near+me/@
                if (location != null) {

                    // get the SMSManager
                    SmsManager smsManager = SmsManager.getDefault();

                    // get the list of all the contacts in Database
                    DbHelper db = new DbHelper(MainActivity.this);
                    List<ContactModel> list = db.getAllContacts();

                    // send SMS to each contact
                    for (ContactModel c : list) {
                        String message = "Hey, " + c.getName() + " I am in DANGER, i need help. Call the POLICE. Here are my coordinates.\n " + "http://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
                        smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                    }
                    Uri uri = Uri.parse("https://www.google.com/maps/search/police+station+near+me/@"+ location.getLatitude() + "," + location.getLongitude());
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                } else {
                    String message = " I am in DANGER, i need help. Call the POLICE.\n" + "GPS was turned off.Couldn't find location. Call your nearest Police Station.";
                    SmsManager smsManager = SmsManager.getDefault();
                    DbHelper db = new DbHelper(MainActivity.this);
                    List<ContactModel> list = db.getAllContacts();
                    for (ContactModel c : list) {
                        smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                    }
                    Uri uri = Uri.parse("https://www.google.com/maps/search/police+station+near+me");
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("Check: ", "OnFailure");
                String message = " I am in DANGER, i need help. Call the POLICE.\n" + "GPS was turned off.Couldn't find location. Call your nearest Police Station.";
                SmsManager smsManager = SmsManager.getDefault();
                DbHelper db = new DbHelper(MainActivity.this);
                List<ContactModel> list = db.getAllContacts();
                for (ContactModel c : list) {
                    smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                }
                Uri uri = Uri.parse("https://www.google.com/maps/search/police+station+near+me");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        });

    }


    public void SosClick1(View view) {//ambulance
        Toast.makeText(this, "SOS Message sent!", Toast.LENGTH_SHORT).show();
        //FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());

        // use the PRIORITY_BALANCED_POWER_ACCURACY
        // so that the service doesn't use unnecessary power via GPS
        // it will only use GPS at this very moment
        fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, new CancellationToken() {
            @Override
            public boolean isCancellationRequested() {
                return false;
            }

            @NonNull
            @Override
            public CancellationToken onCanceledRequested(@NonNull OnTokenCanceledListener onTokenCanceledListener) {
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                // check if location is null
                // for both the cases we will
                // create different messages
                if (location != null) {

                    // get the SMSManager
                    SmsManager smsManager = SmsManager.getDefault();

                    // get the list of all the contacts in Database
                    DbHelper db = new DbHelper(MainActivity.this);
                    List<ContactModel> list = db.getAllContacts();

                    // send SMS to each contact
                    for (ContactModel c : list) {
                        String message = "Hey, " + c.getName() + " I am in DANGER, i need help. Call an Ambulance. Here are my coordinates.\n " + "http://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
                        smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                    }
                    Uri uri = Uri.parse("https://www.google.com/maps/search/hospital+near+me/@"+ location.getLatitude() + "," + location.getLongitude());
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                } else {
                    String message = " I am in DANGER, i need help. Call an Ambulance.\n" + "GPS was turned off.Couldn't find location. Call your nearest Police Station.";
                    SmsManager smsManager = SmsManager.getDefault();
                    DbHelper db = new DbHelper(MainActivity.this);
                    List<ContactModel> list = db.getAllContacts();
                    for (ContactModel c : list) {
                        smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                    }
                    Uri uri = Uri.parse("https://www.google.com/maps/search/hospital+near+me");
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("Check: ", "OnFailure");
                String message = " I am in DANGER, i need help. Call an Ambulance.\n" + "GPS was turned off.Couldn't find location. Call your nearest Police Station.";
                SmsManager smsManager = SmsManager.getDefault();
                DbHelper db = new DbHelper(MainActivity.this);
                List<ContactModel> list = db.getAllContacts();
                for (ContactModel c : list) {
                    smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                }
                Uri uri = Uri.parse("https://www.google.com/maps/search/hospital+near+me");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        });

    }

    public void SosClick2(View view) {//fire
        Toast.makeText(this, "SOS Message sent!", Toast.LENGTH_SHORT).show();
        //FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());

        // use the PRIORITY_BALANCED_POWER_ACCURACY
        // so that the service doesn't use unnecessary power via GPS
        // it will only use GPS at this very moment
        fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, new CancellationToken() {
            @Override
            public boolean isCancellationRequested() {
                return false;
            }

            @NonNull
            @Override
            public CancellationToken onCanceledRequested(@NonNull OnTokenCanceledListener onTokenCanceledListener) {
                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                // check if location is null
                // for both the cases we will
                // create different messages
                if (location != null) {

                    // get the SMSManager
                    SmsManager smsManager = SmsManager.getDefault();

                    // get the list of all the contacts in Database
                    DbHelper db = new DbHelper(MainActivity.this);
                    List<ContactModel> list = db.getAllContacts();

                    // send SMS to each contact
                    for (ContactModel c : list) {
                        String message = "Hey, " + c.getName() + " I am in DANGER, i need help. Call the FIRE BRIGADE. Here are my coordinates.\n " + "http://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
                        smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                    }
                    Uri uri = Uri.parse("https://www.google.com/maps/search/fire+station+near+me/@"+ location.getLatitude() + "," + location.getLongitude());
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                } else {
                    String message = " I am in DANGER, i need help. Call the FIRE BRIGADE.\n" + "GPS was turned off.Couldn't find location. Call your nearest Police Station.";
                    SmsManager smsManager = SmsManager.getDefault();
                    DbHelper db = new DbHelper(MainActivity.this);
                    List<ContactModel> list = db.getAllContacts();
                    for (ContactModel c : list) {
                        smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                    }
                    Uri uri = Uri.parse("https://www.google.com/maps/search/fire+station+near+me");
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("Check: ", "OnFailure");
                String message = " I am in DANGER, i need help. Call the FIRE BRIGADE.\n" + "GPS was turned off.Couldn't find location. Call your nearest Police Station.";
                SmsManager smsManager = SmsManager.getDefault();
                DbHelper db = new DbHelper(MainActivity.this);
                List<ContactModel> list = db.getAllContacts();
                for (ContactModel c : list) {
                    smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                }
                Uri uri = Uri.parse("https://www.google.com/maps/search/fire+station+near+me");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        });

    }
}
