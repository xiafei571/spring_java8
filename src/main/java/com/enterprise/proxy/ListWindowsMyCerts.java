package com.enterprise.proxy;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class ListWindowsMyCerts {
    public static void main(String[] args) throws Exception {
        KeyStore winMy = KeyStore.getInstance("Windows-MY");
        winMy.load(null, null);

        Enumeration<String> aliases = winMy.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            boolean hasPrivateKey = winMy.isKeyEntry(alias);
            X509Certificate cert = (X509Certificate) winMy.getCertificate(alias);
            if (cert == null) continue;

            String subject = cert.getSubjectX500Principal().getName();
            String issuer  = cert.getIssuerX500Principal().getName();
            String notBefore = cert.getNotBefore().toString();
            String notAfter  = cert.getNotAfter().toString();
            String sha1 = sha1Thumbprint(cert);

            List<String> eku = new ArrayList<>();
            try {
                List<String> xkus = cert.getExtendedKeyUsage();
                if (xkus != null) eku = xkus;
            } catch (Exception ignore) {}

            System.out.println("--------------------------------------------------");
            System.out.println("Alias:          " + alias);
            System.out.println("Has PrivateKey: " + hasPrivateKey);
            System.out.println("Subject:        " + subject);
            System.out.println("Issuer:         " + issuer);
            System.out.println("Valid:          " + notBefore + "  ~  " + notAfter);
            System.out.println("EKU:            " + (eku.isEmpty() ? "(none)" : eku));
            System.out.println("SHA1 Thumbprint:" + sha1);
        }
    }

    private static String sha1Thumbprint(X509Certificate cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] der = cert.getEncoded();
        byte[] dig = md.digest(der);
        // 常见显示是十六进制带冒号；你也可以保留 Hex 以便和 MMC/证书管理器对照
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dig.length; i++) {
            sb.append(String.format("%02X", dig[i]));
            if (i < dig.length - 1) sb.append(':');
        }
        return sb.toString();
    }
}
