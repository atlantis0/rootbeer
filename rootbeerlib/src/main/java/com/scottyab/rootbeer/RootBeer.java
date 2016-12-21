package com.scottyab.rootbeer;

import android.content.Context;
import android.content.pm.PackageManager;

import com.scottyab.rootbeer.util.QLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * A simple root checker that gives an *indication* if the device is rooted or not.
 * Disclaimer: **root==god**, so there's no 100% way to check for root.
 */
public class RootBeer {

    final Context mContext;
    private boolean loggingEnabled = true;

    public RootBeer(Context context) {
        mContext = context;
    }

    /**
     * Run all the checks
     * @return true, we think there's a good *indication* of root | false good *indication* of no root (could still be cloaked)
     */
    public boolean isRooted() {
        boolean rootManagement = detectRootManagementApps();
        boolean potentiallyDangerousApps = detectPotentiallyDangerousApps();
        boolean suBinary = checkForBinary("su");
        boolean busyboxBinary = checkForBinary("busybox");
        boolean dangerousProps = checkForDangerousProps();
        boolean rwSystem = checkForRWPaths();
        boolean testKeys = detectTestKeys();
        boolean testSuExists = checkSuExists();
        boolean testRootNative = checkForRootNative();

        return rootManagement || potentiallyDangerousApps || suBinary
                || busyboxBinary || dangerousProps || rwSystem || testKeys || testSuExists || testRootNative;
    }

    /**
     * Release-Keys and Test-Keys has to do with how the kernel is signed when it is compiled.
     * Test-Keys means it was signed with a custom key generated by a third-party developer.
     * @return true if signed with Test-keys
     */
    public boolean detectTestKeys() {
        String buildTags = android.os.Build.TAGS;

        if (buildTags != null && buildTags.contains("test-keys")) {
            return true;
        }
        return false;
    }

    /**
     * Using the PackageManager, check for a list of well known root apps. @link {Const.knownRootAppsPackages}
     * @return true if one of the apps it's installed
     */
    public boolean detectRootManagementApps() {
        return detectRootManagementApps(null);
    }

    /**
     * Using the PackageManager, check for a list of well known root apps. @link {Const.knownRootAppsPackages}
     * @param additionalRootManagementApps - array of additional packagenames to search for
     * @return true if one of the apps it's installed
     */
    public boolean detectRootManagementApps(String[] additionalRootManagementApps) {

        // Create a list of package names to iterate over from constants any others provided
        ArrayList<String> packages = new ArrayList<>();
        packages.addAll(Arrays.asList(Const.knownRootAppsPackages));
        if (additionalRootManagementApps!=null && additionalRootManagementApps.length>0){
            packages.addAll(Arrays.asList(additionalRootManagementApps));
        }

        return isAnyPackageFromListInstalled(packages);
    }

    /**
     * Using the PackageManager, check for a list of well known apps that require root. @link {Const.knownRootAppsPackages}
     * @return true if one of the apps it's installed
     */
    public boolean detectPotentiallyDangerousApps() {
        return detectPotentiallyDangerousApps(null);
    }

    /**
     * Using the PackageManager, check for a list of well known apps that require root. @link {Const.knownRootAppsPackages}
     * @param additionalDangerousApps - array of additional packagenames to search for
     * @return true if one of the apps it's installed
     */
    public boolean detectPotentiallyDangerousApps(String[] additionalDangerousApps) {

        // Create a list of package names to iterate over from constants any others provided
        ArrayList<String> packages = new ArrayList<>();
        packages.addAll(Arrays.asList(Const.knownDangerousAppsPackages));
        if (additionalDangerousApps!=null && additionalDangerousApps.length>0){
            packages.addAll(Arrays.asList(additionalDangerousApps));
        }

        return isAnyPackageFromListInstalled(packages);
    }

    /**
     * Using the PackageManager, check for a list of well known root cloak apps. @link {Const.knownRootAppsPackages}
     * @return true if one of the apps it's installed
     */
    public boolean detectRootCloakingApps() {
        return detectRootCloakingApps(null);
    }

    /**
     * Using the PackageManager, check for a list of well known root cloak apps. @link {Const.knownRootAppsPackages}
     * @param additionalRootCloakingApps - array of additional packagenames to search for
     * @return true if one of the apps it's installed
     */
    public boolean detectRootCloakingApps(String[] additionalRootCloakingApps) {

        // Create a list of package names to iterate over from constants any others provided
        ArrayList<String> packages = new ArrayList<>();
        packages.addAll(Arrays.asList(Const.knownRootCloakingPackages));
        if (additionalRootCloakingApps!=null && additionalRootCloakingApps.length>0){
            packages.addAll(Arrays.asList(additionalRootCloakingApps));
        }

        return isAnyPackageFromListInstalled(packages);
    }


    /**
     * Checks various (Const.suPaths) common locations for the SU binary
     * @return true if found
     */
    public boolean checkForSuBinary(){
        return checkForBinary("su");
    }

