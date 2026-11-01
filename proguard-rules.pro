# قوانین NewPipeExtractor (برای وقتی که isMinifyEnabled = true می‌کنی)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-dontwarn javax.annotation.**
-keep class org.schabi.newpipe.extractor.** { *; }
