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
