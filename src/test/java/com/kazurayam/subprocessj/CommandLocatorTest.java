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
     * The "allure" command could be installed in various path, it depends on your environment.
     */
    @Test
    void test_allure_on_Mac() {
        CommandLocator.CommandLocatingResult clr = CommandLocator.find("allure");
        assertEquals(0, clr.returncode());
        if (OSType.isMac()) {
            String userHome = System.getProperty("user.home");
            String allurePath = clr.command().substring(userHome.length() + 1);
            assertEquals(".volta/bin/allure", allurePath);
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
