#include <jni.h>
#include <cstring>
#include <cstdlib>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>
#include <cctype>
#include <sstream>
#include <cerrno>

/*
    Copyright (C) 2019  Christos Kyriakopoulos
    Copyright (C) 2025  Christian <kimocoder> Bremvaag

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>
 */

void logd(const char* str){
    __android_log_write(ANDROID_LOG_INFO, "CPP", str);
}

extern "C" jint Java_com_hijacker_Airodump_main(JNIEnv* env, jclass obj, jstring str, jint off){
    // Contract: parse a single line produced by airodump-like output and call back into Java.
    // Inputs: env, caller class, jstring containing the line, off flag used for column shifting
    // Outputs: returns 0 on completion, invokes static Java callbacks addAP/addST when appropriate
    // Error modes: null jstring, JNI failures -> no-op and early return

    if (str == nullptr) {
        logd("Java_com_hijacker_Airodump_main: input jstring is null");
        return 0;
    }

    const char* nativeString = env->GetStringUTFChars(str, nullptr);
    if (nativeString == nullptr) {
        // OutOfMemory or other JNI error
        logd("GetStringUTFChars returned null");
        return 0;
    }

    std::string buffer(nativeString);
    env->ReleaseStringUTFChars(str, nativeString);

    // Trim trailing newlines and carriage returns and spaces
    while (!buffer.empty() && (buffer.back() == '\n' || buffer.back() == '\r' || buffer.back() == ' ')) {
        buffer.pop_back();
    }

    // Collapse consecutive spaces starting from column 123 as original logic intended
    if (buffer.size() > 123) {
        for (size_t i = 123; i + 1 < buffer.size(); ++i) {
            if (buffer[i] == ' ' && buffer[i + 1] == ' ') {
                buffer.erase(i, 1);
                if (i > 0) --i; // re-check current position
            }
        }
    }

    // Find the Java class and method IDs (check for nulls)
    jclass jclass1 = env->FindClass("com/hijacker/Airodump");
    if (jclass1 == nullptr) {
        logd("FindClass com/hijacker/Airodump failed");
        return 0;
    }

    jmethodID method_ap = env->GetStaticMethodID(jclass1, "addAP", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIII)V");
    jmethodID method_st = env->GetStaticMethodID(jclass1, "addST", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;III)V");

    // If both method IDs are missing, nothing to do
    if (method_ap == nullptr && method_st == nullptr) {
        logd("Both addAP and addST method IDs not found");
        env->DeleteLocalRef(jclass1);
        return 0;
    }

    // Helper to create jstring and track local refs. If allocation fails (OOM) returns nullptr.
    auto make_jstring = [&](const std::string &s, std::vector<jstring> &created)->jstring{
        jstring js = env->NewStringUTF(s.c_str());
        if (js == nullptr) {
            // OOM or other error
            logd("NewStringUTF returned null (OOM?)");
            return nullptr;
        }
        created.push_back(js);
        return js;
    };

    // Robust integer parser using strtol that returns default_val on error/empty input
    auto to_int = [&](const std::string &s, int default_val = 0)->int{
        if (s.empty()) return default_val;
        char *endptr = nullptr;
        errno = 0;
        long val = strtol(s.c_str(), &endptr, 10);
        if (endptr == s.c_str() || errno == ERANGE) return default_val;
        return static_cast<int>(val);
    };

     // Ensure we have at least 4 characters before accessing buffer[3]
     if (buffer.size() > 3 && (buffer[3] == ':' || buffer[3] == 'o')) {
         size_t len = buffer.size();

        // Helper to safely extract substrings without throwing
        auto safe_sub = [&](size_t pos, size_t n, size_t extra_offset = 0)->std::string{
            size_t p = pos + extra_offset;
            if (p >= len) return {};
            return buffer.substr(p, std::min(n, len - p));
        };

        if (len > 22 && buffer[22] == ':') {
            // ST row parsing (station)
            std::string st_mac = safe_sub(20, 17);
            if (st_mac.size() > 17) st_mac.resize(17);

            std::string bssid;
            if (len > 1 && buffer[1] == '(') bssid = "na";
            else bssid = safe_sub(1, 17);

            std::string pwr_c = safe_sub(37, 5);
            int pwr = to_int(pwr_c, 0);

            std::string lost_c = safe_sub(52, 6);
            int lost = to_int(lost_c, 0);

            std::string frames_c = safe_sub(58, 9);
            int frames = to_int(frames_c, 0);

            std::string probes = safe_sub(69, 100);

            std::vector<jstring> created;
            jstring s1 = make_jstring(st_mac, created);
            jstring s2 = make_jstring(bssid, created);
            jstring s3 = make_jstring(probes, created);

            // If any allocation failed, clean up and skip the call
            bool alloc_failed = false;
            for (auto js : created) if (js == nullptr) { alloc_failed = true; break; }

            if (!alloc_failed && method_st != nullptr) {
                env->CallStaticVoidMethod(jclass1, method_st, s1, s2, s3, pwr, lost, frames);
                if (env->ExceptionCheck()) {
                    __android_log_write(ANDROID_LOG_ERROR, "CPP", "Exception thrown by addST");
                    env->ExceptionClear();
                }
            }

            for (auto js : created) if (js) env->DeleteLocalRef(js);
        } else {
            // AP row parsing (access point)
            size_t extra = (off != 0) ? 4 : 0;

            std::string bssid = safe_sub(1, 17, extra);
            std::string pwr_c = safe_sub(18, 5, extra);
            int pwr = to_int(pwr_c, 0);

            std::string beacons_c = safe_sub(23, 9, extra);
            int beacons = to_int(beacons_c, 0);

            std::string data_c = safe_sub(32, 9, extra);
            int data = to_int(data_c, 0);

            std::string ivs_c = safe_sub(41, 5, extra);
            int ivs = to_int(ivs_c, 0);

            std::string ch_c = safe_sub(48, 2, extra);
            int ch = to_int(ch_c, 0);

            std::string enc = safe_sub(57, 4, extra);
            // Trim trailing space in enc
            size_t pos_space = enc.find(' ');
            if (pos_space != std::string::npos) enc.resize(pos_space);

            std::string cipher = safe_sub(62, 4, extra);
            std::string auth = safe_sub(69, 4, extra);
            pos_space = auth.find(' ');
            if (pos_space != std::string::npos) auth.resize(pos_space);

            std::string essid;
            if (len > 74 + extra && buffer[74 + extra] != '<') essid = safe_sub(74, 49, extra);
            else essid = "<hidden>";

            std::vector<jstring> created;
            jstring s1 = make_jstring(essid, created);
            jstring s2 = make_jstring(bssid, created);
            jstring s3 = make_jstring(enc, created);
            jstring s4 = make_jstring(cipher, created);
            jstring s5 = make_jstring(auth, created);

            bool alloc_failed = false;
            for (auto js : created) if (js == nullptr) { alloc_failed = true; break; }

            if (!alloc_failed && method_ap != nullptr) {
                env->CallStaticVoidMethod(jclass1, method_ap, s1, s2, s3, s4, s5, pwr, beacons, data, ivs, ch);
                if (env->ExceptionCheck()) {
                    __android_log_write(ANDROID_LOG_ERROR, "CPP", "Exception thrown by addAP");
                    env->ExceptionClear();
                }
            }

            for (auto js : created) if (js) env->DeleteLocalRef(js);
        }
    }

    env->DeleteLocalRef(jclass1);
    return 0;
}