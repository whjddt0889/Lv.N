import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** apksig(bundletool 포함)로 v1+v2+v3 서명. 사용: SignApk in.apk out.apk keystore.p12 password alias */
public class SignApk {
    public static void main(String[] a) throws Exception {
        char[] pw = a[3].toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(a[2])) { ks.load(in, pw); }
        PrivateKey key = (PrivateKey) ks.getKey(a[4], pw);
        X509Certificate cert = (X509Certificate) ks.getCertificate(a[4]);
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder("LVN", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(sc))
                .setInputApk(new File(a[0])).setOutputApk(new File(a[1]))
                .setMinSdkVersion(28)
                .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true)
                .build().sign();
        ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[1])).build().verify();
        System.out.println("verified=" + r.isVerified() + " v1=" + r.isVerifiedUsingV1Scheme()
                + " v2=" + r.isVerifiedUsingV2Scheme() + " v3=" + r.isVerifiedUsingV3Scheme());
        if (!r.isVerified()) { System.out.println(r.getErrors()); System.exit(1); }
    }
}