    /**
     * Checks various (Const.suPaths) common locations for the busybox binary (a well know root level program)
     * @return true if found
     */
    public boolean checkForBusyBoxBinary(){
        return checkForBinary("busybox");
    }

    /**
     *
     * @param filename - check for this existence of this file
     * @return true if found
     */
    public boolean checkForBinary(String filename) {

        String[] pathsArray = Const.suPaths;

        boolean result = false;

        for (String path : pathsArray) {
            String completePath = path + filename;
            File f = new File(completePath);
            boolean fileExists = f.exists();
            if (fileExists) {
                QLog.v(completePath + " binary detected!");
                result = true;
            }
        }

        return result;
    }

    /**
     *
     * @param logging - set to true for logging
     */
    public void setLogging(boolean logging) {
        loggingEnabled = logging;
        QLog.LOGGING_LEVEL = logging ? QLog.ALL : QLog.NONE;
    }

    private String[] propsReader() {
        InputStream inputstream = null;
        try {
            inputstream = Runtime.getRuntime().exec("getprop").getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String propval = "";
        try {
            propval = new Scanner(inputstream).useDelimiter("\\A").next();

        } catch (NoSuchElementException e) {
            QLog.e("Error getprop, NoSuchElementException: " +e.getMessage(), e);
        }

        return propval.split("\n");
    }

    private String[] mountReader() {
        InputStream inputstream = null;
        try {
            inputstream = Runtime.getRuntime().exec("mount").getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // If input steam is null, we can't read the file, so return null
        if (inputstream == null) return null;

        String propval = "";
        try {
            propval = new Scanner(inputstream).useDelimiter("\\A").next();
        } catch (NoSuchElementException e) {
            e.printStackTrace();
        }

        return propval.split("\n");
    }

    /**
     * Check if any package in the list is installed
     * @param packages - list of packages to search for
     * @return true if any of the packages are installed
     */
    private boolean isAnyPackageFromListInstalled(List<String> packages){
        boolean result = false;

        PackageManager pm = mContext.getPackageManager();

        for (String packageName : packages) {
            try {
                // Root app detected
                pm.getPackageInfo(packageName, 0);
                QLog.e(packageName + " ROOT management app detected!");
                result = true;
            } catch (PackageManager.NameNotFoundException e) {
                // Exception thrown, package is not installed into the system
                continue;
            }
        }

        return result;
    }

    /**
     * Checks for several system properties for
     * @return
     */
    public boolean checkForDangerousProps() {

        final Map<String, String> dangerousProps = new HashMap<String, String>();
            dangerousProps.put("ro.debuggable", "1");
            dangerousProps.put("ro.secure", "0");

        boolean result = false;

        String[] lines = propsReader();
        for (String line : lines) {
            for (String key : dangerousProps.keySet()) {
                if (line.contains(key)) {
                    String badValue = dangerousProps.get(key);
                    badValue = "[" + badValue + "]";
                    if (line.contains(badValue)) {
                        QLog.v(key + " = " + badValue + " detected!");
                        result = true;
                    }
                }
            }
        }
        return result;
    }

    /**
     * When you're root you can change the permissions on common system directories, this method checks if any of these patha Const.pathsThatShouldNotBeWrtiable are writable.
     * @return true if one of the dir is writable
     */
    public boolean checkForRWPaths() {

        boolean result = false;

        String[] lines = mountReader();
        for (String line : lines) {

            // Split lines into parts
            String[] args = line.split(" ");

            if (args.length < 4){
                // If we don't have enough options per line, skip this and log an error
                QLog.e("Error formatting mount line: "+line);
                continue;
            }

            String mountPoint = args[1];
            String mountOptions = args[3];

            for(String pathToCheck: Const.pathsThatShouldNotBeWrtiable) {
                if (mountPoint.equalsIgnoreCase(pathToCheck)) {

                    // Split options out and compare against "rw" to avoid false positives
                    for (String option : mountOptions.split(",")){

                      if (option.equalsIgnoreCase("rw")){
                        QLog.v(pathToCheck+" path is mounted with rw permissions! "+line);
                        result = true;
                        break;
                      }
                    }
                }
            }
        }

        return result;
    }


    /**
     * A variation on the checking for SU, this attempts a 'which su'
     * @return true if su found
     */
    public boolean checkSuExists() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[] { "/system/xbin/which", "su" });
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            if (in.readLine() != null) return true;
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    /**
     * Native checks are often harder to cloak/trick so here we call through to our native root checker
     * @return true if we found su | false if not
     */
    public boolean checkForRootNative() {

        String binaryName = "su";
        String[] paths = new String[Const.suPaths.length];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = Const.suPaths[i]+binaryName;
        }

        RootBeerNative rootBeerNative = new RootBeerNative();
        rootBeerNative.setLogDebugMessages(loggingEnabled);
        return rootBeerNative.checkForRoot(paths) > 0;
    }

}