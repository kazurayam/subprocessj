package com.kazurayam.subprocessj;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class SubprocessTest {

    @Test
    void test_ls() throws Exception {
        Subprocess.CompletedProcess cp =
                new Subprocess()
                        .cwd(new File("."))
                        .run(Arrays.asList("sh", "-c", "ls")
                        );
        assertEquals(0, cp.returncode());
        //println "stdout: ${cp.getStdout()}";
        //println "stderr: ${cp.getStderr()}";
        assertTrue(cp.stdout().size() > 0);
        assertTrue(cp.stdout().contains("src"));
    }

    @Test
    void test_date() throws Exception {
        Subprocess.CompletedProcess cp =
                new Subprocess().run(Arrays.asList("/bin/date"));
        assertEquals(0, cp.returncode());
        //println "stdout: ${cp.getStdout()}";
        //println "stderr: ${cp.getStderr()}";
        assertTrue(cp.stdout().size() > 0);
        /*
        assertTrue(cp.getStdout().stream()
                .filter { line ->
                    line.contains("2021")
                }.collect(Collectors.toList()).size() > 0)
         */
    }

    /**
     * this test method will throw IOException when executed on a CI/CD environment where
     * "git" is not installed. So I disabled this.
     */
    @Disabled
    @Test
    void test_git() throws Exception {
        Subprocess.CompletedProcess cp =
                    new Subprocess()
                            .cwd(new File(System.getProperty("user.home")))
                            .run(Arrays.asList("/usr/local/bin/git", "status"));
        assertEquals(128, cp.returncode());
        //System.out.println(String.format("stdout: %s", cp.getStdout()));
        //System.out.println(String.format("stderr: %s", cp.getStderr()));
        assertTrue(cp.stderr().size() > 0);
        assertEquals(1,
                cp.stderr().stream()
                        .filter(line -> line.contains("fatal: not a git repository"))
                        .collect(Collectors.toList())
                        .size()
        );
    }

    @Test
    void test_demo() throws Exception {
        Subprocess subprocess = new Subprocess();
        subprocess.cwd(new File(System.getProperty("user.home")));
        Subprocess.CompletedProcess cp = subprocess.run(Arrays.asList("ls", "-la", "."));
        System.out.println(cp.returncode());
        cp.stdout().forEach(System.out::println);
        cp.stderr().forEach(System.out::println);
        assertEquals(0, cp.returncode());
        assertTrue(cp.stdout().size() > 0);
    }
}
