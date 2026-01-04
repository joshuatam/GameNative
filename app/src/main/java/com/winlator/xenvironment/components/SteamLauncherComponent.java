package com.winlator.xenvironment.components;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import com.winlator.core.ProcessHelper;
import com.winlator.xenvironment.EnvironmentComponent;
import com.winlator.xenvironment.ImageFs;

import java.io.File;

public class SteamLauncherComponent extends EnvironmentComponent {

    private static int pid = -1;

    private static final Object lock = new Object();

    @Override
    public void start() {
        synchronized (lock) {
            pid = execProgram();
            Log.d("SteamLauncherComponent", "Process " + pid + " started");
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (pid != -1) {
                Process.killProcess(pid);
                Log.d("SteamLauncherComponent", "Stopped process " + pid);
            }
        }
    }

    private int execProgram() {
        Context context = environment.getContext();
        ImageFs imageFs = ImageFs.find(context);
        String winePath = imageFs.getWinePath() + "/bin/";
        File rootDir = imageFs.getRootDir();

        final String command = winePath + "wine start /b \"C:\\\\Program Files (x86)\\\\Steam\\\\steam.exe\"";

        return ProcessHelper.exec(command, null, rootDir, (status) -> {
            synchronized (lock) {
                pid = -1;
            }
        });
    }
}
