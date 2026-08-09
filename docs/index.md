# subprocessj

## What is this?

1.  You can execute arbitrary OS command from your Java application
    using `com.kazurayam.subprocessj.Subprocess`.
    This class utilizes `java.lang.ProcessBuilder`.
    A new OS process will be started and run background.

2.  You can find the process id of the process which is listening to a specific IP port of the localhost
    using `com.kazurayam.subprocessj.ProcessFinder`.
    It can find the pid of current JVM process as well.

3.  You can stop a server process by pid or by the IP port
    using `com.kazurayam.subprocessj.ProcessTerminator`.

4.  You can find the absolute file path of commands
    using `com.kazurayam.subprocesj.CommandFinder`.

5.  You can find the type of OS on which your java application is
    currently working using `com.kazurayam.subprocessj.OSType`.

## Motivation

There are many articles that tell how to use [`java.lang.ProcessBuilder`](https://docs.oracle.com/javase/8/docs/api/java/lang/ProcessBuilder.html). For example, I learned ["Baeldung article: Run Shell Command in Java"](https://www.baeldung.com/run-shell-command-in-java). The ProcessBuilder class is a state of the art with rich set of functionalities. But it is not easy for me to write a program that utilized ProcessBuilder. It involves multi-threading to consume the output streams (STDOUT and STDERR) from subprocess. I do not want to repeat writing it.

So I have made a simple wrapper of ProcessBuilder which exposes a limited subset of its functionalities.

I named this as `subprocessj` as I meant it to be a homage to the [Subprocess](https://docs.python.org/3/library/subprocess.html) module of Python.

I wanted to use `Subprocess` to start and stop an HTTP server inside
a JUnit test for my Java application.
I wanted to start Python-based HTTP server using the `docker run` command.
Then I need to be able to kill the background process.
I wanted this procedure fully automated.
In order to achieve this, I developed `ProcessTerminator` and some helpers.

## API

Javadoc is [here](https://kazurayam.github.io/subprocessj/api/index.html).

## Example of using Subprocess classes

### Starting a process

You just call `com.kazurayam.subprocessj.Subprocess.run(List<String> command)`. The `run()` will wait for the sub-process to finish, and returns a `com.kazurayam.subprocessj.CompletedProcess` object which contains the return code, STDOUT and STDERR emitted by the sub-process.

    package com.kazurayam.subprocessj;

    import org.junit.jupiter.api.Disabled;
    import org.junit.jupiter.api.Test;

    import java.io.File;
    import java.util.Arrays;
    import java.util.Map;
    import static org.junit.jupiter.api.Assertions.*;

    class SubprocessTest {

        @Test
        void test_list() throws Exception {
            Subprocess.CompletedProcess cp;
            if (OSType.isMac() || OSType.isUnix()) {
                cp = new Subprocess().cwd(new File("."))
                        .run(Arrays.asList("sh", "-c", "ls")
                        );
            } else {
                cp = new Subprocess().cwd(new File("."))
                        .run(Arrays.asList("cmd.exe", "/C", "dir")
                        );
            }
            assertEquals(0, cp.returncode());
            assertFalse(cp.stdout().isEmpty());
            cp.stdout().forEach(System.out::println);
            cp.stderr().forEach(System.err::println);
            assertTrue(cp.stdout().toString().contains("src"));
        }

        @Test
        void test_date() throws Exception {
            Subprocess.CompletedProcess cp;
            if (OSType.isMac() || OSType.isUnix()) {
                cp = new Subprocess().run(Arrays.asList("/bin/date"));
            } else {
                // I could not find out how to execute "date" command on Windows.
                cp = new Subprocess().run(Arrays.asList("java", "-version"));
            }
            assertEquals(0, cp.returncode());
            cp.stdout().forEach(System.out::println);
            cp.stderr().forEach(System.err::println);
            assertTrue(!cp.stdout().isEmpty() || !cp.stderr().isEmpty());
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
            assertFalse(cp.stderr().isEmpty());
            assertEquals(1,
                    (int) cp.stderr().stream()
                            .filter(line -> line.contains("fatal: not a git repository"))
                            .count()
            );
        }

        @Test
        void test_environment_readOnly() {
            Subprocess sp = new Subprocess();
            Map<String, String> env = sp.environment();
            assertNotNull(env);
            assertNotNull(env.get("PATH"));
            env.keySet().forEach(key -> {
                String value = env.get(key);
                System.out.printf("%s: %s%n", key, value);
            });
        }

        /**
         * See https://github.com/kazurayam/VBACallGraph/issues/45 for the background info
         */
        @Test
        void test_environment_setValue() {
            Subprocess sp = new Subprocess();
            Map<String, String> env = sp.environment();
            env.put("PLANTUML_LIMIT_SIZE", "8192");
            String actual = sp.environment("PLANTUML_LIMIT_SIZE");
            assertEquals("8192", actual);
        }
    }

This will emit the following output in the console:

    0
    total 4712
    drwxr-xr-x+  90 kazurayam       staff     2880  7 31 21:01 .
    drwxr-xr-x    6 root            admin      192  1  1  2020 ..
    ...

### Stopping a process

Using `java.lang.ProcessBuilder` class, you can create a `java.lang.Process` in which arbitrary application can run. Suppose you created a process in which HTTP Server runs. The process will stay running long until you explicitly stop it. But how can you stop that process?

Sometimes I encounter a new HTTP Server fails to start because the IP port is already in use. It tends to happen because I am not careful enough to stop the previous server process which is hanging on the IP port. In such situation, I have to do, on Mac, the following operations:

1.  execute a shell command `$ lsof -i:<port> -P`, to find out the id of the process which is still hanging on the IP port.

2.  execute a shell command `$ kill <processId>`, to stop the process.

3.  once the process is stopped, the IP port is released.

I wanted to automate this command line operation in my Java code. So I developed a Java class [`com.kazurayam.subprocessj.ProcessTerminator`](../src/main/java/com/kazurayam/subprocessj/ProcessTerminator.java).

See the following sample JUnit 5 test to see how to use the ProcessKiller.

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

@BeforeAll-annotated method starts the [HiThereServer](../src/main/java/com/kazurayam/subprocessj/HiThereServer.java) using `ProcessBuilder`. The process will start and stay running background. The HiThereServer is a simple HTTP server, listens to the IP port 8500.

@Test-annotated method makes an HTTP request to the HiThereServer.

@AfterAll-annotated method shuts down the HiThereServer using the `ProcessTerminator`. You specify the IP port 8500. The ProcessKiller will find the process ID of a process which is listening the port 8500, and kill the process.

### Finding the path of an OS command

    package com.kazurayam.subprocessj;

    import org.junit.jupiter.api.Disabled;
    import org.junit.jupiter.api.Test;
    import com.kazurayam.subprocessj.CommandLocator.CommandLocatingResult;

    import static org.junit.jupiter.api.Assertions.*;

    public class CommandLocatorTest {

        /**
         * On Mac, the `git` command will be found at `/usr/local/bin/git`
         */
        @Test
        void test_git_on_Mac() {
            CommandLocator.CommandLocatingResult clr = CommandLocator.find("git");
            assertEquals(0, clr.returncode());
            if (OSType.isMac()) {
                assertEquals("/usr/local/bin/git", clr.command());
            }
        }

        @Test
        void test_jq_on_Mac() {
            CommandLocator.CommandLocatingResult clr = CommandLocator.find("jq");
            assertEquals(0, clr.returncode());
            if (OSType.isMac()) {
                String userHome = System.getProperty("user.home");
                String jqPath = clr.command().substring(userHome.length() + 1);
                assertEquals(".pyenv/shims/jq", jqPath);
            }
        }

        /**
         * The "node" command could be installed in various path, it depends on your environment.
         */
        @Test
        void test_node_on_Mac() {
            CommandLocator.CommandLocatingResult clr = CommandLocator.find("node");
            assertEquals(0, clr.returncode());
            if (OSType.isMac()) {
                String userHome = System.getProperty("user.home");
                String nodePath = clr.command().substring(userHome.length() + 1);
                assertEquals(".anyenv/envs/nodenv/shims/node", nodePath);
            }
        }

        /**
         * The returned value depends on the runtime environment.
         *
         * On Mac, this will return
         * <PRE>/usr/local/bin/git</PRE>
         *
         * On Windows,
         * "where git"
         * will return 2 lines:
         * <PRE>
         * C:\Program Files\Git\mingw64\bin\git.exe
         * C:\Program Files\Git\cmd\git.exe
         * </PRE>
         * In this case, CommandLocator can not determine which path to choose.
         * Therefore CommandLocator will returncode -3 and command will be null.
         *
         * If you want to chose the line of "C:\Program Files\Git\cmd", you can specify
         * the second parameter to the find(String, Predicate&lt;Path&gt;)
         */
        @Test
        void test_find_git_with_startswith_predicate() {
            CommandLocatingResult cfr;
            if (OSType.isWindows()) {
                cfr = CommandLocator.find(
                        "git",
                        CommandLocator.startsWith("C:\\Program Files\\Git\\cmd")
                );
                printCFR("test_find_git_is_found_startswith_predicate", cfr);
                assertEquals(0, cfr.returncode());
                assertEquals("C:\\Program Files\\Git\\cmd\\git.exe", cfr.command());
            } else if (OSType.isMac() || OSType.isUnix()) {
                cfr = CommandLocator.find("git");
                printCFR("test_find_git_is_found_startswith_predicate", cfr);
                assertEquals(0, cfr.returncode());
                assertEquals("/usr/local/bin/git", cfr.command());
            } else {
                throw new IllegalStateException(OSType.getOSType() + " is not supported");
            }
        }

        @Test
        void test_find_git_with_endswith_predicate() {
            CommandLocatingResult cfr;
            if (OSType.isWindows()) {
                cfr = CommandLocator.find(
                        "git",
                        CommandLocator.endsWith("cmd\\git.exe")
                );
            } else if (OSType.isMac() || OSType.isUnix()) {
                cfr = CommandLocator.find("git");
            } else {
                throw new IllegalStateException(OSType.getOSType() + " is not supported");
            }
            printCFR("test_find_git_is_found_endswith_predicate", cfr);
            assertEquals(0, cfr.returncode());
        }

        /**
         * If "Docker for Windows" is not installed, CL will return rc=-1.
         * If it is installed, still CL will return rc=-2 because "where docker" command will return 2 lines as:
         * <PRE>
         * C:\\Users\\uraya&gt;where docker
         *
         * C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker
         * C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe
         * </PRE>
         */
        @Test
        void test_find_docker_on_Windows() {
            if (OSType.isWindows()) {
                CommandLocator.CommandLocatingResult cfr = CommandLocator.find("docker");
                printCFR("test_find_docker_on_Windows", cfr);
                assertNotEquals(0, cfr.returncode());
            }
        }

        @Test
        void test_find_dockerexe_on_Windows() {
            if (OSType.isWindows()) {
                CommandLocator.CommandLocatingResult cfr = CommandLocator.find("docker.exe");
                printCFR("test_find_dockerexe_on_Windows", cfr);
                assertEquals(0, cfr.returncode());
            }
        }


        /**
         * On Windows, the "date" command is implemented as a sub-command of cmd.exe.
         * So CommandFinder.find("date") will return non-zero, no Path found.
         *
         * NO.
         * If you have Git Bash installed, you will have "C:\\Program Files\\Git\\usr\\bin\\date.exe
         */
        @Disabled
        @Test
        void test_find_date_on_Windows() {
            if (OSType.isWindows()) {
                CommandLocator.CommandLocatingResult cfr = CommandLocator.find("date");
                printCFR("test_find_date_on_Windows", cfr);
                assertNotEquals(0, cfr.returncode());
            }
        }

        /**
         * assert that the "tiger" command is not there
         */
        @Test
        void test_find_tiger_not_exists() {
            CommandLocator.CommandLocatingResult cfr = CommandLocator.find("tiger");
            printCFR("test_find_tiger_not_exists", cfr);
            assertNotEquals(0, cfr.returncode());
        }

        private void printCFR(String label, CommandLocatingResult cfr) {
            System.out.println("-------- " + label + " --------");
            System.out.println(cfr.toString());
        }
    }

### Finding process id

#### Finding the pid of the current JVM

    package com.kazurayam.subprocessj;

    import org.junit.jupiter.api.Test;

    import static org.junit.jupiter.api.Assertions.assertTrue;

    public class ProcessFinderTest_CurrentJvmPid {


        @Test
        void test_getCurrentJvmPid() {
            long jvmProcessId = ProcessFinder.findCurrentJvmPid();
            assertTrue(jvmProcessId > 0);
        }

    }

#### Finding the pid of a process which is listening to a specific IP port

    package com.kazurayam.subprocessj;

    import com.kazurayam.subprocessj.ProcessFinder.ProcessFindingResult;
    import org.junit.jupiter.api.AfterAll;
    import org.junit.jupiter.api.BeforeAll;
    import org.junit.jupiter.api.Test;

    import java.io.IOException;
    import java.util.regex.Matcher;
    import java.util.regex.Pattern;

    import static org.junit.jupiter.api.Assertions.*;

    public class ProcessFinderTest_base {

        private static HiThereServer server;
        private static final int PORT = 8090;

        @BeforeAll
        public static void beforeAll() throws IOException {
            server = new HiThereServer();
            server.setPort(PORT);
            server.startup();
        }

        @AfterAll
        public static void afterAll() {
            server.shutdown();
        }

        /**
         * For example, this will show `1916` as the pid.
         */
        @Test
        void test_findProcessIdByListeningPort_found() {
            ProcessFindingResult pfr = ProcessFinder.findPidByListeningPort(PORT);
            System.out.println(pfr.processId());
            printPFR("test_findProcessIdByListeningPort_found", pfr);
            assertEquals(0, pfr.returncode(), pfr.message());
            assertTrue(pfr.processId() > 0);
        }

        private void printPFR(String label, ProcessFindingResult pfr) {
            System.out.println("-------- " + label + " --------");
            System.out.println(pfr.toString());
        }
    }

### Identifying OS Type

    package com.kazurayam.subprocessj;

    import org.junit.jupiter.api.Test;

    import static org.junit.jupiter.api.Assertions.assertTrue;

    public class OSTypeTest {

        /**
         * Which OS am I working on now?
         */
        @Test
        void test_getOSType() {
            assertTrue(OSType.isMac() || OSType.isUnix() || OSType.isWindows());
        }
    }

### retrieving Password from Mac KeyChain

I often write Selenium test that access to some Web apps with authentication.
I need to put username/password pair. Due to obvious security reason,
I do not like to write password strings in the source code at all.
I would rather like to use the [KeyChain](https://support.apple.com/guide/keychain-access/kyca1083/mac) of macOS
to store passwords, and I want my Selenium test to retrieve the password from KeyChain.

KeyChain provides a commandline interface named `security` which is built-in the macOS.
So I want my Selenium test to execute the `security` command and
retrieve the password value I need.

The following sample shows how to.

    package example;

    import com.kazurayam.subprocessj.Subprocess;
    import org.junit.jupiter.api.Test;

    import java.io.File;
    import java.io.IOException;
    import java.util.Arrays;

    import static org.junit.jupiter.api.Assertions.assertEquals;

    public class KeyChainExample {

        @Test
        public void test_macos_security_findinternetpassword()
                throws IOException, InterruptedException
        {
            Subprocess.CompletedProcess cp;
            cp = new Subprocess().cwd(new File("."))
                    .run(Arrays.asList("security", "find-internet-password",
                            "-s", "katalon-demo-cura.herokuapp.com",
                            "-a", "John Doe",
                            "-w"));
            assertEquals("ThisIsNotAPassword", cp.stdout().get(0));
            System.out.println("password is '" + cp.stdout().get(0) + "'") ;
        }
    }

## Running a javascript on Node.js from Java

I have a javascript `hello.js`, which is (as you can correctly expect):

    console.log("Hello, World!");

The following JUnit5 test runs Node.js in command line while specifying the `hello.js` to execute:

    package com.kazurayam.subprocessj;

    import org.junit.jupiter.api.Test;

    import java.io.IOException;
    import java.nio.file.Path;
    import java.nio.file.Paths;
    import java.util.Arrays;
    import static org.junit.jupiter.api.Assertions.assertEquals;
    import static org.junit.jupiter.api.Assertions.fail;

    /**
     * This test runs a javascript "hello.js" on Node.js in a subprocess.
     * It waits for the subprocess to finish.
     * It reads and consumes the stream of stderr of the subprocess to print in the console.
     * It consumes the stdout as well.
     */
    public class NodejsTest {

        private Path scriptPath =
                Paths.get(".").resolve("src/test/js/hello.js");
        @Test
        public void test_run_javascript_using_node_command() {
            CommandLocator.CommandLocatingResult clr =
                    CommandLocator.find("node");
            //System.out.println(clr.toString());

            if (clr.returncode() == 0) {
                Subprocess.CompletedProcess cp;
                try {
                    // You are supposed to specify the "node" command in full path
                    // such as "/Users/kazurayam/.nodebrew/current/bin/node"
                    cp = new Subprocess()
                            .run(Arrays.asList(clr.command(), scriptPath.toString()));
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                cp.stderr().forEach(System.err::println);
                cp.stdout().forEach(System.out::println);
                assertEquals(0, cp.returncode());
            } else {
                Subprocess sp = new Subprocess();
                String pathValue = sp.environment("PATH");
                fail(String.format(
                        "the node command was not found in the PATH. " +
                                "Environment Variable PATH = %s",
                        pathValue));
            }
        }
    }

## A sample code to run a utility "pngquant" from Java

The following JUnit5 test shows a sample how to invoke [pngquant](https://pngquant.org/) to compress a PNG image file.

    package com.kazurayam.subprocessj;

    import org.junit.jupiter.api.BeforeEach;
    import org.junit.jupiter.api.Test;

    import java.io.IOException;
    import java.nio.file.Files;
    import java.nio.file.Path;
    import java.nio.file.Paths;
    import java.nio.file.StandardCopyOption;
    import java.util.Arrays;

    import static org.junit.jupiter.api.Assertions.assertEquals;
    import static org.junit.jupiter.api.Assertions.assertTrue;

    /**
     * [pngquant](https://pngquant.org/) is a command-line utility for lossy compression of PNG images.
     * It is available on Mac, Windows and Linux.
     */
    public class PngquantTest {

        private Path fixturesDir = Paths.get(".").resolve("src/test/fixtures");
        private Path outputDir = Paths.get(".").resolve("build/tmp/testOutput/PngquantTest");

        @BeforeEach
        public void beforeEach() throws IOException {
            Files.createDirectories(outputDir);
        }

        /**
         * Here I assume that "pngquant" is already installed in the runtime environment and
         * the command "$ pngquant --version" responds
         * "2.18.0 (January 2023)"
         *
         * This code shows how to execute the pngquant from Java to compress
         * a sample PNG image using pngquant.
         */
        @Test
        public void test_compress_png_using_pngquant() throws IOException {
            // 1. make sure the source PNG image is present
            Path sourcePng = fixturesDir.resolve("apple.png");
            assertTrue(Files.exists(sourcePng));

            // 2. copy the source to the target file
            Path targetPng = outputDir.resolve("apple.png");
            Files.copy(sourcePng, targetPng, StandardCopyOption.REPLACE_EXISTING);

            // 3. record the size information of the target file
            long sizeBeforeCompression = targetPng.toFile().length();

            // 4. check if "pngquant" is installed and available
            CommandLocator.CommandLocatingResult clr = CommandLocator.find("pngquant");
            System.out.println(clr.toString());

            if (clr.returncode() == 0) {
                // 5. now compress it using pngquant
                Subprocess.CompletedProcess cp;
                try {
                    cp = new Subprocess().run(Arrays.asList(
                            "pngquant", "--ext", ".png", "--force",
                            "--speed", "1", targetPng.toString()
                    ));
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // 6. assert that pngquant ran successfully
                System.out.println("[test_compress_png_using_pngquant]");
                System.out.println(cp.toString());
                assertEquals(0, cp.returncode());
            }

            // 7. record the size information of the compressed file
            long sizeAfterCompression = targetPng.toFile().length();

            // 8. report the result
            System.out.println(String.format("file: %s", targetPng.toString()));
            System.out.println(String.format("size before compression: %d", sizeBeforeCompression));
            System.out.println(String.format("size after compression: %d", sizeAfterCompression));
            long delta = ((sizeBeforeCompression - sizeAfterCompression) * 100) / sizeBeforeCompression;
            System.out.println(String.format("size delta: Δ%d%%", delta));
        }

    }

The output from this test is as follows:

    [test_compress_png_using_pngquant]
    <completed-process rc="0">
    <command>pngquant --ext .png --force --speed 1 ./build/tmp/testOutput/PngquantTest/apple.png</command>
    <stdout>
    </stdout>
    <stderr>
    </stdout>
    </completed-process>

    file: ./build/tmp/testOutput/PngquantTest/apple.png
    size before compression: 3655
    size after compression: 2818
    size delta: Δ22%

## How to get Environment Variable values

    package com.kazurayam.subprocessj;

    import org.junit.jupiter.api.Test;

    import java.util.Arrays;
    import java.util.List;
    import java.util.Map;

    import static org.junit.jupiter.api.Assertions.assertNotNull;

    /**
     * In the command line, you can do this
     * ```
     * $ echo $PATH
     * /usr/local/bin:usr/local/sbin:/Users/kazurayam/.nodebrew/current/bin:/Users/kazurayam.pyenv/shims:...
     * ```
     *
     * How to the same from Java?
     * You can do it using the Subprocess#environment()
     */
    public class EchoPathTest {

        @Test
        public void test_get_environment_values_as_Map() {
            Subprocess sp = new Subprocess();
            Map<String, String> env = sp.environment();
            System.out.println(String.format("PATH: %s", env.get("PATH")));
            // split the PATH value by ":", print the elements by line
            List<String> values = Arrays.asList(env.get("PATH").split(":"));
            values.stream().sorted().forEach(System.out::println);
        }

        @Test
        public void test_get_environment_variable_value() {
            Subprocess sp = new Subprocess();
            String pathValue = sp.environment("PATH");
            System.out.println(String.format("PATH: %s", pathValue));
            assertNotNull(pathValue);
        }
    }

When I executed, I got the following output

    PATH: /bin:/sbin:/usr/bin:/usr/local/bin:/usr/local/bin:/usr/local/go/bin:/usr/local/sbin:/usr/sbin:/Users/kazuakiurayama/.nodebrew/current/bin: ... and a lot more

## How to get the Git current branch name

    package example;

    import com.kazurayam.subprocessj.CommandLocator;
    import com.kazurayam.subprocessj.OSType;
    import com.kazurayam.subprocessj.Subprocess;
    import org.junit.jupiter.api.Test;

    import java.io.IOException;
    import java.util.Arrays;

    import static org.junit.jupiter.api.Assertions.assertEquals;

    public class GitCommandExample {

        @Test
        public void test_git_command_path() throws IOException, InterruptedException {
            CommandLocator.CommandLocatingResult clr = CommandLocator.find("git");
            assertEquals(0, clr.returncode());
            System.out.println(clr.command());
            if (OSType.isMac()) {
                assertEquals("/usr/local/bin/git", clr.command());
            }
        }

        @Test
        public void test_git_show_current_branch() throws IOException, InterruptedException {
            Subprocess.CompletedProcess cp =
                    new Subprocess()
                            .run(Arrays.asList("git", "branch", "--show-current"));
            assertEquals(0, cp.returncode());
            String branchName = cp.stdout().get(0).trim();
            System.out.printf("current GIT branch: %s%n", branchName);
        }
    }

When I ran this test, I got the following output in the console:

    > Task :compileJava UP-TO-DATE
    > Task :processResources NO-SOURCE
    > Task :classes UP-TO-DATE
    > Task :compileTestJava UP-TO-DATE
    > Task :processTestResources NO-SOURCE
    > Task :testClasses UP-TO-DATE
    > Task :test
    current GIT branch: issue38
    /usr/local/bin/git
    > Task :jacocoTestReport
    BUILD SUCCESSFUL in 3s
    4 actionable tasks: 2 executed, 2 up-to-date
    9:29:50: Execution finished ':test --tests "example.GitCommandExample"'.

## links

The artifact is available at the Maven Central repository:

-   <https://mvnrepository.com/artifact/com.kazurayam/subprocessj>

The project’s repository is here

-   [the repository](https://github.com/kazurayam/subprocessj/)
