package com.kazurayam.subprocessj;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import com.kazurayam.subprocessj.ProcessTerminator.ProcessTerminationResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Start up a process in which HiThereServer runs on background,
 * will use java.lang.ProcessBuilder to create the subprocess.
 * Make an HTTP request and check the response.
 * Shutdown the process of HiThereServer.
 */
public class HiThereServerAsProcessTest {

    @BeforeAll
    static public void beforeAll() throws IOException, InterruptedException {
        List<String> args = Arrays.asList(
                "java",
                "-cp", "build/classes/java/main",
                "com.kazurayam.subprocessj.HiThereServer"
        );
        ProcessBuilder pb = new ProcessBuilder(args);
        Process process = pb.start();
        Thread.sleep(2000);  // wait for the process to boot successfully
    }

    @Test
    public void test_request_response() throws IOException {
        URL url = new URL("http://127.0.0.1:8500/");
        URLConnection conn = url.openConnection();
        String content = TestUtils.readInputStream(conn.getInputStream());
        assertTrue(content.contains("Hi there!"));
    }

    @AfterAll
    static public void afterAll() throws IOException, InterruptedException {
        ProcessTerminationResult tr = ProcessTerminator.killProcessOnPort(8500);
        assertEquals(0, tr.returncode());
    }
}
