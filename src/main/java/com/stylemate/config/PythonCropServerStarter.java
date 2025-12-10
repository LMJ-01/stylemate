package com.stylemate.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Component
public class PythonCropServerStarter implements CommandLineRunner {

    @Override
    public void run(String... args) {
        try {
            String root = System.getProperty("user.dir");  
            String pythonPath = root + "/crop-server/venv/Scripts/python.exe";
            String scriptPath = root + "/crop-server/crop_server.py";


            System.out.println("🚀 Python crop_server.py 자동 실행 시도중...");

            ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 🔥 Python 로그 출력 스레드
            new Thread(() -> {
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[Python] " + line);
                    }
                } catch (Exception ignored) {}
            }).start();

            System.out.println("🔥 crop_server.py 자동 실행 명령 전송 완료.");

        } catch (Exception e) {
            System.err.println("⚠️ crop_server.py 자동 실행 실패:");
            e.printStackTrace();
        }
    }
}
