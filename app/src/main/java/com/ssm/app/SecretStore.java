package com.ssm.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecretStore {
    private static final String ALIAS="ssm_notion_key";
    private static final String PREFS="ssm_secure";
    private SecretStore(){}

    public static void saveNotionToken(Context c,String token) throws Exception {
        if(token==null||token.isBlank()){c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove("notion").apply();return;}
        SecretKey key=getOrCreateKey(); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key);
        byte[] enc=cipher.doFinal(token.trim().getBytes(StandardCharsets.UTF_8));
        String packed=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(enc,Base64.NO_WRAP);
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("notion",packed).apply();
    }

    public static String getNotionToken(Context c){
        String packed=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("notion",null); if(packed==null)return null;
        try{String[] parts=packed.split(":",2);SecretKey key=getOrCreateKey();Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}catch(Exception e){return null;}
    }

    public static boolean hasNotionToken(Context c){String t=getNotionToken(c);return t!=null&&!t.isBlank();}

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(ALIAS))return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return kg.generateKey();
    }
}
