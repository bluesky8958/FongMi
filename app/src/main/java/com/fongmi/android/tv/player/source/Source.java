package com.fongmi.android.tv.player.source;

import android.net.Uri;

public class Source {

    private static String getScheme(Uri uri) {
        return uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
    }

    private static boolean isHttp(Uri uri) {
        return getScheme(uri).startsWith("http");
    }

    private static boolean isForce(Uri uri) {
        return getScheme(uri).startsWith("p") || getScheme(uri).equals("mitv");
    }

    private static boolean isZLive(Uri uri) {
        return getScheme(uri).startsWith("zlive");
    }

    private static boolean isTVBus(Uri uri) {
        return getScheme(uri).startsWith("tvbus");
    }

    private static boolean isYoutube(Uri uri) {
        return uri.getHost().contains("youtube.com");
    }

    private static boolean isBiliBili(Uri uri) {
        return uri.getHost().equals("live.bilibili.com");
    }

    private static boolean isJianPian(Uri uri) {
        return getScheme(uri).equals("tvbox-xg");
    }

    public static String getUrl(String url) throws Exception {
        Uri uri = Uri.parse(url);
        if (isHttp(uri)) {
            if (isYoutube(uri)) return Youtube.get().fetch(url);
            else if (isBiliBili(uri)) return BiliBili.get().fetch(url);
            else return url;
        } else {
            if (isForce(uri)) return Force.get().fetch(url);
            else if (isZLive(uri)) return ZLive.get().fetch(url);
            else if (isTVBus(uri)) return TVBus.get().fetch(url);
            else if (isJianPian(uri)) return JianPian.get().fetch(url);
            else return url;
        }
    }

    public static void stop() {
        TVBus.get().stop();
        JianPian.get().stop();
    }

    public static void stopAll() {
        Force.get().stop();
        ZLive.get().stop();
        TVBus.get().stop();
        JianPian.get().stop();
    }
}
