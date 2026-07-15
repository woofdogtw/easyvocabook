# sshj (SFTP) and commons-net (FTP) pull in optional/reflective transitive
# dependencies that are not on the Android classpath. Suppress the resulting
# missing-class warnings and keep the network libraries from being stripped.
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn net.i2p.crypto.**
-dontwarn org.apache.commons.net.**
-dontwarn net.schmizz.sshj.**
-dontwarn com.hierynomus.**
-dontwarn javax.annotation.**

-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.** { *; }
-keep class org.apache.commons.net.** { *; }
