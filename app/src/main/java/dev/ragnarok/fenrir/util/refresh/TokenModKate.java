package dev.ragnarok.fenrir.util.refresh;

import static dev.ragnarok.fenrir.Constants.KATE_APP_VERSION_CODE;
import static dev.ragnarok.fenrir.Constants.KATE_APP_VERSION_NAME;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import dev.ragnarok.fenrir.Injection;
import dev.ragnarok.fenrir.api.ProxyUtil;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TokenModKate {
    private static final String agent = String.format("Android-GCM/1.5 (%s %s)", "Nexus 5", "Nexus 5");
    public static ArrayList<String> langs = new ArrayList<>();
    private static KeyPair pair;
    private static int rid;

    static {
        genNewKey();
    }

    public static String requestToken() {
        String str;
        String str2;
        try {
            System.out.println("Token register start");
            String[] strArr = {"4537286713832810256:3813922857350986999", "4607161437294568617:4436643741345745345", "4031819488942003867:1675892049294949499", "3665846370517392830:3012248377502379040"};
            String str3 = "AidLogin " + strArr[new Random().nextInt(strArr.length - 1)];
            String genNewKey = genNewKey();
            String sig = getSig(genNewKey);
            byte[] encoded = pair.getPublic().getEncoded();
            try {
                encoded = MessageDigest.getInstance("SHA1").digest(encoded);
                str = null;
            } catch (NoSuchAlgorithmException unused) {
                str = "";
            }
            if (str == null) {
                encoded[0] = (byte) (((encoded[0] & 15) + 112) & 255);
                str2 = Base64.encodeToString(encoded, 2).substring(0, 11);
            } else {
                str2 = str;
            }
            ArrayList<String> arrayList = new ArrayList<>();
            fillParams(arrayList, sig, genNewKey, str2, str3.split(" ")[1].split(":")[0]);
            String sb3 = doRequest("https://android.clients.google.com/c2dm/register3", arrayList, str3);
            if (sb3.contains("REGISTRATION_ERROR")) {
                System.out.println("Token register fail");
                return null;
            }
            System.out.println("Token register OK");
            return sb3.split("\\|ID\\|1\\|:")[1];
        } catch (Exception unused) {
            return null;
        }
    }

    private static void fillParams(List<String> list, String str, String str2, String str3, String device) {
        rid++;
        list.add("X-scope=GCM");
        list.add("X-subtype=54740537194");
        list.add("X-X-subscription=54740537194");
        list.add("X-X-subtype=54740537194");
        list.add("X-app_ver=" + KATE_APP_VERSION_CODE);
        list.add("X-kid=|ID|" + rid + "|");
        list.add("X-osv=23");
        list.add("X-sig=" + str);
        list.add("X-cliv=iid-12211000");
        list.add("X-gmsv=200313005");
        list.add("X-pub2=" + str2);
        list.add("X-X-kid=|ID|" + rid + "|");
        String sb = "X-appid=" +
                str3;
        list.add(sb);
        list.add("X-subscription=54740537194");
        list.add("X-app_ver_name=" + KATE_APP_VERSION_NAME);
        list.add("app=com.perm.kate_new_6");
        list.add("sender=54740537194");
        list.add("device=" + device);
        list.add("cert=966882ba564c2619d55d0a9afd4327a38c327456");
        list.add("app_ver=" + KATE_APP_VERSION_CODE);
        list.add("info=U_ojcf1ahbQaUO6eTSP7b7WomakK_hY");
        list.add("gcm_ver=200313005");
        list.add("plat=0");
        list.add("target_ver=28");
    }

    private static String join(String str, Iterable<String> iterable) {
        StringBuilder str2 = new StringBuilder();
        for (String next : iterable) {
            if (str2.length() == 0) {
                str2 = new StringBuilder(next);
            } else {
                str2.append(str).append(next);
            }
        }
        return str2.toString();
    }

    private static String join(String str, String[] strArr) {
        return join(str, Arrays.asList(strArr));
    }

    private static String doRequest(String str, List<String> list, String str3) throws IOException {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .addHeader("User-Agent", agent)
                        .addHeader("Authorization", str3)
                        .build()));
        ProxyUtil.applyProxyConfig(builder, Injection.provideProxySettings().getActiveProxy());
        FormBody.Builder formBody = new FormBody.Builder();
        for (String i : list) {
            String[] v = i.split("=");
            formBody.add(v[0], v[1]);
        }
        Request request = new Request.Builder()
                .url(str)
                .post(formBody.build())
                .build();
        Response response = builder.build().newCall(request).execute();
        return new BufferedReader(new InputStreamReader(response.body().byteStream())).readLine();
    }

    private static String genNewKey() {
        try {
            KeyPairGenerator instance = KeyPairGenerator.getInstance("RSA");
            instance.initialize(2048);
            pair = instance.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return Base64.encodeToString(pair.getPublic().getEncoded(), 0);
    }

    private static String getSig(String str) {
        try {
            PrivateKey privateKey = pair.getPrivate();
            Signature instance = Signature.getInstance(privateKey instanceof RSAPrivateKey ? "SHA256withRSA" : "SHA256withECDSA");
            instance.initSign(privateKey);
            instance.update(join("\n", new String[]{"com.perm.kate_new_6", str}).getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(instance.sign(), 0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
