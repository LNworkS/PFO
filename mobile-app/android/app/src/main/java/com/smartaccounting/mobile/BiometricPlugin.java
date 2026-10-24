package com.smartaccounting.mobile;

import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 指纹解锁插件：用 Android Keystore 保管网页端的数据密钥。
 * 密钥标记为「每次使用都需指纹验证」，只有指纹通过后才能加解密，
 * 因此 SharedPreferences 里只存在密文，无法离线还原。
 */
@CapacitorPlugin(name = "Biometric")
public class BiometricPlugin extends Plugin {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "pfo_biometric_key";
    private static final String PREFS_NAME = "pfo_biometric";
    private static final String PREF_BLOB = "secret_blob";
    private static final String PREF_IV = "secret_iv";
    private static final int GCM_TAG_BITS = 128;

    /** 同一时间只允许一个指纹弹窗 */
    private boolean prompting = false;

    private interface CipherHandler {
        void onSuccess(Cipher cipher) throws Exception;
    }

    private SharedPreferences getPrefs() {
        return getContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject ret = new JSObject();
        boolean available = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                int code = BiometricManager.from(getContext())
                        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
                available = code == BiometricManager.BIOMETRIC_SUCCESS;
            } catch (Exception ignored) {
                available = false;
            }
        }
        ret.put("available", available);
        call.resolve(ret);
    }

    @PluginMethod
    public void isEnabled(PluginCall call) {
        SharedPreferences prefs = getPrefs();
        boolean enabled = prefs.contains(PREF_BLOB) && prefs.contains(PREF_IV) && loadKey() != null;
        // 密钥已失效（如指纹重新录入）时清理残留密文，避免界面显示「已开启」却无法解锁
        if (!enabled && prefs.contains(PREF_BLOB)) {
            prefs.edit().clear().apply();
        }
        JSObject ret = new JSObject();
        ret.put("enabled", enabled);
        call.resolve(ret);
    }

    /** 保存数据密钥：创建指纹密钥 → 验证指纹 → 用验证后的 Cipher 加密并落库 */
    @PluginMethod
    public void enable(final PluginCall call) {
        final String secret = call.getString("secret");
        if (secret == null || secret.isEmpty()) {
            call.reject("缺少待保管的密钥", "NO_SECRET");
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            call.reject("系统版本不支持指纹解锁", "UNSUPPORTED");
            return;
        }
        Cipher cipher;
        try {
            deleteKey();
            SecretKey key = createKey();
            cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
        } catch (Exception e) {
            deleteKey();
            call.reject("无法创建指纹密钥：" + e.getMessage(), "KEY_ERROR");
            return;
        }
        showPrompt(call, cipher, "开启指纹解锁", "验证指纹以保管访问密钥", new CipherHandler() {
            @Override
            public void onSuccess(Cipher c) throws Exception {
                try {
                    byte[] encrypted = c.doFinal(secret.getBytes(StandardCharsets.UTF_8));
                    getPrefs().edit()
                            .putString(PREF_BLOB, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                            .putString(PREF_IV, Base64.encodeToString(c.getIV(), Base64.NO_WRAP))
                            .apply();
                    JSObject ret = new JSObject();
                    ret.put("ok", true);
                    call.resolve(ret);
                } catch (Exception e) {
                    deleteKey();
                    getPrefs().edit().clear().apply();
                    call.reject("保存密钥失败：" + e.getMessage(), "AUTH_ERROR");
                }
            }
        });
    }

    /** 取回数据密钥：读取密文 → 验证指纹 → 解密返回明文 base64 */
    @PluginMethod
    public void unlock(final PluginCall call) {
        SharedPreferences prefs = getPrefs();
        String blob = prefs.getString(PREF_BLOB, null);
        String ivText = prefs.getString(PREF_IV, null);
        if (blob == null || ivText == null) {
            call.reject("尚未开启指纹解锁", "NOT_ENABLED");
            return;
        }
        Cipher cipher;
        final byte[] encrypted;
        try {
            SecretKey key = loadKey();
            if (key == null) {
                clearAll();
                call.reject("指纹密钥已失效", "KEY_INVALID");
                return;
            }
            encrypted = Base64.decode(blob, Base64.NO_WRAP);
            cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivText, Base64.NO_WRAP)));
        } catch (KeyPermanentlyInvalidatedException e) {
            clearAll();
            call.reject("指纹信息已变更，请用密码解锁后重新开启", "KEY_INVALID");
            return;
        } catch (Exception e) {
            if (e.getCause() instanceof KeyPermanentlyInvalidatedException) {
                clearAll();
                call.reject("指纹信息已变更，请用密码解锁后重新开启", "KEY_INVALID");
                return;
            }
            call.reject("指纹解锁失败：" + e.getMessage(), "AUTH_ERROR");
            return;
        }
        showPrompt(call, cipher, "指纹解锁", "验证指纹以解锁应用", new CipherHandler() {
            @Override
            public void onSuccess(Cipher c) throws Exception {
                try {
                    byte[] plain = c.doFinal(encrypted);
                    JSObject ret = new JSObject();
                    ret.put("secret", new String(plain, StandardCharsets.UTF_8));
                    call.resolve(ret);
                } catch (Exception e) {
                    clearAll();
                    call.reject("密钥解密失败：" + e.getMessage(), "KEY_INVALID");
                }
            }
        });
    }

    /** 关闭指纹解锁：删除密钥与密文 */
    @PluginMethod
    public void disable(PluginCall call) {
        clearAll();
        JSObject ret = new JSObject();
        ret.put("ok", true);
        call.resolve(ret);
    }

    /** 弹出系统指纹验证框，验证通过后用同一个 Cipher 交给回调处理 */
    private void showPrompt(final PluginCall call, final Cipher cipher, final String title,
                            final String subtitle, final CipherHandler handler) {
        if (prompting) {
            call.reject("指纹验证正在进行中", "BUSY");
            return;
        }
        if (!(getActivity() instanceof FragmentActivity)) {
            call.reject("当前界面不支持指纹验证", "NO_ACTIVITY");
            return;
        }
        final FragmentActivity activity = (FragmentActivity) getActivity();
        prompting = true;

        BiometricPrompt.AuthenticationCallback callback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                prompting = false;
                String message = errString != null ? errString.toString() : "指纹验证失败";
                boolean canceled = errorCode == BiometricPrompt.ERROR_USER_CANCELED
                        || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        || errorCode == BiometricPrompt.ERROR_CANCELED;
                call.reject(message, canceled ? "CANCELLED" : "AUTH_ERROR");
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                prompting = false;
                Cipher used = result.getCryptoObject() != null
                        ? result.getCryptoObject().getCipher() : null;
                if (used == null) {
                    call.reject("指纹验证结果无效", "AUTH_ERROR");
                    return;
                }
                try {
                    handler.onSuccess(used);
                } catch (Exception e) {
                    call.reject("指纹验证失败：" + e.getMessage(), "AUTH_ERROR");
                }
            }

            @Override
            public void onAuthenticationFailed() {
                // 指纹不匹配，系统会自动让用户重试，这里不做处理
            }
        };

        final BiometricPrompt prompt = new BiometricPrompt(activity,
                ContextCompat.getMainExecutor(getContext()), callback);

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("使用密码")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
                } catch (Exception e) {
                    prompting = false;
                    call.reject("无法调起指纹验证：" + e.getMessage(), "AUTH_ERROR");
                }
            }
        });
    }

    private SecretKey loadKey() {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            java.security.Key key = ks.getKey(KEY_ALIAS, null);
            return key instanceof SecretKey ? (SecretKey) key : null;
        } catch (Exception e) {
            return null;
        }
    }

    private SecretKey createKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 每次使用都必须通过指纹验证
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 指纹重新录入后旧密钥立即失效
            builder.setInvalidatedByBiometricEnrollment(true);
        }
        generator.init(builder.build());
        return generator.generateKey();
    }

    private void deleteKey() {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) {
                ks.deleteEntry(KEY_ALIAS);
            }
        } catch (Exception ignored) {
            // 删除失败不阻断流程，后续 isEnabled 会按密钥是否存在判断
        }
    }

    private void clearAll() {
        deleteKey();
        getPrefs().edit().clear().apply();
    }
}
